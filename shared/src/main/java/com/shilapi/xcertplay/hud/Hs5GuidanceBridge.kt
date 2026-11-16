package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.orchestration.ConnectionDiagnosticTrace
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** CarPlay turn-by-turn for Hongqi head units (QiSi binder or Gaode-style broadcasts). */
internal object Hs5GuidanceBridge {
    private const val TAG = "Xcert-Hs5-Guidance"
    private const val AMAP_ACTION = "AUTONAVI_STANDARD_BROADCAST_SEND"
    private const val KEY_GUIDANCE = 10001
    private const val KEY_STATE = 10019
    private const val STATE_ENDED = 9
    private const val KEEPALIVE_TICKS = 5
    private const val FLAG_RECEIVER_INCLUDE_BACKGROUND = 0x01000000
    private const val BIND_RETRY_MS = 3_000L

    private val lock = Any()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val route = Hs5RouteState()
    private var context: Context? = null
    private var adapter: Hs5NavPackages? = null
    private var amapTargetPackage: String? = null
    private var available = false
    private var useQisi = false
    private var senderStarted = false
    private var lastSent: Hs5GuidanceFrame? = null
    private var externalFrame: Hs5GuidanceFrame? = null
    private var ticksSinceSend = 0
    private var guidanceLogged = false

    private var binding = false
    private var remoteBinder: IBinder? = null
    private var hudBinder: IBinder? = null
    private var instrumentBinder: IBinder? = null
    private var systemBinder: IBinder? = null
    private var modulesReadyLogged = false
    private var hudActive = false
    private var instrumentNavActive = false
    private var instrumentMapActive = false
    private var instrumentMapSession: QisiClusterMapSession? = null
    private var clusterMapCloseRequest: (() -> Unit)? = null
    private var clusterMapShown = false
    private var lastInstrument: QisiInstrumentFrame? = null
    private val unsupportedGuidanceCalls = mutableSetOf<Pair<IBinder, Int>>()
    private val reportedGuidanceFailures = mutableSetOf<Pair<IBinder, Int>>()

    private val bindRetry = Runnable { bindAdapterLocked() }

    fun initialize(appContext: Context) = synchronized(lock) {
        if (context != null) return@synchronized
        context = appContext.applicationContext
        val packageName = Hs5NavPackages.resolvedPackage(appContext)
        adapter = packageName?.let { pkg ->
            Hs5NavPackages.entries.firstOrNull { it.packageName == pkg } ?: Hs5NavPackages.AMAP_AUTO
        }
        val outputPackages = Hs5NavPackages.navigationOutputPackages(appContext)
        amapTargetPackage = packageName ?: outputPackages.firstOrNull()
        useQisi = Hs5NavPackages.isQisiStack(appContext)
        available = useQisi || outputPackages.isNotEmpty()
        DiagnosticLogging.logcat { Log.i(
            TAG,
            "hs5 guidance available=$available qisi=$useQisi target=$amapTargetPackage outputs=$outputPackages",
        ) }
        if (available && useQisi) mainHandler.post { bindAdapterLocked() }
        if (available && !senderStarted) {
            senderStarted = true
            Executors.newSingleThreadScheduledExecutor { runnable ->
                Thread(runnable, "xcert-hs5-guidance").apply { isDaemon = true }
            }.scheduleAtFixedRate(::tick, 1, 1, TimeUnit.SECONDS)
        }
    }

    /** CarPlay cluster map visibility; toggles QiSi dashboard projection alongside stream 111. */
    fun setClusterMapShown(shown: Boolean) = synchronized(lock) {
        if (clusterMapShown == shown) return@synchronized
        clusterMapShown = shown
        if (!useQisi) return@synchronized
        applyInstrumentMapLocked()
    }

    fun setClusterMapCloseRequest(request: (() -> Unit)?) = synchronized(lock) {
        if (clusterMapCloseRequest === request) return@synchronized
        instrumentMapSession?.close()
        instrumentMapSession = null
        instrumentMapActive = false
        clusterMapCloseRequest = request
        if (useQisi) applyInstrumentMapLocked()
    }

