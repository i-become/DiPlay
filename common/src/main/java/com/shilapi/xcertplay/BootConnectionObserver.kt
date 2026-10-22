package com.shilapi.xcertplay

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.provider.Settings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Passive observations only: no enable/connect, discovery, group changes or network probes. */
@Suppress("DEPRECATION")
internal class BootConnectionObserver(
    private val context: Context,
    private val log: (String) -> Unit,
    private val durationMillis: Long = 180_000,
) : java.io.Closeable {
    private val stopped = AtomicBoolean(false)
    private val receiverLock = Any()
    private var registered = false
    private val started = SystemClock.elapsedRealtime()
    private val receiverThread = HandlerThread("diplay-radio-events").apply { isDaemon = true }
    private val mainHandler = Handler(Looper.getMainLooper())
    private val mainPendingAt = AtomicLong(0)
    @Volatile private var mainDelayMillis = -1L
    @Volatile private var sampleCompletedAt = started
    @Volatile private var operation = "not-started"
    @Volatile private var operationStartedAt = started
    private val radioThread = Thread(::observeRadios, "diplay-radio-observer").apply { isDaemon = true }
    private val healthThread = Thread(::watchHealth, "diplay-startup-health").apply { isDaemon = true }
    private val mainCheck = Runnable {
        val posted = mainPendingAt.getAndSet(0)
        if (posted != 0L) mainDelayMillis = SystemClock.elapsedRealtime() - posted
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (stopped.get() || !com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
            val action = intent.action ?: return
            val details = when (action) {
                BluetoothAdapter.ACTION_STATE_CHANGED ->
                    "previous=${intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, -1)} " +
                        "state=${intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1)}"
                BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED ->
                    "previous=${intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_CONNECTION_STATE, -1)} " +
                        "state=${intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, -1)}"
                BluetoothDevice.ACTION_BOND_STATE_CHANGED ->
                    "previous=${intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1)} " +
                        "state=${intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)}"
                WifiManager.WIFI_STATE_CHANGED_ACTION ->
                    "previous=${intent.getIntExtra(WifiManager.EXTRA_PREVIOUS_WIFI_STATE, -1)} " +
                        "state=${intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE, -1)}"
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION ->
                    "state=${intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)}"
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> runCatching {
                    val info = intent.getParcelableExtra<WifiP2pInfo>(WifiP2pManager.EXTRA_WIFI_P2P_INFO)
                    val group = intent.getParcelableExtra<WifiP2pGroup>(WifiP2pManager.EXTRA_WIFI_P2P_GROUP)
                    "formed=${info?.groupFormed} owner=${info?.isGroupOwner} clients=${group?.clientList?.size} " +
                        "frequencyMHz=${if (Build.VERSION.SDK_INT >= 29) group?.frequency else "unavailable"}"
                }.getOrElse { "detailsUnavailable=${it.javaClass.simpleName}" }
                else -> "received=true"
            }
            val savedMatch = if (action.startsWith("android.bluetooth.device.")) runCatching {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val saved = DiPlayPreferences.phoneAddress(this@BootConnectionObserver.context)
                " savedTarget=${saved != null && device?.address.equals(saved, ignoreCase = true)}"
            }.getOrDefault(" savedTarget=unavailable") else ""
            emit("radio-broadcast action=$action $details$savedMatch")
        }
    }

    fun start() {
        if (stopped.get() || !com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        receiverThread.start()
        radioThread.start()
        healthThread.start()
    }

    private fun observeRadios() {
        try {
            runCatching { operation("register-broadcasts") {
                val filter = IntentFilter().apply {
                    addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
                    addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
                    addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED)
                    addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
                    addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
                    addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                    addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                }
                synchronized(receiverLock) {
                    if (!stopped.get()) {
                        if (Build.VERSION.SDK_INT >= 33) {
                            context.registerReceiver(receiver, filter, null, Handler(receiverThread.looper), Context.RECEIVER_EXPORTED)
                        } else context.registerReceiver(receiver, filter, null, Handler(receiverThread.looper))
                        registered = true
                        emit("radio-observer broadcasts=registered registerAfterMs=${SystemClock.elapsedRealtime() - started}")
                    }
                }
            } }.onFailure { emit("radio-observer broadcasts=unavailable failureClass=${it.javaClass.simpleName}") }
            emit(runCatching { operation("initial-config") {
                "startup-config bootCount=${Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1)} " +
                    "rom=${Build.DISPLAY.take(120)} savedTargetPresent=${!DiPlayPreferences.phoneAddress(context).isNullOrBlank()} " +
                    "autoConnect=${DiPlayPreferences.autoConnect(context)} " +
                    "autoWireless=${DiPlayPreferences.autoConnectWireless(context)} " +
                    "startWithCar=${AirPlayPersistence.loadAutoStartOnBoot(context)}"
            } }.getOrElse { "startup-config unavailable failureClass=${it.javaClass.simpleName}" })
            var previous = ""
            var lastLogAt = 0L
            var lastNetworkAt = 0L
            while (!stopped.get() && SystemClock.elapsedRealtime() - started < durationMillis) {
                val began = SystemClock.elapsedRealtime()
                val snapshot = operation("bluetooth-state-reads") { bluetoothSnapshot() }
                if (snapshot != previous || began - lastLogAt >= 1000) {
                    emit("radio-sample $snapshot readDurationMs=${SystemClock.elapsedRealtime() - began}")
                    previous = snapshot
                    lastLogAt = began
                }
                if (began - lastNetworkAt >= 1000) {
                    emit(operation("wifi-permission-reads") { networkSnapshot() })
                    lastNetworkAt = began
                }
                sampleCompletedAt = SystemClock.elapsedRealtime()
                operation = "sleep"
                Thread.sleep(250)
            }
        } catch (_: InterruptedException) {
            // The independent health observer owns the deadline and unregisters broadcasts.
        } catch (error: Exception) {
            emit("radio-observer polling=ended failureClass=${error.javaClass.simpleName}")
        } finally {
            operation = "ended"
        }
    }

    private fun bluetoothSnapshot(): String {
        val cached = runCatching { BluetoothAdapter.getDefaultAdapter() }
        val fresh = runCatching { context.getSystemService(BluetoothManager::class.java)?.adapter }
        fun describe(result: Result<BluetoothAdapter?>): String {
            val adapter = result.getOrNull() ?: return "available=false failureClass=${result.exceptionOrNull()?.javaClass?.simpleName ?: "none"}"
            fun read(action: () -> Any): String = runCatching { action().toString() }
                .getOrElse { "unavailable_${it.javaClass.simpleName}" }
            return "adapterId=${System.identityHashCode(adapter)} state=${read { adapter.state }} enabled=${read { adapter.isEnabled }} " +
                "discovering=${read { adapter.isDiscovering }}"
        }
        return "default=[${describe(cached)}] manager=[${describe(fresh)}] " +
            "sameAdapter=${cached.getOrNull() != null && cached.getOrNull() === fresh.getOrNull()}"
    }

    private fun networkSnapshot(): String {
        fun permitted(permission: String): Boolean = context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        val wifi = runCatching { context.getSystemService(WifiManager::class.java)?.wifiState }
            .fold({ it?.toString() ?: "no-service" }, { "unavailable_${it.javaClass.simpleName}" })
        val active = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            val caps = manager?.activeNetwork?.let { manager.getNetworkCapabilities(it) }
            "wifi=${caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)} " +
                "validated=${caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}"
        }.getOrElse { "unavailable_${it.javaClass.simpleName}" }
        return "system-network wifiState=$wifi active=[$active] " +
            "permissions connect=${if (Build.VERSION.SDK_INT >= 31) permitted(Manifest.permission.BLUETOOTH_CONNECT) else "legacy"} " +
            "scan=${if (Build.VERSION.SDK_INT >= 31) permitted(Manifest.permission.BLUETOOTH_SCAN) else "legacy"} " +
            "location=${permitted(Manifest.permission.ACCESS_FINE_LOCATION)} " +
            "nearbyWifi=${if (Build.VERSION.SDK_INT >= 33) permitted(Manifest.permission.NEARBY_WIFI_DEVICES) else "legacy"}"
    }

    private fun watchHealth() {
        var previousElapsed = started
        var previousUptime = SystemClock.uptimeMillis()
        var lastStackAt = 0L
        try {
            while (!stopped.get() && SystemClock.elapsedRealtime() - started < durationMillis) {
                val now = SystemClock.elapsedRealtime()
                val uptime = SystemClock.uptimeMillis()
                if (mainPendingAt.compareAndSet(0, now)) mainHandler.post(mainCheck)
                val pendingAge = mainPendingAt.get().let { if (it == 0L) 0 else now - it }
                val readAge = if (operation == "sleep" || operation == "ended") 0 else now - operationStartedAt
                emit("startup-health cpuMs=${Process.getElapsedCpuTime()} mainLastDelayMs=$mainDelayMillis " +
                    "mainPendingMs=$pendingAge radioOperation=$operation radioOperationMs=$readAge " +
                    "lastSampleAgeMs=${now - sampleCompletedAt} " +
                    "tickGapMs=${now - previousElapsed} suspendDeltaMs=${(now - previousElapsed) - (uptime - previousUptime)}")
                if ((pendingAge >= 2000 || readAge >= 2000) && now - lastStackAt >= 10_000) {
                    if (pendingAge >= 2000) emitStack("main", Looper.getMainLooper().thread)
                    if (readAge >= 2000) emitStack("radio", radioThread)
                    lastStackAt = now
                }
                previousElapsed = now
                previousUptime = uptime
                Thread.sleep(1000)
            }
        } catch (_: InterruptedException) {
            emit("startup-health interrupted=true")
        } finally {
            close()
        }
    }

    override fun close() {
        if (!stopped.compareAndSet(false, true)) return
        radioThread.interrupt()
        healthThread.interrupt()
        mainHandler.removeCallbacks(mainCheck)
        if (receiverThread.state == Thread.State.NEW) return
        // Unregister on its own thread, so a blocked framework call cannot hide the deadline.
        Handler(receiverThread.looper).post {
            synchronized(receiverLock) {
                if (registered) runCatching { context.unregisterReceiver(receiver) }
                    .onFailure { emit("radio-observer unregisterFailure=${it.javaClass.simpleName}") }
                registered = false
            }
            receiverThread.quitSafely()
        }
        emit("radio-observer observation=ended elapsedMs=${SystemClock.elapsedRealtime() - started} " +
            "lastSampleAgeMs=${SystemClock.elapsedRealtime() - sampleCompletedAt}")
    }

    private fun emitStack(label: String, thread: Thread) {
        thread.stackTrace.take(8).forEachIndexed { index, frame ->
            emit("startup-blocked thread=$label frame=$index at=$frame")
        }
    }

    private fun <T> operation(label: String, block: () -> T): T {
        operationStartedAt = SystemClock.elapsedRealtime()
        operation = label
        return block()
    }

    private fun emit(message: String) {
        if (!stopped.get() && com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) runCatching { log(message) }
    }
}
