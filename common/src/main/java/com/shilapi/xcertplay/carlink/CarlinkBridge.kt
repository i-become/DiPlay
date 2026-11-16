package com.shilapi.xcertplay.carlink

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.util.Log
import android.view.Surface
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import java.security.SecureRandom
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.ucar.vehiclesdk.ICarConnectListener
import com.ucar.vehiclesdk.ICarInitCallback
import com.ucar.vehiclesdk.IPhoneDataListener
import com.ucar.vehiclesdk.UCarAdapter
import com.ucar.vehiclesdk.UCarCommon
import com.ucar.vehiclesdk.UCarConfig
import com.share.connect.WifiOwnerConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.hud.Hs5NavigationOutputs

/** Process-wide ICCOA/Carlink owner. It is never created on the CarPlay path. */
class CarlinkBridge private constructor(private val context: Context) : ICarConnectListener, IPhoneDataListener {
    data class Snapshot(val state: CarlinkLinkState = CarlinkLinkState.IDLE, val pin: String? = null, val device: String? = null, val error: Int? = null, val projecting: Boolean = false, val progressStep: Int = 0, val sessionId: Long = 0, val desktopRequested: Boolean = false)
    @Volatile var navigation: com.shilapi.xcertplay.hud.ClusterTurnGuidance? = null
        private set
    private val _snapshot = MutableStateFlow(Snapshot())
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()
    fun recordPermissions(missing: List<String>) { log("permission precheck missing=${missing.joinToString().ifEmpty { "none" }}") }
    internal fun recordProjectionEvent(event: String) = log("projection $event")
    fun diagnosticSummary(): String = "state=${_snapshot.value.state} device=${_snapshot.value.device.orEmpty()} " +
        "error=${_snapshot.value.error ?: "none"} projecting=${_snapshot.value.projecting} " +
        "sdkReady=$sdkReady active=$active surfaceValid=${surface?.isValid == true} " +
        "castStarted=$castStarted castRetry=$castRetry canvas=${width}x$height fps=${com.shilapi.xcertplay.AirPlayPersistence.loadFps(context)} " +
        "musicCallbacks=$musicCallbacks lyricCallbacks=$lyricCallbacks navigationCallbacks=$navigationCallbacks"
    private val adapter by lazy { UCarAdapter.getInstance() }
    private var initPending = false
    private var sessionSequence = 0L
    private var wirelessFailureStreak = 0
    private var firstFailure: String? = null
    @Volatile private var musicCallbacks = 0L
    @Volatile private var lyricCallbacks = 0L
    @Volatile private var navigationCallbacks = 0L
    private val trace = java.util.ArrayDeque<String>()
    @Synchronized fun diagnosticTrace(): String = "firstFailure=${firstFailure ?: "none"}\n" + trace.joinToString("\n")
    @Volatile private var initialized = false
    @Volatile private var sdkReady = false
    @Volatile private var active = false
    private var surface: Surface? = null
    private var width = 1920
    private var height = 720
    private var connecting = false
    private var castStarted = false
    private var castRetry = 0
    private val main = Handler(Looper.getMainLooper())
    private val bluetoothAdapter: BluetoothAdapter? by lazy { BluetoothAdapter.getDefaultAdapter() }
    private var bluetoothReceiverRegistered = false
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action != BluetoothDevice.ACTION_FOUND || !active) return
            val device = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") (intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE))
            val selected = com.shilapi.xcertplay.DiPlayPreferences.phoneAddress(context)
            if (device != null && selected != null && device.address.equals(selected, true)) {
                log("bluetooth target discovered bonded=${device.bondState == BluetoothDevice.BOND_BONDED}")
                runCatching { bluetoothAdapter?.cancelDiscovery() }
            }
        }
    }
    private val castRetryTask = Runnable { tryStartCast() }
    private val maxCastRetries = 20
    private val discoveryRetry = Runnable { startAdvertising() }
    private val discoveryWatchdog = object : Runnable {
        override fun run() {
            if (!active || !sdkReady) return
            if (_snapshot.value.pin.isNullOrBlank() && !connecting && !castStarted &&
                _snapshot.value.state != CarlinkLinkState.CONNECTED) startAdvertising()
            main.postDelayed(this, 30_000L)
        }
    }
    private fun retryDiscovery(delay: Long = 1200L) {
        main.removeCallbacks(discoveryRetry)
        if (active && sdkReady) main.postDelayed(discoveryRetry, delay)
    }

    fun start(width: Int = this.width, height: Int = this.height) {
        if (com.shilapi.xcertplay.HongqiScreenStatus.screenOff) return
        if (sdkReady && _snapshot.value.state == CarlinkLinkState.CONNECTED) return
        log("start requested size=${width}x$height abi=${Build.SUPPORTED_ABIS.joinToString()}")
        com.shilapi.xcertplay.Hs5SteeringKeys.attachCarlink(context, this)
        active = true
        startBluetoothDiscovery()
        // The SDK negotiates this canvas once. View/surface callbacks must never change it.
        if (!initialized && !initPending) { this.width = width.coerceAtLeast(1); this.height = height.coerceAtLeast(1) }
        if (initPending) return
        if (initialized) {
            main.removeCallbacks(discoveryWatchdog)
            main.postDelayed(discoveryWatchdog, 30_000L)
            if (sdkReady && (_snapshot.value.state == CarlinkLinkState.IDLE || _snapshot.value.state == CarlinkLinkState.ERROR)) startAdvertising()
            return
        }
        val missing = listOf("libovmsink.so", "libusbio.so").filter {
            !java.io.File(context.applicationInfo.nativeLibraryDir, it).isFile
        }
        if (missing.isNotEmpty()) {
            setState(CarlinkLinkState.UNSUPPORTED)
            log("native precheck failed missing=${missing.joinToString()} abi=${Build.SUPPORTED_ABIS.joinToString()}")
            return
        }
        try {
            initPending = true
            adapter.registerCarConnectListener(this); adapter.registerPhoneDataListener(this)
            val config = UCarConfig.Builder()
                .setCarBrMac(carIdentity()).setDpi(320).setRealDpi(context.resources.displayMetrics.densityDpi.coerceIn(120, 640)).setFps(com.shilapi.xcertplay.AirPlayPersistence.loadFps(context))
                .setScreenWidth(this.width).setScreenHeight(this.height).setVideoDisplayWidth(this.width).setVideoDisplayHeight(this.height)
                .setSupportP2P(true).setSupportSoftAP(false).setAllowed5gChannels(intArrayOf(149, 153, 157, 161, 165))
                .setSupportCamera(false).setSupportHevc(false).setSupportMic(true).setUseCustomAudioRecord(false)
                .setSupportStereoRecord(false).setSupportLowLatencyDecodingMode(false).setSupportPOINav(false)
                .setCcdFilePath("ccd.data").setSupportedWorkModes(UCarCommon.WorkMode.Builder().setSupportNormalCast(true).build()).build()
            val submitted = adapter.init(context, config, object : ICarInitCallback {
                override fun onInitSuccess() {
                    main.post {
                        initPending = false
                        initialized = true
                        sdkReady = true
                        main.removeCallbacks(discoveryWatchdog)
                        main.postDelayed(discoveryWatchdog, 30_000L)
                        _snapshot.value = _snapshot.value.copy(progressStep = 1)
                        log("SDK init success")
                        if (active) startAdvertising()
                        tryStartCast()
                    }
                }
                override fun onInitFailed(errCode: Int) { main.post { initPending = false; initialized = false; sdkReady = false; setState(CarlinkLinkState.ERROR, errCode); log("SDK init failed error=$errCode") } }
            })
            if (!submitted) { initPending = false; initialized = false; setState(CarlinkLinkState.ERROR); log("SDK init submission failed") }
        } catch (error: Throwable) {
            initPending = false
            initialized = false
            setState(CarlinkLinkState.ERROR)
            log("start failed ${error.javaClass.simpleName}: ${error.message}")
        }
        log("start initialized=$initialized abi=${Build.SUPPORTED_ABIS.joinToString()}")
    }
    private fun startAdvertising() {
        if (!active || !sdkReady || !CarlinkSessionPolicy.canAdvertise(
                _snapshot.value.state, _snapshot.value.pin, connecting, castStarted)) return
        main.removeCallbacks(discoveryRetry)
        runCatching {
            adapter.registerCarConnectListener(this)
            adapter.registerPhoneDataListener(this)
            adapter.stopAdvertise()
            adapter.startAdvertise()
        }.onSuccess { started ->
            if (started) {
                setState(CarlinkLinkState.ADVERTISING)
                _snapshot.value = _snapshot.value.copy(progressStep = 2)
            } else { setState(CarlinkLinkState.ERROR); retryDiscovery() }
            log("startAdvertise=$started channels=149,153,157,161,165 P2P=true SoftAP=false")
        }.onFailure {
            log("startAdvertise failed ${it.javaClass.simpleName}: ${it.message}")
            setState(CarlinkLinkState.ERROR)
            retryDiscovery()
        }
    }
    fun stop() {
        active = false
        stopBluetoothDiscovery()
        main.removeCallbacks(discoveryRetry)
        main.removeCallbacks(discoveryWatchdog)
        main.removeCallbacks(castRetryTask)
        onSurfaceDestroyed()
        com.shilapi.xcertplay.Hs5SteeringKeys.detachCarlink(this)
        runCatching { adapter.stopAdvertise() }
        runCatching { adapter.disconnect() }
        connecting = false
        navigation = null; CarlinkDashboardState.clear(); CarPlayGlance.clearExternal(); Hs5NavigationOutputs.onExternalNavigation(false, 0, 0, "")
        _snapshot.value = Snapshot()
        log("stop; SDK initialization retained")
    }
    private fun startBluetoothDiscovery() {
        val adapter = bluetoothAdapter ?: run { log("bluetooth discovery unavailable: no adapter"); return }
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            log("bluetooth discovery skipped: connect permission missing"); return
        }
        if (Build.VERSION.SDK_INT >= 31 && context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            log("bluetooth discovery skipped: scan permission missing"); return
        }
        runCatching {
            if (!bluetoothReceiverRegistered) {
                val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
                if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(bluetoothReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else @Suppress("DEPRECATION") context.registerReceiver(bluetoothReceiver, filter)
                bluetoothReceiverRegistered = true
            }
            if (adapter.isDiscovering) adapter.cancelDiscovery()
            val started = adapter.startDiscovery()
            log("bluetooth discovery started=$started targetPresent=${!com.shilapi.xcertplay.DiPlayPreferences.phoneAddress(context).isNullOrBlank()}")
        }.onFailure { log("bluetooth discovery failed ${it.javaClass.simpleName}: ${it.message}") }
    }
    private fun stopBluetoothDiscovery() {
        runCatching { bluetoothAdapter?.cancelDiscovery() }
        if (bluetoothReceiverRegistered) runCatching { context.unregisterReceiver(bluetoothReceiver) }
        bluetoothReceiverRegistered = false
    }
    private fun clearExternalSession() {
        navigation = null
        CarlinkDashboardState.clear()
        CarPlayGlance.clearExternal()
        Hs5NavigationOutputs.onExternalNavigation(false, 0, 0, "")
    }
    fun disconnectSession() {
        clearExternalSession()
        onSurfaceDestroyed()
        connecting = false
        runCatching { adapter.disconnect() }
        retryDiscovery(1200L)
        setState(CarlinkLinkState.ADVERTISING)
        log("session disconnected; advertising retained")
    }
    fun onSurfaceDestroyed(owner: Surface? = null) {
        if (owner != null && surface !== owner) return
        main.removeCallbacks(castRetryTask)
        // The default pauseCast() also tells the phone to leave projection. A window
        // disappearing only releases the local decoder; it must not end the session.
        if (surface != null) runCatching { adapter.pauseCast(false) }
        surface = null
        castStarted = false
        castRetry = 0
        _snapshot.value = _snapshot.value.copy(projecting = false,
            progressStep = if (connecting) 4 else _snapshot.value.progressStep.coerceAtMost(2))
        log("surface destroyed; session retained")
    }
    fun setSurface(surface: Surface?) {
        if (this.surface === surface) return
        main.removeCallbacks(castRetryTask)
        if (this.surface != null) runCatching { adapter.pauseCast(false) }
        this.surface = surface; castStarted = false
        log("surface bound canvas=${width}x$height valid=${surface?.isValid == true}")
        castRetry = 0
        if (surface?.isValid == true) {
            if (connecting) _snapshot.value = _snapshot.value.copy(progressStep = 5)
            tryStartCast()
        }
    }
    private fun tryStartCast() {
        if (!active || !sdkReady || _snapshot.value.state != CarlinkLinkState.CONNECTED || castStarted || surface?.isValid != true) return
        val started = runCatching { adapter.startCast(surface, width, height) }
            .onFailure { log("startCast exception ${it.javaClass.simpleName}: ${it.message}") }
            .getOrDefault(false)
        castStarted = started
        _snapshot.value = _snapshot.value.copy(projecting = started, progressStep = if (started) 6 else if (surface?.isValid == true && connecting) 5 else _snapshot.value.progressStep)
        log("startCast=$started size=${width}x$height attempt=${castRetry + 1}")
        if (!started && castRetry < maxCastRetries) {
            castRetry++
            main.removeCallbacks(castRetryTask)
            main.postDelayed(castRetryTask, 500L)
        } else if (started) {
            castRetry = 0
        }
    }
    fun resumeProjection() { _snapshot.value = _snapshot.value.copy(desktopRequested = false) }
    val videoWidth: Int get() = width
    val videoHeight: Int get() = height
    fun sendKey(keyCode: Int, action: Int): Boolean {
        val mapped = when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> UCarCommon.KeyCodeType.KEY_CODE_DPAD_UP
            KeyEvent.KEYCODE_DPAD_DOWN -> UCarCommon.KeyCodeType.KEY_CODE_DPAD_DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> UCarCommon.KeyCodeType.KEY_CODE_DPAD_LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> UCarCommon.KeyCodeType.KEY_CODE_DPAD_RIGHT
            KeyEvent.KEYCODE_DPAD_CENTER -> UCarCommon.KeyCodeType.KEY_CODE_DPAD_CENTER
            KeyEvent.KEYCODE_ENTER -> UCarCommon.KeyCodeType.KEY_CODE_ENTER
            KeyEvent.KEYCODE_BACK -> UCarCommon.KeyCodeType.KEY_CODE_BACK
            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> UCarCommon.KeyCodeType.KEY_CODE_MEDIA
            KeyEvent.KEYCODE_MEDIA_PLAY -> UCarCommon.KeyCodeType.KEY_CODE_MEDIA_PLAY
            KeyEvent.KEYCODE_MEDIA_PAUSE -> UCarCommon.KeyCodeType.KEY_CODE_MEDIA_PAUSE
            KeyEvent.KEYCODE_MEDIA_NEXT -> UCarCommon.KeyCodeType.KEY_CODE_MEDIA_NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> UCarCommon.KeyCodeType.KEY_CODE_MEDIA_PREVIOUS
            KeyEvent.KEYCODE_CALL -> UCarCommon.KeyCodeType.KEY_CODE_CALL
            KeyEvent.KEYCODE_ENDCALL -> UCarCommon.KeyCodeType.KEY_CODE_ENDCALL
            else -> return false
        }
        val eventAction = when (action) {
            KeyEvent.ACTION_DOWN -> UCarCommon.KeyEventActionType.KEY_EVENT_ACTION_DOWN
            KeyEvent.ACTION_UP -> UCarCommon.KeyEventActionType.KEY_EVENT_ACTION_UP
            else -> return false
        }
        return runCatching { adapter.sendKeyEvent(eventAction, mapped, 0) }.getOrDefault(false)
    }
    fun sendTouch(action: Int, ids: IntArray, xs: IntArray, ys: IntArray): Boolean =
        ids.isNotEmpty() && ids.size == xs.size && ids.size == ys.size && runCatching { adapter.sendTouchEvent(action, ids.size, ids, xs, ys) }.getOrDefault(false)
    fun refreshPairing() { if (active && sdkReady && _snapshot.value.state == CarlinkLinkState.ADVERTISING && !connecting && !castStarted) { runCatching { adapter.stopAdvertise() }; startAdvertising(); log("refresh pairing") } }
    override fun onPinCode(pinCode: String?, carDeviceName: String?) { main.post { if (active && !pinCode.isNullOrBlank()) _snapshot.value = _snapshot.value.copy(state = CarlinkLinkState.PAIRING, pin = pinCode, device = carDeviceName, progressStep = 3) }; log("pin received") }
    override fun onConnectStateChanged(deviceID: String?, state: Int, parameter: Int) {
        log("state=$state parameter=$parameter device=${deviceID.orEmpty()}")
        main.post {
            if (!active) return@post
            when (state) {
                1 -> {
                    clearExternalSession()
                    connecting = false
                    onSurfaceDestroyed()
                    _snapshot.value = _snapshot.value.copy(state = CarlinkLinkState.ADVERTISING, pin = null, error = null, projecting = false, progressStep = 2)
                    retryDiscovery()
                }
                2, 4 -> {
                    if (_snapshot.value.state != CarlinkLinkState.CONNECTED) {
                        sessionSequence++
                        musicCallbacks = 0L; lyricCallbacks = 0L; navigationCallbacks = 0L
                        CarPlayGlance.onExternalNavigation(false, 0, "")
                        _snapshot.value = _snapshot.value.copy(desktopRequested = false)
                    }
                    wirelessFailureStreak = 0
                    main.removeCallbacks(discoveryRetry)
                    connecting = true; com.shilapi.xcertplay.DiPlayPreferences.markCarlinkPhonePaired(context, com.shilapi.xcertplay.DiPlayPreferences.phoneAddress(context)); _snapshot.value = _snapshot.value.copy(state = CarlinkLinkState.CONNECTED, sessionId = sessionSequence, error = null, pin = _snapshot.value.pin, progressStep = if (surface?.isValid == true) 5 else 4); tryStartCast() }
                3 -> {
                    clearExternalSession()
                    wirelessFailureStreak = if (parameter in setOf(10003, 10006, 10008)) wirelessFailureStreak + 1 else 0
                    connecting = false; onSurfaceDestroyed(); castStarted = false; _snapshot.value = _snapshot.value.copy(state = CarlinkLinkState.ERROR, error = parameter, pin = null, projecting = false); main.removeCallbacks(castRetryTask); retryDiscovery(CarlinkSessionPolicy.retryDelay(parameter, wirelessFailureStreak)) }
                // SDK NotifyMirrorState.STATE_GO_TO_DESKTOP; keep the connection alive.
                6 -> { _snapshot.value = _snapshot.value.copy(desktopRequested = true); log("desktop requested") }
                5 -> { castStarted = false; _snapshot.value = _snapshot.value.copy(projecting = false) }
                else -> Unit
            }
        }
    }
    override fun onConnectingProgress(deviceID: String?, progress: Int) {
        main.post { if (active && progress in 1..99) { connecting = true; main.removeCallbacks(discoveryRetry) } }
        log("connecting progress=$progress")
    }
    override fun onReconfigureWifiInfo(config: WifiOwnerConfig?) = Unit
    override fun onRethinkBluetoothConnect(rethink: Boolean) {
        log("SDK requested bluetooth reconnect=$rethink")
        if (rethink && active) main.post { startBluetoothDiscovery() }
    }
    override fun onSelectWorkMode(deviceID: String?, supported: UCarCommon.WorkMode): UCarCommon.WorkMode = UCarCommon.WorkMode.NORMAL_CAST
    override fun onNavigationInfoReceived(phoneId: String?, info: UCarCommon.NavigationInfo?) {
        if (!active || _snapshot.value.state != CarlinkLinkState.CONNECTED) return
        if (info == null) return
        navigationCallbacks++
        val next = CarlinkNavigation.guidance(info.isNavigating, info.distance, info.distanceUnit,
            info.operation, info.where, info.title1, info.title2, info.directionIcon)
        navigation = next
        CarPlayGlance.onExternalNavigation(info.isNavigating, next?.distanceMeters ?: 0, next?.road.orEmpty(),
            next?.let { CarlinkNavigation.appleManeuver(it.icon) }, next?.directionIcon, next?.routeSummary.orEmpty())
        Hs5NavigationOutputs.onExternalNavigation(info.isNavigating, next?.icon ?: 0, next?.distanceMeters ?: 0, next?.road.orEmpty())
        log("navigation received navigating=${info.isNavigating} distance=${info.distance} unit=${info.distanceUnit} " +
            "operation=${info.operation} where=${info.where} title1=${info.title1} title2=${info.title2} " +
            "iconBytes=${info.directionIcon?.size ?: 0} mappedIcon=${next?.icon}")
    }
    override fun onMusicInfoReceived(phoneId: String?, info: UCarCommon.MusicInfo?) {
        if (!active || _snapshot.value.state != CarlinkLinkState.CONNECTED) return
        musicCallbacks++
        if (info == null || info.isDestroyed) { CarlinkDashboardState.clear(); CarPlayGlance.onExternalMusic(null, false); return }
        if (!info.lyrics.isNullOrBlank()) lyricCallbacks++
        CarlinkDashboardState.update(CarPlayNowPlaying(
            title = info.title, album = info.albumName, artist = info.artistName,
            lyrics = info.lyrics,
            durationMillis = info.totalTimesMs.takeIf { it > 0 },
            elapsedMillis = info.playingCurrentTimeMs.toLong().takeIf { it >= 0 },
            playing = info.isPlaying,
        ), info.cover)
        com.shilapi.xcertplay.CarPlayMediaKeys.notifyExternalDashboardChanged()
        CarPlayGlance.onExternalMusic(info.title, info.isPlaying)
        log("music received playing=${info.isPlaying} lyrics=${!info.lyrics.isNullOrBlank()}")
    }
    override fun onCallInfoReceived(phoneId: String?, info: UCarCommon.CallInfo?) = Unit
    override fun onPhoneStateInfoReceived(phoneId: String?, info: UCarCommon.PhoneStateInfo?) = Unit
    override fun onPOIReceived(phoneId: String?, poi: UCarCommon.POIAddress?) = Unit
    override fun onAudioFocusChanged(type: UCarCommon.AudioType?, focus: Int) = Unit
    override fun onAppListStateChanged(phoneId: String?, list: MutableList<*>?, state: UCarCommon.AppListState?) = Unit
    override fun onAppStateChanged(phoneId: String?, state: Int, flag: Boolean, a: Int, b: Int, appState: UCarCommon.AppState?) = Unit
    override fun getAudioAttributesByType(type: UCarCommon.AudioType?): UCarCommon.AudioAttributes? = null
    private fun setState(state: CarlinkLinkState, error: Int? = null) { _snapshot.value = _snapshot.value.copy(state = state, error = error, pin = if (state == CarlinkLinkState.PAIRING) _snapshot.value.pin else null) }
    @Synchronized private fun log(message: String) {
        DiagnosticLogging.record {
            val safe = message.replace(Regex("device=[^ ]*"), "device=<redacted>")
            if (firstFailure == null && ("failed" in safe || "state=3 " in safe)) firstFailure = safe
            if (trace.size >= 300) trace.removeFirst()
            trace.addLast("elapsedMs=${android.os.SystemClock.elapsedRealtime()} $safe")
            com.shilapi.xcertplay.CarPlayStartupDiagnostics.capture("Carlink $safe")
            Log.i("DiPlay-Carlink", safe)
        }
    }
    private fun carIdentity(): ByteArray {
        val prefs = context.getSharedPreferences("carlink_identity", Context.MODE_PRIVATE)
        val saved = prefs.getString("car_id", null)?.split(":")?.mapNotNull { it.toIntOrNull(16)?.toByte() }?.toByteArray()
        if (saved?.size == 6 && saved.any { it.toInt() != 0 } && saved.joinToString("") { "%02X".format(it) } != "020000000000") return saved
        val bytes = ByteArray(6).also(SecureRandom()::nextBytes)
        bytes[0] = ((bytes[0].toInt() and 0xfe) or 0x02).toByte()
        prefs.edit().putString("car_id", bytes.joinToString(":") { "%02X".format(it) }).apply()
        return bytes
    }
    companion object {
        fun stopIfStarted() { instance?.takeIf { it.active }?.stop() }
        @Volatile private var instance: CarlinkBridge? = null; fun get(context: Context): CarlinkBridge = instance ?: synchronized(this) { instance ?: CarlinkBridge(context.applicationContext).also { instance = it } } }
}
