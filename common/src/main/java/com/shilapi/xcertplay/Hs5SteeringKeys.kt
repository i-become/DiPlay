package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.hud.BydCarPlayCall
import com.shilapi.xcertplay.hud.BydNavigationOutputs
import com.shilapi.xcertplay.hud.CarPlayCallCard
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.media.CarPlayTelephonyAudio
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Hongqi / QiSi steering-wheel keys for CarPlay.
 *
 * The head unit routes previous, next, phone and voice keys through
 * [ltd.qisi.systemservice.QisiSystemService], not as foreground [KeyEvent]s, so
 * [CarPlayMediaKeys]' MediaSession never sees them while DiPlay plays CarPlay audio.
 * With [AirPlayPersistence.loadCarBluetoothAudio] on, AVRCP on the car Bluetooth link
 * already drives the iPhone; media keys are not claimed then. Custom voice routing is independent.
 * SystemService BACK is registered independently while a CarPlay projection is visible.
 */
internal object Hs5SteeringKeys {
    private const val TAG = "Xcert-Hs5-Steering"
    private const val HARD_KEY_ACTION_DOWN = QisiSteeringServiceClient.HARD_KEY_SHORT_CLICK
    private const val BIND_RETRY_MS = 5_000L
    private const val REFRESH_DELAY_MS = 250L
    private const val VIEW_FOCUS_COOLDOWN_MS = 2_000L
    private const val CALL_KEY_RETURN_DELAY_MS = 1_500L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "xcert-hs5-steering").apply { isDaemon = true }
    }
    @Volatile private var appContext: Context? = null
    @Volatile private var controller: CarPlayController? = null
    @Volatile private var controllerGeneration = 0L
    @Volatile private var hostRef = WeakReference<Activity>(null)
    private var hostGeneration = 0L

    private var serviceBinder: IBinder? = null
    private var connection: ServiceConnection? = null
    private var binding = false
    private var bindAttemptAt = 0L
    private var keysRegistered = false
    private var registeredKeyCodes: IntArray = intArrayOf()
    private var viewFocusHeld = false
    private var viewFocusType: Int? = null
    private var preferredViewFocusType = QisiSteeringServiceClient.VIEW_FOCUS_CARPLAY
    @Volatile private var viewFocusSuppressed = false
    private var viewFocusCooldownUntil = 0L
    private var phoneKeyHeld = false
    private var shuttingDown = false

    @Volatile private var hardKeyGeneration = 0L
    private var hardKeyListener: HardKeyListener? = null
    private var viewFocusListener = ViewFocusListener()
    private var systemCallback: SystemCallback? = null
    private var fawKeys: FawSteeringServiceClient? = null

    private val refreshRunnable = Runnable { worker.execute { refreshLocked() } }
    private val retryRunnable = Runnable { scheduleRefresh(force = true) }

    fun attach(context: Context, next: CarPlayController) {
        if (!supported(context)) return
        fawKeys?.close()
        appContext = context.applicationContext
        controller = next
        controllerGeneration++
        val generation = controllerGeneration
        var backTarget: CarPlayHostActivity? = null
        var backSession: Any? = null
        var backDownTime = 0L
        fawKeys = FawSteeringServiceClient(context.applicationContext) { event ->
            if (controller !== next || controllerGeneration != generation) return@FawSteeringServiceClient
            recordEvent { "FAW key code=${event.keyCode} action=${event.action} repeat=${event.repeatCount} canceled=${event.isCanceled} downTime=${event.downTime} eventTime=${event.eventTime}" }
            // This service observes keys without taking them away from the factory assistant.
            // Voice stays on the Qisi gesture route, avoiding duplicate taps from the two buses.
            if (event.keyCode != KeyEvent.KEYCODE_BACK) return@FawSteeringServiceClient
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                backTarget = CarPlayHostSettingsBridge.wheelBackTarget()
                backSession = next.activeAirPlaySessionToken()
                backDownTime = event.downTime
            }
            if (event.action != KeyEvent.ACTION_UP) return@FawSteeringServiceClient
            val target = backTarget
            val session = backSession
            backTarget = null
            backSession = null
            if (target == null || session == null || event.isCanceled || event.downTime != backDownTime) {
                recordEvent { "FAW Back ignored: missing matching press or canceled" }
                return@FawSteeringServiceClient
            }
            mainHandler.post {
                if (controller === next && controllerGeneration == generation && !next.isClosed() &&
                    next.activeAirPlaySessionToken() === session) {
                    val closed = CarPlayHostSettingsBridge.closeFromWheel(target, "FAW steering BACK")
                    recordEvent { "FAW Back projection closed=$closed" }
                }
            }
        }.also { it.start() }
        hostGeneration++
        VoiceAssistantKeyController.reset()
        VoiceAssistantKeyController.applySettings(context)
        shuttingDown = false
        BydCarPlayCall.callChanged = { card ->
            // A long press that ended the call releases after it; the key goes back once that is in.
            if (card != null) mainHandler.post { scheduleRefresh(force = true) }
            else mainHandler.postDelayed({ scheduleRefresh(force = true) }, CALL_KEY_RETURN_DELAY_MS)
        }
        CarPlayTelephonyAudio.changed = { active ->
            if (active) mainHandler.post { scheduleRefresh(force = true) }
            else mainHandler.postDelayed({ scheduleRefresh(force = true) }, CALL_KEY_RETURN_DELAY_MS)
        }
        scheduleRefresh(force = true)
    }

    fun detach(expected: CarPlayController?) {
        if (expected != null && controller !== expected) return
        fawKeys?.close()
        fawKeys = null
        controller = null
        controllerGeneration++
        hostGeneration++
        phoneKeyHeld = false
        hostRef = WeakReference(null)
        VoiceAssistantKeyController.reset()
        releaseAll()
    }

    fun hostResumed(host: Activity) {
        if (!supported(host)) return
        hostRef = WeakReference(host)
        viewFocusSuppressed = false
        hostGeneration++
        scheduleRefresh(force = true)
    }

    fun hostPaused(host: Activity) {
        if (hostRef.get() === host) {
            hostRef = WeakReference(null)
            hostGeneration++
        }
        scheduleRefresh(force = true)
    }

    fun applySettings(context: Context) {
        if (!supported(context)) return
        VoiceAssistantKeyController.applySettings(context)
        scheduleRefresh(force = true)
    }

    internal fun launchVoiceAssistant(target: VoiceAssistantKeyTarget) {
        val context = appContext ?: return
        val active = controller ?: return
        if (!sessionReady(active) || !AirPlayPersistence.loadCarPlayWheelSiri(context)) return
        recordEvent { "voice resolved=$target" }
        when (target) {
            VoiceAssistantKeyTarget.SIRI -> {
                val generation = controllerGeneration
                worker.execute {
                    synchronized(this@Hs5SteeringKeys) {
                        if (controller !== active || generation != controllerGeneration ||
                            !sessionReady(active) || !AirPlayPersistence.loadCarPlayWheelSiri(context)) return@synchronized
                        // Restore the OEM display route before asking the phone to show Siri.
                        viewFocusSuppressed = false
                        refreshLocked()
                        val queued = runCatching { active.requestSiri() }.getOrDefault(false)
                        recordEvent { "voice Siri request queued=$queued focusType=$viewFocusType" }
                    }
                }
            }
            VoiceAssistantKeyTarget.HONGQI -> {
                val generation = controllerGeneration
                worker.execute {
                    synchronized(this@Hs5SteeringKeys) {
                        if (controller !== active || generation != controllerGeneration ||
                            !sessionReady(active) || !AirPlayPersistence.loadCarPlayWheelSiri(context)) return@synchronized
                        viewFocusSuppressed = true
                        val binder = serviceBinder
                        if (binder == null) {
                            recordEvent { "voice Hongqi not submitted: service unavailable" }
                            return@synchronized
                        }
                        if (viewFocusHeld) releaseViewFocusLocked(binder)
                        runCatching { Hs5VehicleVoiceAssistant.wake(context, binder) }
                            .onSuccess { route -> recordEvent { "voice Hongqi $route" } }
                            .onFailure { error -> recordEvent { "voice Hongqi submission failed: ${error.javaClass.simpleName}: ${error.message}" } }
                    }
                }
            }
        }
    }

    internal inline fun recordEvent(crossinline message: () -> String) {
        if (!DiagnosticLogging.enabled) return
        DiagnosticLogging.write { Hs5SteeringDiagnostics.record(message()) }
    }

    @Synchronized
    internal fun diagnosticReport(context: Context): String {
        if (!DiagnosticLogging.enabled) return "Qisi steering recording disabled"
        return "Qisi steering: supported=${supported(context)} binding=$binding bound=${serviceBinder != null} " +
            "systemCallback=${systemCallback != null} keys=${registeredKeyCodes.contentToString()} " +
            "focusRequested=$viewFocusHeld focusType=$viewFocusType focusSuppressed=$viewFocusSuppressed host=${hostRef.get() != null} " +
            "flyScreen=${CarPlayHostSettingsBridge.flyScreenShown()} session=${sessionReady(controller)}\n" +
            "Voice routing: enabled=${AirPlayPersistence.loadCarPlayWheelSiri(context)} " +
            "target=SIRI immediate=true\n" +
            (fawKeys?.status() ?: "FAW steering: detached") + "\n" + Hs5SteeringDiagnostics.report()
    }

    internal fun supported(context: Context): Boolean = Hs5HeadUnit.isHeadUnit(context)

    private fun shouldClaimHardKeys(context: Context): Boolean {
        if (!Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context, CarPlayHostSettingsBridge.wheelBackTargetShown())) return false
        return sessionReady(controller)
    }

    private fun shouldHoldViewFocus(): Boolean {
        if (viewFocusSuppressed || (hostRef.get() == null && !CarPlayHostSettingsBridge.flyScreenShown())) return false
        return sessionReady(controller)
    }

    private fun sessionReady(active: CarPlayController?): Boolean {
        if (active == null || active.isClosed()) return false
        return active.activeAirPlaySessionToken() != null
    }

    /** QiSi compares key arrays by contents; call-key toggles only reorder the same set while probing. */
    private fun sameKeyRegistration(current: IntArray, desired: IntArray): Boolean =
        current.size == desired.size && current.toSet() == desired.toSet()

    private fun scheduleRefresh(force: Boolean) {
        mainHandler.removeCallbacks(refreshRunnable)
        if (force) mainHandler.post(refreshRunnable)
        else mainHandler.postDelayed(refreshRunnable, REFRESH_DELAY_MS)
    }

    private fun scheduleRetry(at: Long) {
        mainHandler.removeCallbacks(retryRunnable)
        mainHandler.postDelayed(retryRunnable, (at - SystemClock.uptimeMillis()).coerceAtLeast(1L))
    }

    @Synchronized
    private fun refreshLocked() {
        val context = appContext ?: return
        mainHandler.removeCallbacks(retryRunnable)
        if (shuttingDown) {
            tearDownServiceLocked(context)
            return
        }
        val claimKeys = shouldClaimHardKeys(context)
        val holdFocus = shouldHoldViewFocus()
        if (!claimKeys && !holdFocus) {
            tearDownServiceLocked(context)
            return
        }
        ensureServiceLocked(context)
        val binder = serviceBinder ?: return
        if (systemCallback == null) {
            val callback = SystemCallback(binder)
            runCatching { QisiSteeringServiceClient.registerSystemCallback(binder, context.packageName, callback) }
                .onSuccess {
                    systemCallback = callback
                    recordEvent { "system callback registered" }
                }
                .onFailure { error ->
                    recordEvent { "system callback registration failed: ${error.javaClass.simpleName}: ${error.message}" }
                    scheduleRetry(SystemClock.uptimeMillis() + BIND_RETRY_MS)
                    return
                }
        }
        val phoneCall = BydNavigationOutputs.carPlayCall() != null || CarPlayTelephonyAudio.active()
        val flyScreen = CarPlayHostSettingsBridge.wheelBackTargetShown()
        val desiredKeys = if (claimKeys) {
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, phoneCall, flyScreen)
        } else {
            intArrayOf()
        }
        if (claimKeys && keysRegistered && !sameKeyRegistration(registeredKeyCodes, desiredKeys)) {
            releaseHardKeysLocked(context, binder)
        }
        if (claimKeys && !keysRegistered && desiredKeys.isNotEmpty()) {
            val listener = HardKeyListener(++hardKeyGeneration)
            runCatching {
                QisiSteeringServiceClient.registerHardKeys(
                    binder,
                    context.packageName,
                    desiredKeys,
                    listener,
                )
                hardKeyListener = listener
                keysRegistered = true
                registeredKeyCodes = desiredKeys
                recordEvent { "hard keys registered=${desiredKeys.contentToString()} generation=$hardKeyGeneration" }
                DiagnosticLogging.logcat { Log.i(TAG, "temporary keys registered count=${desiredKeys.size}") }
            }.onFailure { error ->
                hardKeyGeneration++
                runCatching {
                    QisiSteeringServiceClient.unregisterHardKeys(binder, context.packageName, desiredKeys, listener)
                }
                DiagnosticLogging.logcat { Log.w(TAG, "steering hard-key register failed: ${error.javaClass.simpleName}") }
                recordEvent { "hard key registration failed: ${error.javaClass.simpleName}: ${error.message}" }
                bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
                scheduleRetry(bindAttemptAt)
            }
        } else if (!claimKeys && keysRegistered) {
            releaseHardKeysLocked(context, binder)
        }
        if (holdFocus && !viewFocusHeld && SystemClock.uptimeMillis() >= viewFocusCooldownUntil) {
            viewFocusCooldownUntil = SystemClock.uptimeMillis() + VIEW_FOCUS_COOLDOWN_MS
            val otherType = if (preferredViewFocusType == QisiSteeringServiceClient.VIEW_FOCUS_CARPLAY) {
                QisiSteeringServiceClient.VIEW_FOCUS_CARLINK
            } else QisiSteeringServiceClient.VIEW_FOCUS_CARPLAY
            for (type in intArrayOf(preferredViewFocusType, otherType)) {
                val result = runCatching {
                    QisiSteeringServiceClient.requestCarPlayViewFocus(binder, viewFocusListener, type)
                }.onFailure { error ->
                    recordEvent { "focus request type=$type failed: ${error.javaClass.simpleName}: ${error.message}" }
                }.getOrDefault(0)
                recordEvent { "focus request tx=5 type=$type result=$result" }
                if (result == 1 || result == 2) {
                    viewFocusHeld = true
                    viewFocusType = type
                    preferredViewFocusType = type
                    break
                }
            }
            if (!viewFocusHeld) scheduleRetry(viewFocusCooldownUntil)
        } else if (!holdFocus && viewFocusHeld) {
            releaseViewFocusLocked(binder)
        }
    }

    @Synchronized
    private fun ensureServiceLocked(context: Context) {
        if (serviceBinder != null) return
        if (binding) {
            if (SystemClock.uptimeMillis() < bindAttemptAt) {
                scheduleRetry(bindAttemptAt)
                return
            }
            // A successful bind request can still never deliver onServiceConnected during boot.
            tearDownServiceLocked(context)
        }
        if (SystemClock.uptimeMillis() < bindAttemptAt) {
            scheduleRetry(bindAttemptAt)
            return
        }
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                synchronized(this@Hs5SteeringKeys) {
                    if (connection !== this) return
                    binding = false
                    if (runCatching { service?.interfaceDescriptor }.getOrNull() != QisiSteeringServiceClient.TOKEN) {
                        DiagnosticLogging.logcat { Log.w(TAG, "QiSi key service unexpected binder") }
                        tearDownServiceLocked(context)
                        bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
                        scheduleRetry(bindAttemptAt)
                        return
                    }
                    serviceBinder = service
                    viewFocusListener = ViewFocusListener()
                    bindAttemptAt = 0L
                    recordEvent { "service connected descriptor=${service?.interfaceDescriptor}" }
                    DiagnosticLogging.logcat { Log.i(TAG, "QiSi steering service bound") }
                    scheduleRefresh(force = true)
                }
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                synchronized(this@Hs5SteeringKeys) {
                    if (connection !== this) return
                    recordEvent { "service disconnected" }
                    tearDownServiceLocked(context)
                    bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
                    scheduleRetry(bindAttemptAt)
                }
            }

            override fun onBindingDied(name: android.content.ComponentName?) = onServiceDisconnected(name)
            override fun onNullBinding(name: android.content.ComponentName?) = onServiceDisconnected(name)
        }
        connection = conn
        binding = true
        bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
        val accepted = runCatching {
            context.bindService(QisiSteeringServiceClient.serviceIntent, conn, Context.BIND_AUTO_CREATE)
        }.onFailure { error -> recordEvent { "service bind failed: ${error.javaClass.simpleName}: ${error.message}" } }.getOrDefault(false)
        recordEvent { "service bind accepted=$accepted" }
        if (!accepted) {
            binding = false
            connection = null
            DiagnosticLogging.logcat { Log.w(TAG, "QiSi steering service bind denied") }
        }
        if (serviceBinder == null) scheduleRetry(bindAttemptAt)
    }

    @Synchronized
    private fun releaseAll() {
        val context = appContext ?: return
        mainHandler.removeCallbacks(refreshRunnable)
        shuttingDown = true
        tearDownServiceLocked(context)
        shuttingDown = false
    }

    @Synchronized
    private fun tearDownServiceLocked(context: Context) {
        mainHandler.removeCallbacks(retryRunnable)
        hardKeyGeneration++
        val binder = serviceBinder
        if (binder != null) {
            if (viewFocusHeld) releaseViewFocusLocked(binder)
            if (keysRegistered) releaseHardKeysLocked(context, binder)
            systemCallback?.let { callback ->
                runCatching { QisiSteeringServiceClient.unregisterSystemCallback(binder, context.packageName, callback) }
                    .onFailure { error -> recordEvent { "system callback release failed: ${error.javaClass.simpleName}: ${error.message}" } }
            }
        }
        val previous = connection
        connection = null
        previous?.let { conn ->
            runCatching { context.unbindService(conn) }
                .onFailure { DiagnosticLogging.logcat { Log.w(TAG, "QiSi steering unbind failed: ${it.javaClass.simpleName}") } }
        }
        serviceBinder = null
        systemCallback = null
        binding = false
        bindAttemptAt = 0L
        keysRegistered = false
        registeredKeyCodes = intArrayOf()
        hardKeyListener = null
        viewFocusHeld = false
        viewFocusType = null
        preferredViewFocusType = QisiSteeringServiceClient.VIEW_FOCUS_CARPLAY
        viewFocusCooldownUntil = 0L
        // Invalidate callbacks already queued by the previous service connection.
        viewFocusListener = ViewFocusListener()
        viewFocusSuppressed = false
        phoneKeyHeld = false
    }

    private fun releaseHardKeysLocked(context: Context, binder: IBinder) {
        hardKeyGeneration++
        val keys = registeredKeyCodes
        val listener = hardKeyListener
        if (keys.isEmpty() || listener == null) {
            keysRegistered = false
            return
        }
        runCatching {
            QisiSteeringServiceClient.unregisterHardKeys(
                binder,
                context.packageName,
                keys,
                listener,
            )
            DiagnosticLogging.logcat { Log.i(TAG, "steering hard keys restored to OEM") }
            recordEvent { "hard keys unregistered=${keys.contentToString()}" }
        }.onFailure { error ->
            DiagnosticLogging.logcat { Log.w(TAG, "steering hard-key release failed: ${error.javaClass.simpleName}") }
            recordEvent { "hard key release failed: ${error.javaClass.simpleName}: ${error.message}" }
        }
        keysRegistered = false
        registeredKeyCodes = intArrayOf()
        hardKeyListener = null
        phoneKeyHeld = false
    }

    private fun releaseViewFocusLocked(binder: IBinder) {
        runCatching {
            val result = QisiSteeringServiceClient.releaseCarPlayViewFocus(binder, viewFocusListener)
            recordEvent { "focus abandon tx=6 result=$result" }
            DiagnosticLogging.logcat { Log.i(TAG, "CarPlay UI focus returned to vehicle") }
        }.onFailure { error ->
            DiagnosticLogging.logcat { Log.w(TAG, "CarPlay view focus release failed: ${error.javaClass.simpleName}") }
            recordEvent { "focus abandon failed: ${error.javaClass.simpleName}: ${error.message}" }
        }
        viewFocusHeld = false
        viewFocusType = null
        viewFocusCooldownUntil = 0L
    }

    private fun onHardKey(keyCode: Int, action: Int, receivedAt: Long, backTarget: CarPlayHostActivity?) {
        val context = appContext ?: return
        val active = controller
        if (active == null || !shouldClaimHardKeys(context)) {
            scheduleRefresh(force = true)
            return
        }
        if (keyCode in Hs5SteeringKeyPolicy.FLY_SCREEN_BACK_CODES) {
            if (Hs5SteeringKeyPolicy.closesFlyScreen(keyCode, action, CarPlayHostSettingsBridge.wheelBackTargetShown())) {
                val closed = backTarget != null && CarPlayHostSettingsBridge.closeFromWheel(backTarget, "Qisi BACK short click")
                recordEvent { "BACK fly screen close=$closed" }
            }
            return
        }
        // A previous registration may already have queued a media event before settings changed.
        if (keyCode == QisiSteeringServiceClient.KEYCODE_VOICE_ASSIST ||
            keyCode == QisiSteeringServiceClient.KEYCODE_SDS_CAR_LINK) {
            if (action == HARD_KEY_ACTION_DOWN && AirPlayPersistence.loadCarPlayWheelSiri(context)) {
                VoiceAssistantKeyController.onFactoryVoiceKeyDown(context, keyCode, receivedAt)
            }
            return
        }
        if (AirPlayPersistence.loadCarBluetoothAudio(context)) return
        if (keyCode == 110) {
            DiagnosticLogging.logcat { Log.i(TAG, "factory mode key action=$action consumed=true") }
            return
        }
        if (keyCode == KeyEvent.KEYCODE_CALL) {
            onPhoneKey(action, active)
            return
        }
        if (action != HARD_KEY_ACTION_DOWN) return
        val role = QisiSteeringServiceClient.carPlayActionForFactoryKey(keyCode) ?: return
        val sent = when (role) {
            0 -> if (AirPlayPersistence.loadCarPlayWheelMedia(context)) {
                CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.PREVIOUS, "hs5-steer")
            } else {
                false
            }
            1 -> if (AirPlayPersistence.loadCarPlayWheelMedia(context)) {
                CarPlayMediaKeys.onExternalMediaKey(CarPlayMediaButton.NEXT, "hs5-steer")
            } else {
                false
            }
            else -> false
        }
        DiagnosticLogging.logcat { Log.i(TAG, "factory key=$keyCode action=$action sent=$sent") }
    }

    /** QiSi phone key: action 0 is a short press, 1 a long press starting and 2 its release. */
    private fun onPhoneKey(action: Int, active: CarPlayController) {
        val call = BydNavigationOutputs.carPlayCall()
        val voip = CarPlayTelephonyAudio.active()
        val speechVoip = CarPlayTelephonyAudio.speechOnlyCallActive()
        val iapVoip = CarPlayTelephonyAudio.voipAcceptUsesIap()
        val weChatMix = CarPlayTelephonyAudio.speechRecognitionActive()
        val outcome = when (
            Hs5PhoneKeyPolicy.onPhoneKey(
                action,
                call?.phase,
                phoneKeyHeld,
                voip,
                iapVoip,
                weChatMix,
            )
        ) {
            Hs5PhoneKeyPolicy.Action.ANSWER -> "answer sent=${active.answerCall()}"
            Hs5PhoneKeyPolicy.Action.END -> "end sent=${active.endCall()}"
            Hs5PhoneKeyPolicy.Action.VOIP_ANSWER -> "voip answer sent=${active.answerCall()}"
            Hs5PhoneKeyPolicy.Action.VOIP_SPEECH_ANSWER -> {
                val iap = active.acceptCallViaIap()
                val hook = active.answerCall()
                "speech voip iap=$iap hook=$hook"
            }
            Hs5PhoneKeyPolicy.Action.VOIP_END -> {
                val iap = active.endCallViaIap()
                val hook = active.endCall()
                "speech voip end iap=$iap hook=$hook"
            }
            Hs5PhoneKeyPolicy.Action.NONE -> "none"
        }
        when (action) {
            1 -> phoneKeyHeld = true
            2 -> phoneKeyHeld = false
        }
        DiagnosticLogging.logcat { Log.i(TAG, "factory key=${KeyEvent.KEYCODE_CALL} action=$action call=${call?.phase} $outcome") }
    }

    internal object Hs5PhoneKeyPolicy {
        enum class Action { ANSWER, END, VOIP_ANSWER, VOIP_SPEECH_ANSWER, VOIP_END, NONE }

        /**
         * A short press answers a ringing call and ends a connected or outgoing one; a long press ends
         * or declines. WeChat VoIP uses speechrecognition audio without iAP2 call state: short press
         * sends iAP AcceptCall plus the telephony hook; play/pause is not used because it starts music.
         */
        fun onPhoneKey(
            action: Int,
            phase: CarPlayCallCard.Phase?,
            held: Boolean,
            voipAudio: Boolean,
            iapVoipAccept: Boolean,
            weChatSpeechMix: Boolean,
        ): Action =
            when (action) {
                0 -> when (phase) {
                    CarPlayCallCard.Phase.RINGING -> when {
                        weChatSpeechMix || iapVoipAccept -> Action.VOIP_SPEECH_ANSWER
                        else -> Action.ANSWER
                    }
                    CarPlayCallCard.Phase.ACTIVE, CarPlayCallCard.Phase.DIALING -> when {
                        weChatSpeechMix -> Action.VOIP_SPEECH_ANSWER
                        else -> Action.END
                    }
                    null -> when {
                        !voipAudio -> Action.NONE
                        iapVoipAccept || voipAudio -> Action.VOIP_SPEECH_ANSWER
                        else -> Action.VOIP_ANSWER
                    }
                }
                1 -> when {
                    !held && phase != null && !weChatSpeechMix -> Action.END
                    (iapVoipAccept || weChatSpeechMix) && voipAudio -> Action.VOIP_END
                    else -> Action.NONE
                }
                else -> Action.NONE
            }
    }

    private class HardKeyListener(private val generation: Long) : Binder() {
        init { attachInterface(null, "ltd.qisi.systemmanager.IHardKeyListener") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString("ltd.qisi.systemmanager.IHardKeyListener")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ltd.qisi.systemmanager.IHardKeyListener")
            val keyCode = data.readInt()
            val action = data.readInt()
            val receivedAt = SystemClock.uptimeMillis()
            val backTarget = if (keyCode == QisiSteeringServiceClient.KEYCODE_BACK) {
                CarPlayHostSettingsBridge.wheelBackTarget()
            } else null
            val backSession = if (backTarget != null) controller?.activeAirPlaySessionToken() else null
            mainHandler.post {
                recordEvent { "hard key raw code=$keyCode action=$action listener=$generation current=$hardKeyGeneration" }
                if (generation == hardKeyGeneration && (backTarget == null ||
                        controller?.activeAirPlaySessionToken() === backSession)) {
                    onHardKey(keyCode, action, receivedAt, backTarget)
                }
            }
            reply?.writeNoException()
            return true
        }
    }

    private class ViewFocusListener : Binder() {
        init { attachInterface(null, "ltd.qisi.systemmanager.IViewFocusChangedListener") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString("ltd.qisi.systemmanager.IViewFocusChangedListener")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ltd.qisi.systemmanager.IViewFocusChangedListener")
            val type = data.readInt()
            val state = data.readInt()
            mainHandler.post {
                synchronized(this@Hs5SteeringKeys) {
                    if (viewFocusListener !== this@ViewFocusListener) return@synchronized
                    recordEvent { "focus callback type=$type state=$state" }
                    if (type == viewFocusType && viewFocusHeld) {
                        // Respect a higher-priority OEM screen. Host resume or a Siri press may request again.
                        if (state < 0) {
                            viewFocusSuppressed = true
                            scheduleRefresh(force = true)
                        }
                    }
                }
            }
            DiagnosticLogging.logcat { Log.i(TAG, "CarPlay view focus change=$state") }
            reply?.writeNoException()
            return true
        }
    }

    private class SystemCallback(private val source: IBinder) : Binder() {
        init { attachInterface(null, "ltd.qisi.systemmanager.ISystemServiceCallback") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString("ltd.qisi.systemmanager.ISystemServiceCallback")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ltd.qisi.systemmanager.ISystemServiceCallback")
            val command = data.readString()
            recordEvent {
                // Parse only with diagnostic recording enabled. Never include SDS speech content.
                val envelope = runCatching { JSONObject(command ?: "") }.getOrNull()
                val route = if (envelope == null) "invalid envelope" else
                    listOf("SenderGroup", "SenderDomain", "TargetGroup", "TargetDomain", "SessionID", "CommandType", "ErrorCode")
                        .joinToString(" ") { "$it=${envelope.optString(it).take(96)}" }
                "system callback current=${serviceBinder === source} $route"
            }
            return true
        }
    }
}
