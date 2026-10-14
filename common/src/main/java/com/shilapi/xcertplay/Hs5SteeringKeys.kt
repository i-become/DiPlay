package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent
import com.shilapi.xcertplay.airplay.CarPlayMediaButton
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/**
 * Hongqi / QiSi steering-wheel keys for CarPlay.
 *
 * The head unit routes previous, next, phone and voice keys through
 * [ltd.qisi.systemservice.QisiSystemService], not as foreground [KeyEvent]s, so
 * [CarPlayMediaKeys]' MediaSession never sees them while DiPlay plays CarPlay audio.
 * With [AirPlayPersistence.loadCarBluetoothAudio] on, AVRCP on the car Bluetooth link
 * already drives the iPhone; factory hard keys are not claimed then (same idea as
 * [GeelyWheelKeys]).
 */
internal object Hs5SteeringKeys {
    private const val TAG = "Xcert-Hs5-Steering"
    private const val HARD_KEY_ACTION_DOWN = 0
    private const val BIND_RETRY_MS = 5_000L
    private const val REFRESH_DELAY_MS = 250L
    private const val VIEW_FOCUS_COOLDOWN_MS = 2_000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { task ->
        Thread(task, "xcert-hs5-steering").apply { isDaemon = true }
    }
    private var appContext: Context? = null
    private var controller: CarPlayController? = null
    private var controllerGeneration = 0L
    private var hostRef = WeakReference<Activity>(null)
    private var hostGeneration = 0L

    private var serviceBinder: IBinder? = null
    private var connection: ServiceConnection? = null
    private var binding = false
    private var bindAttemptAt = 0L
    private var keysRegistered = false
    private var registeredKeyCodes: IntArray = intArrayOf()
    private var viewFocusHeld = false
    private var viewFocusCooldownUntil = 0L
    private var phoneKeyHeld = false
    private var shuttingDown = false

    private val hardKeyListener = HardKeyListener()
    private val viewFocusListener = ViewFocusListener()

    private val refreshRunnable = Runnable { worker.execute { refreshLocked() } }

    fun attach(context: Context, next: CarPlayController) {
        if (!supported(context)) return
        appContext = context.applicationContext
        controller = next
        controllerGeneration++
        hostGeneration++
        shuttingDown = false
        scheduleRefresh(force = true)
    }

    fun detach(expected: CarPlayController?) {
        if (expected != null && controller !== expected) return
        controller = null
        controllerGeneration++
        hostGeneration++
        phoneKeyHeld = false
        releaseAll()
    }

    fun hostResumed(host: Activity) {
        if (!supported(host)) return
        hostRef = WeakReference(host)
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
        scheduleRefresh(force = true)
    }

    internal fun supported(context: Context): Boolean = Hs5HeadUnit.isHeadUnit(context)