    fun clearClusterMapCloseRequest(expected: () -> Unit) = synchronized(lock) {
        if (clusterMapCloseRequest === expected) setClusterMapCloseRequest(null)
    }

    fun clusterMapDiagnosticReport(): String = synchronized(lock) {
        "Qisi cluster navigation: qisi=$useQisi bound=${systemBinder != null} " +
            "instrument=${instrumentBinder != null} hud=${hudBinder != null} " +
            "shown=$clusterMapShown active=$instrumentMapActive exitTarget=${clusterMapCloseRequest != null}"
    }

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        externalFrame = null
        if (!available) return@synchronized
        when (route.accept(frame.messageId, frame.payload)) {
            Hs5RouteChange.GUIDANCE -> queueApplyLocked()
            Hs5RouteChange.CLEAR -> if (lastSent != null || hudActive || instrumentNavActive) sendEndLocked()
            Hs5RouteChange.NONE -> Unit
        }
    }

    fun onExternalFrame(frame: Hs5GuidanceFrame) = synchronized(lock) {
        if (!available) return@synchronized
        if (context?.let(Hs5OutputSettings::enabled) != true) return@synchronized
        externalFrame = frame
        val sent = if (useQisi && hudBinder != null) sendQisiLocked(frame) else sendBroadcastLocked(frame)
        if (sent) {
            lastSent = frame
            ticksSinceSend = 0
            if (!guidanceLogged) {
                guidanceLogged = true
                DiagnosticLogging.logcat { Log.i(TAG, "external guidance frame sent $frame qisi=$useQisi") }
            }
        }
    }

    fun clear() = synchronized(lock) {
        externalFrame = null
        route.clear()
        if (available) sendEndLocked()
    }

    private fun tick() = synchronized(lock) {
        if (useQisi && remoteBinder == null && !binding) mainHandler.post { bindAdapterLocked() }
        if (useQisi && clusterMapShown && !instrumentMapActive) {
            mainHandler.post { synchronized(lock) { applyInstrumentMapLocked() } }
        }
        if (externalFrame != null) {
            if (++ticksSinceSend >= KEEPALIVE_TICKS) {
                val frame = externalFrame!!
                val sent = if (useQisi && hudBinder != null) sendQisiLocked(frame) else sendBroadcastLocked(frame)
                if (sent) { lastSent = frame; ticksSinceSend = 0 }
            }
            return@synchronized
        }
        if (lastSent != null && route.currentApple() == null) sendEndLocked()
        else if (++ticksSinceSend >= KEEPALIVE_TICKS) queueApplyLocked(force = true)
    }

    private fun queueApplyLocked(force: Boolean = false) {
        mainHandler.post { applyGuidanceLocked(force) }
    }

    private fun applyGuidanceLocked(force: Boolean = false) = synchronized(lock) {
        if (context?.let(Hs5OutputSettings::enabled) != true) {
            if (lastSent != null || hudActive || instrumentNavActive) sendEndLocked()
            return@synchronized
        }
        val frame = route.currentApple()?.let(Hs5GuidanceFrame::from)
        if (frame == null) {
            if (lastSent != null || hudActive || instrumentNavActive) sendEndLocked()
            return@synchronized
        }
        if (!force && frame == lastSent) return@synchronized
        val sent = if (useQisi && hudBinder != null) sendQisiLocked(frame) else sendBroadcastLocked(frame)
        if (sent) {
            lastSent = frame
            ticksSinceSend = 0
            if (!guidanceLogged) {
                guidanceLogged = true
                DiagnosticLogging.logcat { Log.i(TAG, "guidance frame sent $frame qisi=$useQisi") }
            }
        }
    }

    private fun sendEndLocked() {
        if (useQisi && hudBinder != null) clearQisiLocked()
        else clearBroadcastLocked()
        lastSent = null
        guidanceLogged = false
        lastInstrument = null
    }

    private fun sendQisiLocked(frame: Hs5GuidanceFrame): Boolean {
        val hud = hudBinder ?: return false
        val instrument = instrumentBinder
        return try {
            sendHudLocked(hud, frame)
            if (instrument != null && instrumentTbtEnabled()) {
                sendInstrumentGuidanceLocked(instrument, frame)
            }
            true
        } catch (error: DeadObjectException) {
            DiagnosticLogging.logcat { Log.w(TAG, "adapter service died", error) }
            resetModulesLocked()
            scheduleBindRetryLocked()
            false
        }
    }

    private fun sendHudLocked(hud: IBinder, frame: Hs5GuidanceFrame) {
        // Mark partial output active too, so ending navigation clears accepted fields.
        hudActive = true
        val icon = frame.icon.coerceIn(0, 31)
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ICON, intArrayOf(icon))
        val distance = QisiVehicleBinder.hudDistanceUnits(frame.distanceMeters)
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_DISTANCE,
            distance,
        )
        val roundabout = frame.roundaboutExit.coerceIn(0, 16)
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ROUNDABOUT,
            intArrayOf(roundabout),
        )
        sendHudMetadataLocked(hud, frame)
    }

    /** Send route fields whose HS5 payload semantics are known. */
    private fun sendHudMetadataLocked(hud: IBinder, frame: Hs5GuidanceFrame) {
        val nextCrossDistance = frame.distanceMeters.coerceAtLeast(0)
        val remainingMeters = frame.routeRemainingMeters.coerceAtLeast(0)
        val remainingSeconds = frame.routeRemainingSeconds.coerceAtLeast(0)
        val laneValues = hs5LaneValues(frame)
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NAV_STATUS, intArrayOf(1),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_CROSS_DISTANCE,
            intArrayOf(nextCrossDistance),
        )
        // The iAP2 route payload has one distance, but no distance range. Keep the range
        // explicitly empty instead of treating the distance as a made-up range.
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_CROSS_RANGE,
            intArrayOf(0),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_INTERSECTION,
            intArrayOf(frame.junctionType.coerceAtLeast(0)),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_LEFT_LANE_LINE,
            intArrayOf(laneValues.leftLineType),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_RIGHT_LANE_LINE,
            intArrayOf(laneValues.rightLineType),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_LANE_TYPE,
            intArrayOf(laneValues.laneType),
        )
        // No road classification, camera, or complete route total is present in CarPlay's
        // 0x5201-0x5204 messages. QiSi uses zero as the documented empty value.
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ROAD_TYPE, intArrayOf(0),
        )
        sendHudStringLocked(hud, QisiVehicleBinder.HUD_SET_DESTINATION_NAME,
            frame.destination,
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_DESTINATION_DISTANCE,
            intArrayOf(remainingMeters),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_REMAINING_TIME,
            intArrayOf(remainingSeconds),
        )
        sendHudStringLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_ROAD, frame.road,
        )
        sendHudStringIntLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_ROAD_INFO,
            frame.exitInfo.ifEmpty { frame.laneGuidanceDescription }, frame.junctionType.coerceAtLeast(0),
        )
        val exit = parseExitNumber(frame.exitInfo)
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_TARGET_EXIT, intArrayOf(exit),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_CURRENT_EXIT, intArrayOf(0),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_TOTAL_DISTANCE, intArrayOf(0),
        )
        sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_CAMERA_DISTANCE, intArrayOf(0),
        )
    }

    /**
     * CarPlay lane status is a colour/recommendation state, while QiSi exposes three scalar
     * lane fields. Preserve the only shared meaning: recommended lanes are 2, available lanes
     * are 1, and an unknown/absent lane is 0. The values are deliberately bounded because the
     * phone protocol is untrusted input.
     */
    private fun hs5LaneValues(frame: Hs5GuidanceFrame): Hs5LaneValues {
        val lanes = frame.lanes.sortedBy { it.position }
        if (lanes.isEmpty()) return Hs5LaneValues()
        val selected = lanes.indexOfFirst { it.status == 2 || it.highlightedAngle != null }
            .takeIf { it >= 0 } ?: -1
        val laneType = selected.takeIf { it >= 0 }?.let { laneLineType(lanes[it].status) } ?: 0
        val left = selected.takeIf { it > 0 }?.let { laneLineType(lanes[it - 1].status) } ?: 0
        val right = selected.takeIf { it >= 0 && it + 1 < lanes.size }
            ?.let { laneLineType(lanes[it + 1].status) } ?: 0
        return Hs5LaneValues(laneType, left, right)
    }

    private fun laneLineType(status: Int): Int = when (status) {
        2 -> 2
        1 -> 1
        else -> 0
    }

    private data class Hs5LaneValues(
        val laneType: Int = 0,
        val leftLineType: Int = 0,
        val rightLineType: Int = 0,
    )

    private fun parseExitNumber(text: String): Int {
        var number = 0
        var found = false
        for (char in text) {
            if (char in '0'..'9') {
                found = true
                number = (number * 10 + (char - '0')).coerceAtMost(99)
            } else if (found) break
        }
        return if (found) number else 0
    }

    private fun sendInstrumentGuidanceLocked(instrument: IBinder, frame: Hs5GuidanceFrame) {
        if (!instrumentNavActive) {
            instrumentNavActive = guidanceCallLocked(instrument, QisiVehicleBinder.INSTRUMENT_SET_ACTIVE) {
                QisiVehicleBinder.transactInts(instrument, QisiVehicleBinder.TOKEN_INSTRUMENT,
                    QisiVehicleBinder.INSTRUMENT_SET_ACTIVE, intArrayOf(1))
            }
        }
        val units = QisiVehicleBinder.hudDistanceUnits(frame.distanceMeters)
        val guidance = QisiInstrumentFrame(
            distanceLabel = QisiDistanceFormat.segmentDistance(frame.distanceMeters),
            icon = frame.icon.coerceIn(0, 31),
            nextRoad = frame.road,
            routeDistanceLabel = QisiDistanceFormat.routeDistance(
                frame.routeRemainingMeters.takeIf { it >= 0 },
            ),
            etaLabel = QisiDistanceFormat.eta(
                frame.routeRemainingSeconds.takeIf { it >= 0 }?.toLong(),
            ),
            distanceUnit = units[0],
            roundaboutExit = if (frame.roundaboutExit in 1..16) frame.roundaboutExit - 1 else -1,
        )
        if (guidance == lastInstrument) return
        if (guidanceCallLocked(instrument, QisiVehicleBinder.INSTRUMENT_SEND_GUIDANCE) {
            QisiVehicleBinder.sendInstrumentGuidance(instrument, guidance)
        }) lastInstrument = guidance
    }

    private fun clearQisiLocked() {
        try {
            hudBinder?.let { hud ->
                if (hudActive) {
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ICON, intArrayOf(0))
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_DISTANCE,
                        intArrayOf(0, 0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ROUNDABOUT,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NAV_STATUS,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_CROSS_DISTANCE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_CROSS_RANGE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_INTERSECTION,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_LEFT_LANE_LINE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_RIGHT_LANE_LINE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_LANE_TYPE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_ROAD_TYPE,
                        intArrayOf(0),
                    )
                    sendHudStringLocked(hud, QisiVehicleBinder.HUD_SET_DESTINATION_NAME, "",
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_DESTINATION_DISTANCE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_REMAINING_TIME,
                        intArrayOf(0),
                    )
                    sendHudStringLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_ROAD, "",
                    )
                    sendHudStringIntLocked(hud, QisiVehicleBinder.HUD_SET_NEXT_ROAD_INFO, "", 0,
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_TARGET_EXIT,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_CURRENT_EXIT,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_TOTAL_DISTANCE,
                        intArrayOf(0),
                    )
                    sendHudIntsLocked(hud, QisiVehicleBinder.HUD_SET_CAMERA_DISTANCE,
                        intArrayOf(0),
                    )
                    DiagnosticLogging.logcat { Log.i(TAG, "HUD guidance cleared") }
                }
            }
            instrumentBinder?.let { instrument ->
                if (instrumentNavActive) {
                    if (instrumentTbtEnabled()) {
                        guidanceCallLocked(instrument, QisiVehicleBinder.INSTRUMENT_SEND_GUIDANCE) {
                            QisiVehicleBinder.sendInstrumentGuidance(instrument, QisiInstrumentFrame.cleared)
                        }
                    }
                    guidanceCallLocked(instrument, QisiVehicleBinder.INSTRUMENT_SET_ACTIVE) {
                        QisiVehicleBinder.transactInts(instrument, QisiVehicleBinder.TOKEN_INSTRUMENT,
                            QisiVehicleBinder.INSTRUMENT_SET_ACTIVE, intArrayOf(0))
                    }
                }
            }
        } catch (error: DeadObjectException) {
            DiagnosticLogging.logcat { Log.w(TAG, "adapter service not reachable", error) }
            resetModulesLocked()
            scheduleBindRetryLocked()
        } finally {
            hudActive = false
            instrumentNavActive = false
            DiagnosticLogging.logcat { Log.i(TAG, "guidance output ended") }
        }
    }

    private fun sendHudIntsLocked(hud: IBinder, code: Int, values: IntArray) =
        guidanceCallLocked(hud, code) {
            QisiVehicleBinder.transactInts(hud, QisiVehicleBinder.TOKEN_HUD, code, values)
        }

    private fun sendHudStringLocked(hud: IBinder, code: Int, value: String) =
        guidanceCallLocked(hud, code) {
            QisiVehicleBinder.transactString(hud, QisiVehicleBinder.TOKEN_HUD, code, value)
        }

    private fun sendHudStringIntLocked(hud: IBinder, code: Int, value: String, number: Int) =
        guidanceCallLocked(hud, code) {
            QisiVehicleBinder.transactStringInt(hud, QisiVehicleBinder.TOKEN_HUD, code, value, number)
        }

    /** Guidance shares the adapter with map projection. Only service death may tear both down. */
    private fun guidanceCallLocked(binder: IBinder, code: Int, send: () -> Unit): Boolean {
        val key = binder to code
        if (key in unsupportedGuidanceCalls) return false
        return try {
            send()
            true
        } catch (error: Exception) {
            if (error is DeadObjectException) throw error
            if (!binder.isBinderAlive || remoteBinder?.isBinderAlive == false) throw DeadObjectException()
            // Cache absent methods only for this connection. A negative result or rejected value
            // can be temporary, so it is retried on the next guidance update/keepalive.
            if (error is QisiVehicleBinder.UnsupportedTransaction) unsupportedGuidanceCalls += key
            if (reportedGuidanceFailures.add(key)) {
                DiagnosticLogging.logcat { Log.w(TAG, "guidance field failed tx=$code", error) }
                DiagnosticLogging.record {
                    val module = if (binder === hudBinder) "hud" else "instrument"
                    ConnectionDiagnosticTrace.record(
                        "QISI_GUIDANCE field failed module=$module tx=$code: ${error.javaClass.simpleName}: ${error.message}")
                }
            }
            false
        }
    }

    private fun applyInstrumentMapLocked() {
        val system = systemBinder ?: return
        val instrument = instrumentBinder ?: return
        val shouldShow = clusterMapShown && clusterMapCloseRequest != null &&
            Hs5OutputSettings.hs5ClusterVisible(context ?: return)
        try {
            if (shouldShow && !instrumentMapActive) {
                val request = clusterMapCloseRequest ?: return
                val session = QisiClusterMapSession(system, instrument, mainHandler, request)
                session.open()
                instrumentMapSession = session
                instrumentMapActive = true
                DiagnosticLogging.logcat { Log.i(TAG, "cluster map projection started") }
            } else if (!shouldShow && instrumentMapActive) {
                instrumentMapSession?.close()
                instrumentMapSession = null
                instrumentMapActive = false
                DiagnosticLogging.logcat { Log.i(TAG, "cluster map projection stopped") }
            }
        } catch (error: Exception) {
            DiagnosticLogging.logcat { Log.w(TAG, "instrument map stream failed", error) }
            DiagnosticLogging.record {
                ConnectionDiagnosticTrace.record("QISI_CLUSTER map request failed: ${error.javaClass.simpleName}: ${error.message}")
            }
        }
    }

    private fun instrumentTbtEnabled(): Boolean = legacyQisiInstrumentTbt()

    /** Some API 28 Hongqi builds accept instrument TBT parcels; others only show HUD icons. */
    private fun legacyQisiInstrumentTbt(): Boolean =
        Build.VERSION.SDK_INT == 28 &&
            (Build.MODEL == "HONG QI" || Build.DEVICE == "t1c_acrn")

    private fun bindAdapterLocked() {
        val appContext = context ?: return
        if (remoteBinder != null || binding) return
        val intent = Intent().setComponent(
            ComponentName(QisiVehicleBinder.ADAPTER_PACKAGE, QisiVehicleBinder.ADAPTER_SERVICE),
        )
        try {
            binding = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!binding) {
                DiagnosticLogging.logcat { Log.w(TAG, "adapter service bind denied") }
                scheduleBindRetryLocked()
            }
        } catch (error: Exception) {
            binding = false
            DiagnosticLogging.logcat { Log.w(TAG, "adapter service bind failed: ${error.javaClass.simpleName}", error) }
            scheduleBindRetryLocked()
        }
    }

    private fun scheduleBindRetryLocked() {
        mainHandler.removeCallbacks(bindRetry)
        mainHandler.postDelayed(bindRetry, BIND_RETRY_MS)
    }

    private fun attachRemoteService(service: IBinder) = synchronized(lock) {
        binding = true
        remoteBinder = service
        try {
            instrumentBinder = QisiVehicleBinder.queryModule(
                service,
                QisiVehicleBinder.MODULE_INSTRUMENT,
                QisiVehicleBinder.TOKEN_INSTRUMENT,
            )
            systemBinder = QisiVehicleBinder.queryModule(
                service,
                QisiVehicleBinder.MODULE_SYSTEM,
                QisiVehicleBinder.TOKEN_SYSTEM,
            )
            // Full-screen navigation needs modules 8/13, independently of the optional HUD module.
            hudBinder = runCatching {
                QisiVehicleBinder.queryModule(service, QisiVehicleBinder.MODULE_HUD,
                    QisiVehicleBinder.TOKEN_HUD)
            }.getOrNull()
            DiagnosticLogging.record {
                ConnectionDiagnosticTrace.record("QISI_CLUSTER adapter modules ready instrument=true system=true hud=${hudBinder != null}")
            }
            if (!modulesReadyLogged) {
                modulesReadyLogged = true
                DiagnosticLogging.logcat { Log.i(TAG, "navigation modules ready hud=${hudBinder != null}") }
            }
            applyInstrumentMapLocked()
            queueApplyLocked(force = true)
        } catch (error: Exception) {
            DiagnosticLogging.logcat { Log.w(TAG, "adapter service not reachable", error) }
            DiagnosticLogging.record {
                ConnectionDiagnosticTrace.record("QISI_CLUSTER adapter modules failed: ${error.javaClass.simpleName}: ${error.message}")
            }
            resetModulesLocked()
            scheduleBindRetryLocked()
        }
    }

    private fun resetModulesLocked() {
        instrumentMapSession?.close()
        instrumentMapSession = null
        hudBinder = null
        instrumentBinder = null
        systemBinder = null
        remoteBinder = null
        binding = false
        hudActive = false
        instrumentNavActive = false
        instrumentMapActive = false
        modulesReadyLogged = false
        lastInstrument = null
        unsupportedGuidanceCalls.clear()
        reportedGuidanceFailures.clear()
        context?.let { app ->
            runCatching { app.unbindService(connection) }
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder) {
            attachRemoteService(service)
        }

        override fun onServiceDisconnected(name: ComponentName?) = synchronized(lock) {
            resetModulesLocked()
            scheduleBindRetryLocked()
        }

        override fun onBindingDied(name: ComponentName?) = synchronized(lock) {
            resetModulesLocked()
            scheduleBindRetryLocked()
        }

        override fun onNullBinding(name: ComponentName?) = synchronized(lock) {
            binding = false
            scheduleBindRetryLocked()
        }
    }

    private fun sendBroadcastLocked(frame: Hs5GuidanceFrame): Boolean {
        val intent = baseIntent(KEY_GUIDANCE).apply {
            putExtra("TYPE", 0)
            putExtra("EXTRA_STATE", 0)
            putExtra("EXTRA_IS_FOREGROUND", 1)
            putExtra("NEW_ICON", frame.icon)
            putExtra("ROUNG_ABOUT_NUM", frame.roundaboutExit)
            putExtra("SEG_REMAIN_DIS", frame.distanceMeters)
            putExtra("NEXT_ROAD_NAME", frame.road)
            putExtra("ROUTE_REMAIN_DIS", frame.routeRemainingMeters)
            putExtra("ROUTE_REMAIN_TIME", frame.routeRemainingSeconds)
            putExtra("CUR_ROAD_NAME", frame.road)
            putExtra("DESTINATION_NAME", frame.destination)
            putExtra("JUNCTION_TYPE", frame.junctionType)
            putExtra("JUNCTION_ANGLES", frame.junctionAngles.toIntArray())
            putExtra("EXIT_ANGLE", frame.exitAngle)
            putExtra("EXIT_INFO", frame.exitInfo)
            putExtra("LANE_GUIDANCE_INDEX", frame.laneGuidanceIndex)
            putExtra("LANE_GUIDANCE_SHOWING", frame.laneGuidanceShowing)
            putExtra("LANE_GUIDANCE_TOTAL", frame.laneGuidanceTotal)
            putExtra("LANE_GUIDANCE_DESCRIPTION", frame.laneGuidanceDescription)
            putLaneGuidanceExtras(frame)
        }
        return broadcastLocked(intent)
    }

    private fun clearBroadcastLocked() {
        val intent = baseIntent(KEY_STATE).apply {
            putExtra("EXTRA_STATE", STATE_ENDED)
            putExtra("EXTRA_IS_FOREGROUND", 1)
            putExtra("NEW_ICON", -1)
            putExtra("SEG_REMAIN_DIS", -1)
            putExtra("NEXT_ROAD_NAME", "")
            putExtra("ROUTE_REMAIN_DIS", -1)
            putExtra("ROUTE_REMAIN_TIME", -1)
            putExtra("DESTINATION_NAME", "")
            putExtra("JUNCTION_TYPE", -1)
            putExtra("JUNCTION_ANGLES", intArrayOf())
            putExtra("EXIT_ANGLE", 1000)
            putExtra("EXIT_INFO", "")
            putExtra("LANE_GUIDANCE_INDEX", -1)
            putExtra("LANE_GUIDANCE_SHOWING", false)
            putExtra("LANE_GUIDANCE_TOTAL", 0)
            putExtra("LANE_GUIDANCE_DESCRIPTION", "")
            putLaneGuidanceExtras(Hs5GuidanceFrame(0, 0, -1))
        }
        if (!broadcastLocked(intent)) return
        DiagnosticLogging.logcat { Log.i(TAG, "hongqi guidance ended") }
    }

    private fun baseIntent(keyType: Int): Intent {
        val pkg = amapTargetPackage ?: adapter?.packageName ?: Hs5NavPackages.AMAP_AUTO.packageName
        return Intent(AMAP_ACTION).apply {
            setPackage(pkg)
            addFlags(FLAG_RECEIVER_INCLUDE_BACKGROUND)
            putExtra("KEY_TYPE", keyType)
        }
    }

    private fun broadcastLocked(intent: Intent): Boolean {
        val appContext = context ?: return false
        val targets = Hs5NavPackages.navigationOutputPackages(appContext).ifEmpty {
            listOfNotNull(amapTargetPackage, adapter?.packageName)
        }
        for (pkg in targets) {
            if (sendLocked(intent, pkg)) return true
        }
        return sendLocked(intent, null)
    }

    private fun sendLocked(intent: Intent, packageName: String?): Boolean {
        val appContext = context ?: return false
        val copy = Intent(intent)
        if (packageName != null) copy.setPackage(packageName) else copy.setPackage(null)
        return try {
            appContext.sendBroadcast(copy)
            true
        } catch (error: RuntimeException) {
            DiagnosticLogging.logcat { Log.w(TAG, "nav broadcast failed pkg=$packageName", error) }
            false
        }
    }
}
