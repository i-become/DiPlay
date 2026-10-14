package com.shilapi.xcertplay.hud

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
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
    private val route = BydHudRouteState()
    private var context: Context? = null
    private var adapter: Hs5NavPackages? = null
    private var amapTargetPackage: String? = null
    private var available = false
    private var useQisi = false
    private var senderStarted = false
    private var lastSent: BydClusterFrame? = null
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
    private var clusterMapShown = false
    private var lastInstrument: QisiInstrumentFrame? = null

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
        available = !Hs5HeadUnit.isBydHeadUnit(appContext) &&
            (useQisi || outputPackages.isNotEmpty())
        Log.i(
            TAG,
            "hs5 guidance available=$available qisi=$useQisi target=$amapTargetPackage outputs=$outputPackages",
        )
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

    fun onFrame(frame: Iap2Frame) = synchronized(lock) {
        if (!available) return@synchronized
        when (route.accept(frame.messageId, frame.payload)) {
            BydHudRouteChange.GUIDANCE -> queueApplyLocked()
            BydHudRouteChange.CLEAR -> if (lastSent != null || hudActive || instrumentNavActive) sendEndLocked()
            BydHudRouteChange.NONE -> Unit
        }
    }

    fun clear() = synchronized(lock) {
        route.clear()
        if (available) sendEndLocked()
    }

    private fun tick() = synchronized(lock) {
        if (useQisi && remoteBinder == null && !binding) mainHandler.post { bindAdapterLocked() }
        if (lastSent != null && route.currentApple() == null) sendEndLocked()
        else if (++ticksSinceSend >= KEEPALIVE_TICKS) queueApplyLocked(force = true)
    }

    private fun queueApplyLocked(force: Boolean = false) {
        mainHandler.post { applyGuidanceLocked(force) }
    }

    private fun applyGuidanceLocked(force: Boolean = false) = synchronized(lock) {
        if (context?.let(BydOutputSettings::enabled) != true) {
            if (lastSent != null || hudActive || instrumentNavActive) sendEndLocked()
            return@synchronized
        }
        val frame = route.currentApple()?.let(BydClusterFrame::from)
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
                Log.i(TAG, "guidance frame sent $frame qisi=$useQisi")
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

    private fun sendQisiLocked(frame: BydClusterFrame): Boolean {
        val hud = hudBinder ?: return false
        val instrument = instrumentBinder
        return try {
            sendHudLocked(hud, frame)
            if (instrument != null && instrumentTbtEnabled()) {
                sendInstrumentGuidanceLocked(instrument, frame)
            }
            true
        } catch (error: Exception) {
            Log.w(TAG, "adapter service not reachable${error.message?.let { ": $it" }.orEmpty()}", error)
            resetModulesLocked()
            scheduleBindRetryLocked()
            false
        }
    }

    private fun sendHudLocked(hud: IBinder, frame: BydClusterFrame) {
        val icon = frame.icon.coerceIn(0, 31)
        QisiVehicleBinder.transactInts(hud, QisiVehicleBinder.TOKEN_HUD, QisiVehicleBinder.HUD_SET_ICON, intArrayOf(icon))
        val distance = QisiVehicleBinder.hudDistanceUnits(frame.distanceMeters)
        QisiVehicleBinder.transactInts(
            hud,
            QisiVehicleBinder.TOKEN_HUD,
            QisiVehicleBinder.HUD_SET_DISTANCE,
            distance,
        )
        val roundabout = frame.roundaboutExit.coerceIn(0, 16)
        QisiVehicleBinder.transactInts(
            hud,
            QisiVehicleBinder.TOKEN_HUD,
            QisiVehicleBinder.HUD_SET_ROUNDABOUT,
            intArrayOf(roundabout),
        )
        hudActive = true
    }

    private fun sendInstrumentGuidanceLocked(instrument: IBinder, frame: BydClusterFrame) {
        if (!instrumentNavActive) {
            QisiVehicleBinder.transactInts(
                instrument,
                QisiVehicleBinder.TOKEN_INSTRUMENT,
                QisiVehicleBinder.INSTRUMENT_SET_ACTIVE,
                intArrayOf(1),
            )
            instrumentNavActive = true
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
        QisiVehicleBinder.sendInstrumentGuidance(instrument, guidance)
        lastInstrument = guidance
    }

    private fun clearQisiLocked() {
        try {
            hudBinder?.let { hud ->
                if (hudActive) {
                    QisiVehicleBinder.transactInts(hud, QisiVehicleBinder.TOKEN_HUD, QisiVehicleBinder.HUD_SET_ICON, intArrayOf(0))
                    QisiVehicleBinder.transactInts(
                        hud,
                        QisiVehicleBinder.TOKEN_HUD,
                        QisiVehicleBinder.HUD_SET_DISTANCE,
                        intArrayOf(0, 0),
                    )
                    QisiVehicleBinder.transactInts(
                        hud,
                        QisiVehicleBinder.TOKEN_HUD,
                        QisiVehicleBinder.HUD_SET_ROUNDABOUT,
                        intArrayOf(0),
                    )
                    Log.i(TAG, "HUD guidance cleared")
                }
            }
            instrumentBinder?.let { instrument ->
                if (instrumentNavActive) {
                    if (instrumentTbtEnabled()) {
                        QisiVehicleBinder.sendInstrumentGuidance(instrument, QisiInstrumentFrame.cleared)
                    }
                    QisiVehicleBinder.transactInts(
                        instrument,
                        QisiVehicleBinder.TOKEN_INSTRUMENT,
                        QisiVehicleBinder.INSTRUMENT_SET_ACTIVE,
                        intArrayOf(0),
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "adapter service not reachable", error)
            resetModulesLocked()
            scheduleBindRetryLocked()
        } finally {
            hudActive = false
            instrumentNavActive = false
            Log.i(TAG, "guidance output ended")
        }
    }

    private fun applyInstrumentMapLocked() {
        val system = systemBinder ?: return
        val instrument = instrumentBinder ?: return
        if (!BydOutputSettings.hs5ClusterVisible(context ?: return)) return
        try {
            if (clusterMapShown && !instrumentMapActive) {
                QisiVehicleBinder.transactInts(
                    system,
                    QisiVehicleBinder.TOKEN_SYSTEM,
                    QisiVehicleBinder.SYSTEM_INSTRUMENT_MAP,
                    intArrayOf(1, 0),
                )
                QisiVehicleBinder.setInstrumentMapMode(instrument, INSTRUMENT_MAP_STAGE_PREPARE)
                QisiVehicleBinder.setInstrumentMapMode(instrument, INSTRUMENT_MAP_STAGE_PRESENT)
                QisiVehicleBinder.transactInts(
                    instrument,
                    QisiVehicleBinder.TOKEN_INSTRUMENT,
                    QisiVehicleBinder.INSTRUMENT_SET_ACTIVE,
                    intArrayOf(1),
                )
                instrumentMapActive = true
                Log.i(TAG, "cluster map projection started")
            } else if (!clusterMapShown && instrumentMapActive) {
                QisiVehicleBinder.transactInts(
                    instrument,
                    QisiVehicleBinder.TOKEN_INSTRUMENT,
                    QisiVehicleBinder.INSTRUMENT_SET_ACTIVE,
                    intArrayOf(0),
                )
                QisiVehicleBinder.transactInts(
                    system,
                    QisiVehicleBinder.TOKEN_SYSTEM,
                    QisiVehicleBinder.SYSTEM_INSTRUMENT_MAP,
                    intArrayOf(0, 0),
                )
                instrumentMapActive = false
                Log.i(TAG, "cluster map projection stopped")
            }
        } catch (error: Exception) {
            Log.w(TAG, "instrument map stream failed", error)
        }
    }

    private fun instrumentTbtEnabled(): Boolean = legacyQisiInstrumentTbt()

    /** Some API 28 Hongqi builds accept instrument TBT parcels; others only show HUD icons. */
    private fun legacyQisiInstrumentTbt(): Boolean =
        Build.VERSION.SDK_INT == 28 &&
            (Build.MODEL == "HONG QI" || Build.DEVICE == "t1c_acrn")

    private const val INSTRUMENT_MAP_STAGE_PREPARE = 2
    private const val INSTRUMENT_MAP_STAGE_PRESENT = 4

    private fun bindAdapterLocked() {
        val appContext = context ?: return
        if (remoteBinder != null || binding) return
        val intent = Intent().setComponent(
            ComponentName(QisiVehicleBinder.ADAPTER_PACKAGE, QisiVehicleBinder.ADAPTER_SERVICE),
        )
        try {
            binding = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!binding) {
                Log.w(TAG, "adapter service bind denied")
                scheduleBindRetryLocked()
            }
        } catch (error: Exception) {
            binding = false
            Log.w(TAG, "adapter service bind failed: ${error.javaClass.simpleName}", error)
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
            hudBinder = QisiVehicleBinder.queryModule(
                service,
                QisiVehicleBinder.MODULE_HUD,
                QisiVehicleBinder.TOKEN_HUD,
            )
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
            if (!modulesReadyLogged) {
                modulesReadyLogged = true
                Log.i(TAG, "navigation modules ready hud=${hudBinder != null}")
            }
            applyInstrumentMapLocked()
            queueApplyLocked(force = true)
        } catch (error: Exception) {
            Log.w(TAG, "adapter service not reachable", error)
            resetModulesLocked()
            scheduleBindRetryLocked()
        }
    }

    private fun resetModulesLocked() {
        hudBinder = null
        instrumentBinder = null
        systemBinder = null
        remoteBinder = null
        binding = false
        hudActive = false
        instrumentNavActive = false
        instrumentMapActive = false
        modulesReadyLogged = false
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

    private fun sendBroadcastLocked(frame: BydClusterFrame): Boolean {
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
        }
        if (!broadcastLocked(intent)) return
        Log.i(TAG, "hongqi guidance ended")
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
            Log.w(TAG, "nav broadcast failed pkg=$packageName", error)
            false
        }
    }
}
