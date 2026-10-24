package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Handler
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.util.concurrent.atomic.AtomicBoolean

/** Releases only the current iPhone's Bluetooth music route after wireless CarPlay takes over. */
internal class BluetoothAudioHandoff(
    private val target: BluetoothDevice,
    private val handler: Handler,
    private val isCurrent: () -> Boolean,
    private val requestProxy: (BluetoothProfile.ServiceListener) -> Boolean,
    private val releaseProxy: (BluetoothProfile) -> Unit,
    private val diagnostic: (String) -> Unit,
    private val disconnect: (BluetoothProfile, BluetoothDevice) -> Boolean = { proxy, device ->
        // A head unit receives A2DP: BluetoothA2dp (profile 2) is the opposite role.
        proxy.javaClass.getMethod("disconnect", BluetoothDevice::class.java).invoke(proxy, device) == true
    },
) : AutoCloseable {
    private val started = AtomicBoolean(false)
    private val finished = AtomicBoolean(false)
    private val timeout = Runnable {
        if (finished.compareAndSet(false, true) && isCurrent()) {
            log { "CarPlay audio handoff: Bluetooth music profile unavailable (timeout)" }
        }
    }
    private val listener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            handler.post {
                try {
                    if (!finished.compareAndSet(false, true) || !isCurrent()) return@post
                    if (profile != A2DP_SINK) return@post
                    if (target !in proxy.connectedDevices.orEmpty()) {
                        log { "CarPlay audio handoff: current iPhone has no Bluetooth music connection" }
                        return@post
                    }
                    val accepted = disconnect(proxy, target)
                    log { "CarPlay audio handoff: Bluetooth music disconnect requested=$accepted" }
                } catch (error: Exception) {
                    val cause = error.cause ?: error
                    log { "CarPlay audio handoff unavailable: ${cause.javaClass.simpleName}" }
                } finally {
                    handler.removeCallbacks(timeout)
                    // A proxy arriving after cancellation or timeout still belongs to this request.
                    runCatching { releaseProxy(proxy) }
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            close()
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return
        handler.post {
            if (finished.get() || !isCurrent()) return@post
            try {
                if (requestProxy(listener)) handler.postDelayed(timeout, PROFILE_TIMEOUT_MILLIS)
                else {
                    finished.set(true)
                    log { "CarPlay audio handoff: Bluetooth music profile unsupported" }
                }
            } catch (error: Exception) {
                finished.set(true)
                log { "CarPlay audio handoff unavailable: ${error.javaClass.simpleName}" }
            }
        }
    }

    override fun close() {
        finished.set(true)
        handler.removeCallbacks(timeout)
    }

    private inline fun log(message: () -> String) {
        DiagnosticLogging.record { diagnostic(message()) }
    }

    companion object {
        // BluetoothProfile.A2DP_SINK is hidden from the public Android SDK.
        internal const val A2DP_SINK = 11
        private const val PROFILE_TIMEOUT_MILLIS = 3_000L

        fun create(
            context: Context,
            adapter: BluetoothAdapter,
            target: BluetoothDevice,
            handler: Handler,
            isCurrent: () -> Boolean,
            diagnostic: (String) -> Unit,
        ) = BluetoothAudioHandoff(
            target, handler, isCurrent,
            requestProxy = { adapter.getProfileProxy(context.applicationContext, it, A2DP_SINK) },
            releaseProxy = { adapter.closeProfileProxy(A2DP_SINK, it) },
            diagnostic = diagnostic,
        )
    }
}