    private fun shouldClaimHardKeys(context: Context): Boolean {
        if (!Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context)) return false
        if (AirPlayPersistence.loadCarBluetoothAudio(context)) return false
        return sessionReady(controller)
    }

    private fun shouldHoldViewFocus(): Boolean {
        if (hostRef.get() == null) return false
        return sessionReady(controller)
    }

    private fun sessionReady(active: CarPlayController?): Boolean {
        if (active == null || active.isClosed()) return false
        return active.activeAirPlaySessionToken() != null
    }

    private fun scheduleRefresh(force: Boolean) {
        mainHandler.removeCallbacks(refreshRunnable)
        if (force) mainHandler.post(refreshRunnable)
        else mainHandler.postDelayed(refreshRunnable, REFRESH_DELAY_MS)
    }

    @Synchronized
    private fun refreshLocked() {
        val context = appContext ?: return
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
        val desiredKeys = if (claimKeys) Hs5SteeringKeyPolicy.factoryKeyCodes(context) else intArrayOf()
        if (claimKeys && keysRegistered && !registeredKeyCodes.contentEquals(desiredKeys)) {
            releaseHardKeysLocked(context, binder)
        }
        if (claimKeys && !keysRegistered && desiredKeys.isNotEmpty()) {
            runCatching {
                QisiSteeringServiceClient.registerHardKeys(
                    binder,
                    context.packageName,
                    desiredKeys,
                    hardKeyListener,
                )
                keysRegistered = true
                registeredKeyCodes = desiredKeys
                Log.i(TAG, "temporary keys registered count=${desiredKeys.size}")
            }.onFailure { error ->
                Log.w(TAG, "steering hard-key register failed: ${error.javaClass.simpleName}")
                bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
            }
        } else if (!claimKeys && keysRegistered) {
            releaseHardKeysLocked(context, binder)
        }
        if (holdFocus && !viewFocusHeld && SystemClock.uptimeMillis() >= viewFocusCooldownUntil) {
            viewFocusCooldownUntil = SystemClock.uptimeMillis() + VIEW_FOCUS_COOLDOWN_MS
            runCatching {
                val result = QisiSteeringServiceClient.requestCarPlayViewFocus(binder, viewFocusListener)
                viewFocusHeld = result == 1 || result == 2
                Log.i(TAG, "CarPlay view focus request=$result")
            }.onFailure { error ->
                Log.w(TAG, "CarPlay view focus unavailable: ${error.javaClass.simpleName}")
            }
        } else if (!holdFocus && viewFocusHeld) {
            releaseViewFocusLocked(binder)
        }
    }

    @Synchronized
    private fun ensureServiceLocked(context: Context) {
        if (serviceBinder != null) return
        if (binding) {
            if (SystemClock.uptimeMillis() < bindAttemptAt) return
            return
        }
        if (SystemClock.uptimeMillis() < bindAttemptAt) return
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: android.content.ComponentName?, service: IBinder?) {
                synchronized(this@Hs5SteeringKeys) {
                    binding = false
                    if (service?.interfaceDescriptor != QisiSteeringServiceClient.TOKEN) {
                        Log.w(TAG, "QiSi key service unexpected binder")
                        bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
                        runCatching { context.unbindService(this) }
                        return
                    }
                    serviceBinder = service
                    Log.i(TAG, "QiSi steering service bound")
                    scheduleRefresh(force = true)
                }
            }

            override fun onServiceDisconnected(name: android.content.ComponentName?) {
                synchronized(this@Hs5SteeringKeys) {
                    serviceBinder = null
                    keysRegistered = false
                    viewFocusHeld = false
                    binding = false
                    bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
                }
            }

            override fun onBindingDied(name: android.content.ComponentName?) = onServiceDisconnected(name)
            override fun onNullBinding(name: android.content.ComponentName?) = onServiceDisconnected(name)
        }
        connection = conn
        binding = runCatching {
            context.bindService(QisiSteeringServiceClient.serviceIntent, conn, Context.BIND_AUTO_CREATE)
        }.getOrDefault(false)
        if (!binding) {
            connection = null
            bindAttemptAt = SystemClock.uptimeMillis() + BIND_RETRY_MS
            Log.w(TAG, "QiSi steering service bind denied")
        }
    }

    @Synchronized
    private fun releaseAll() {
        val context = appContext ?: return
        shuttingDown = true
        tearDownServiceLocked(context)
        shuttingDown = false
    }

    @Synchronized
    private fun tearDownServiceLocked(context: Context) {
        val binder = serviceBinder
        if (binder != null) {
            if (viewFocusHeld) releaseViewFocusLocked(binder)
            if (keysRegistered) releaseHardKeysLocked(context, binder)
        }
        connection?.let { conn ->
            runCatching { context.unbindService(conn) }
                .onFailure { Log.w(TAG, "QiSi steering unbind failed: ${it.javaClass.simpleName}") }
        }
        connection = null
        serviceBinder = null
        binding = false
        keysRegistered = false
        registeredKeyCodes = intArrayOf()
        viewFocusHeld = false
        phoneKeyHeld = false
    }

    private fun releaseHardKeysLocked(context: Context, binder: IBinder) {
        val keys = registeredKeyCodes
        if (keys.isEmpty()) {
            keysRegistered = false
            return
        }
        runCatching {
            QisiSteeringServiceClient.unregisterHardKeys(
                binder,
                context.packageName,
                keys,
                hardKeyListener,
            )
            Log.i(TAG, "steering hard keys restored to OEM")
        }.onFailure { error ->
            Log.w(TAG, "steering hard-key release failed: ${error.javaClass.simpleName}")
        }
        keysRegistered = false
        registeredKeyCodes = intArrayOf()
        phoneKeyHeld = false
    }

    private fun releaseViewFocusLocked(binder: IBinder) {
        runCatching {
            QisiSteeringServiceClient.releaseCarPlayViewFocus(binder)
            Log.i(TAG, "CarPlay UI focus returned to vehicle")
        }.onFailure { error ->
            Log.w(TAG, "CarPlay view focus release failed: ${error.javaClass.simpleName}")
        }
        viewFocusHeld = false
        viewFocusCooldownUntil = 0L
    }

    private fun onHardKey(keyCode: Int, action: Int) {
        val context = appContext ?: return
        val active = controller
        if (active == null || !shouldClaimHardKeys(context)) {
            scheduleRefresh(force = true)
            return
        }
        if (keyCode == 110) {
            Log.i(TAG, "factory mode key action=$action consumed=true")
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
            3 -> if (AirPlayPersistence.loadCarPlayWheelSiri(context)) {
                CarPlayMediaKeys.onExternalSiriKey()
            } else {
                false
            }
            else -> false
        }
        Log.i(TAG, "factory key=$keyCode action=$action sent=$sent")
    }

    private fun onPhoneKey(action: Int, active: CarPlayController) {
        when (action) {
            2 -> phoneKeyHeld = false
            1 -> if (!phoneKeyHeld) {
                phoneKeyHeld = true
                val sent = active.endCall()
                Log.i(TAG, "factory key=${KeyEvent.KEYCODE_CALL} action=$action phone=end_or_reject sent=$sent")
            }
        }
    }

    private class HardKeyListener : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString("ltd.qisi.systemmanager.IHardKeyListener")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ltd.qisi.systemmanager.IHardKeyListener")
            val keyCode = data.readInt()
            val action = data.readInt()
            mainHandler.post { onHardKey(keyCode, action) }
            reply?.writeNoException()
            return true
        }
    }

    private class ViewFocusListener : Binder() {
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString("ltd.qisi.systemmanager.IViewFocusChangedListener")
                return true
            }
            if (code != 1) return super.onTransact(code, data, reply, flags)
            data.enforceInterface("ltd.qisi.systemmanager.IViewFocusChangedListener")
            val type = data.readInt()
            val state = data.readInt()
            if (state == -1 && type == QisiSteeringServiceClient.VIEW_FOCUS_CARPLAY) {
                scheduleRefresh(force = true)
            }
            Log.i(TAG, "CarPlay view focus change=$state")
            reply?.writeNoException()
            return true
        }
    }
}
