package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.bluetooth.BluetoothAdapter
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.orchestration.BluetoothBootPreconnect
import com.shilapi.xcertplay.orchestration.cancelBluetoothDiscoveryForConnection
import com.shilapi.xcertplay.network.WirelessStartupPolicy
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Dial the saved paired phone while MFi, Wi-Fi and the UI are still starting. */
internal object Hs5BootBluetoothWarmup {
    private const val TAG = "DiPlay-BtWarmup"
    private val started = AtomicBoolean(false)

    fun scheduleIfNeeded(context: Context) {
        val app = context.applicationContext
        if (AirPlayPersistence.loadClusterOnlyMode(app)) return
        if (HongqiScreenStatus.screenOff || ConnectionProgressUiState.disconnected()) return
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        if (!DiPlayPreferences.autoConnectWireless(app)) { record("skip=wireless_disabled"); return }
        if (!DiPlayPreferences.autoConnect(app)) { record("skip=auto_connect_disabled"); return }
        if (CarPlayBackgroundSession.hasSession()) { record("skip=session_exists"); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) { record("skip=connect_permission_missing"); return }
        if (!started.compareAndSet(false, true)) { record("skip=already_scheduled"); return }
        val address = DiPlayPreferences.phoneAddress(app)
        if (!address.isNullOrBlank()) {
            CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.BLUETOOTH_PRECONNECT_STARTED)
            BluetoothBootPreconnect.schedule(address, prepareSocket = {
                if (AirPlayPersistence.loadClusterOnlyMode(app)) throw IOException("Instrument-only mode")
                val adapter = BluetoothAdapter.getDefaultAdapter()
                    ?: throw IOException("Bluetooth adapter is unavailable")
                val allowBootWait = SystemClock.elapsedRealtime() <
                    WirelessStartupPolicy.bootGraceMillis(app)
                val radioWaitMillis = WirelessStartupPolicy.bluetoothRadioReadyMillis(app, allowBootWait)
                record("stage=radio-wait state=${adapter.state} timeoutMs=$radioWaitMillis")
                requestEnable(adapter)
                val deadline = System.nanoTime() + radioWaitMillis * 1_000_000L
                var lastState = adapter.state
                while (!adapter.isEnabled && System.nanoTime() < deadline) {
                    if (AirPlayPersistence.loadClusterOnlyMode(app)) throw IOException("Instrument-only mode")
                    if (Thread.currentThread().isInterrupted) throw InterruptedException("Bluetooth boot cancelled")
                    val state = adapter.state
                    if (state != lastState) {
                        record("stage=radio-wait state=$state")
                        lastState = state
                    }
                    Thread.sleep(100)
                }
                if (!adapter.isEnabled) {
                    record("stage=radio-wait result=timeout state=${adapter.state}")
                    throw IOException("Bluetooth radio did not become ready at boot")
                }
                record("stage=radio-wait result=ready state=${adapter.state}")
                CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.BLUETOOTH_RADIO_READY)
                // The controller owns suspend-lease recovery. A second recovery here can
                // invalidate its handshake ticket while the two boot paths run in parallel.
                record("stage=saved-phone-lookup")
                val device = adapter.bondedDevices.orEmpty().firstOrNull {
                    it.address.equals(address, ignoreCase = true)
                } ?: run {
                    record("stage=saved-phone-lookup result=not-paired")
                    throw IOException("Saved phone is no longer paired")
                }
                record("stage=discovery-cancel")
                cancelBluetoothDiscoveryForConnection(app, adapter)
                record("stage=socket-create")
                if (AirPlayPersistence.loadClusterOnlyMode(app)) throw IOException("Instrument-only mode")
                device.createRfcommSocketToServiceRecord(UUID.fromString(BluetoothBootPreconnect.IAP2_SERVICE_UUID))
            }, log = { event ->
                record(event)
                if (event.startsWith("connected ")) {
                    CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.BLUETOOTH_CONNECTED)
                } else if (event.startsWith("failed ")) {
                    CarPlayStartupDiagnostics.mark(CarPlayStartupDiagnostics.Phase.BLUETOOTH_PRECONNECT_FAILED)
                }
            })
            return
        }
        record("savedTargetPresent=false action=radio_warmup_only")
        Thread({
            runCatching { BluetoothAdapter.getDefaultAdapter()?.let(::requestEnable) }
                .onFailure { DiagnosticLogging.logcat { Log.w(TAG, "Bluetooth warm-up failed: ${it.javaClass.simpleName}") } }
        }, "diplay-bt-warmup").apply { isDaemon = true }.start()
    }

    private fun requestEnable(adapter: BluetoothAdapter) {
        if (adapter.isEnabled) return
        val began = SystemClock.elapsedRealtime()
        record("stage=radio-enable call=begin state=${adapter.state}")
        @Suppress("DEPRECATION")
        val started = adapter.enable()
        record("stage=radio-enable requested=true accepted=$started state=${adapter.state} " +
            "callDurationMs=${SystemClock.elapsedRealtime() - began}")
    }

    private fun record(event: String) {
        DiagnosticLogging.logcat { Log.i(TAG, "boot Bluetooth $event") }
        DiagnosticLogging.record { CarPlayStartupDiagnostics.event("boot-bt $event") }
    }
}
