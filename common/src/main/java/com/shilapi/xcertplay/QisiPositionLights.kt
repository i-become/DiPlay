package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/** Read-only Hongqi Android 9 protocol supplied for the original QiSi adapter service. */
internal object QisiPositionLightBinder {
    const val PACKAGE = "ltd.qisi.adapterservice"
    const val SERVICE = "ltd.qisi.adapterservice.QISIAdapterService"
    const val ROOT = "ltd.qisi.adapterproxy.IVehicleRemoteClienter"
    const val VEHICLE_STATE = "ltd.qisi.adapterproxy.ICarVehicleStateManager"

    fun manager(remote: IBinder): IBinder {
        if (remote.interfaceDescriptor != ROOT) throw RemoteException("unexpected QiSi root interface")
        return transact(remote, ROOT, 1, { writeInt(15) }) {
            val manager = readStrongBinder() ?: throw RemoteException("missing vehicle state manager")
            if (manager.interfaceDescriptor != VEHICLE_STATE) throw RemoteException("unexpected QiSi state interface")
            manager
        }
    }

    fun read(manager: IBinder): Int = transact(manager, VEHICLE_STATE, 19, {}) {
        if (dataAvail() < 4) throw RemoteException("missing position light state")
        readInt()
    }

    fun night(raw: Int): Boolean? = when (raw) {
        0 -> false
        1 -> true
        else -> null
    }

    private inline fun <T> transact(
        binder: IBinder,
        descriptor: String,
        code: Int,
        write: Parcel.() -> Unit,
        read: Parcel.() -> T,
    ): T {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken(descriptor)
            data.write()
            if (!binder.transact(code, data, reply, 0)) throw RemoteException("QiSi transaction rejected: $code")
            if (reply.dataAvail() < 4) throw RemoteException("missing QiSi reply")
            reply.readException()
            return reply.read()
        } finally {
            data.recycle()
            reply.recycle()
        }
    }
}

/** One binding per process. No vehicle Binder operation runs on the UI/CarPlay connection threads. */
internal class QisiPositionLightMonitor(
    private val context: Context,
    private val worker: Handler,
    private val main: Handler,
    private val onChanged: (Boolean?) -> Unit,
    private val record: (String) -> Unit = {},
) {
    @Volatile var night: Boolean? = null
        private set
    @Volatile private var snapshot = "state=not-started night=unknown"
    private val events = ArrayDeque<String>()
    private var started = false
    private var connection: ServiceConnection? = null
    private var manager: IBinder? = null
    private var reads = 0L
    private var attempts = 0L
    private var lastRaw: Int? = null
    private var lastSuccess: Long? = null
    private var deliveredNight: Boolean? = null // Main-thread delivery only.
    private var hasDelivered = false
    private val retry = Runnable { bind() }
    private val bindTimeout = Runnable { fail("bind-timeout") }
    private val poll = object : Runnable {
        override fun run() {
            val binder = manager ?: return
            try {
                val raw = QisiPositionLightBinder.read(binder)
                if (DiagnosticLogging.enabled) {
                    reads++
                    lastSuccess = SystemClock.elapsedRealtime()
                }
                val changed = lastRaw != raw
                lastRaw = raw
                publish(QisiPositionLightBinder.night(raw))
                updateSnapshot("connected")
                if (changed) event("sample raw=$raw night=${night ?: "unknown"}")
                worker.postDelayed(this, POLL_MS)
            } catch (error: Exception) {
                fail("read-${error.javaClass.simpleName}")
            }
        }
    }

    fun start() {
        worker.post {
            if (!started) {
                started = true
                bind()
            }
        }
    }

    fun stop() {
        worker.post {
            started = false
            release()
            publish(null)
            updateSnapshot("stopped")
        }
    }

    fun report(): String = snapshot + "\n" + synchronized(events) { events.joinToString("\n") }

    private fun bind() {
        if (!started || connection != null) return
        if (DiagnosticLogging.enabled) attempts++
        val next = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) {
                worker.post {
                    if (!started || connection !== this) return@post
                    worker.removeCallbacks(bindTimeout)
                    try {
                        manager = QisiPositionLightBinder.manager(service)
                        event("manager-ready module=15 readTransaction=19")
                        poll.run()
                    } catch (error: Exception) {
                        fail("manager-${error.javaClass.simpleName}")
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName) = disconnected("service-disconnected")
            override fun onBindingDied(name: ComponentName) = disconnected("binding-died")
            override fun onNullBinding(name: ComponentName) = disconnected("null-binding")

            private fun disconnected(reason: String) {
                worker.post { if (connection === this) fail(reason) }
            }
        }
        connection = next
        updateSnapshot("binding")
        event("bind attempt=$attempts")
        try {
            val intent = Intent().setComponent(ComponentName(QisiPositionLightBinder.PACKAGE, QisiPositionLightBinder.SERVICE))
            if (context.bindService(intent, next, Context.BIND_AUTO_CREATE)) {
                worker.postDelayed(bindTimeout, BIND_TIMEOUT_MS)
            } else {
                fail("bind-rejected")
            }
        } catch (error: Exception) {
            fail("bind-${error.javaClass.simpleName}")
        }
    }

    private fun fail(reason: String) {
        release()
        lastRaw = null
        publish(null)
        updateSnapshot("retry:$reason")
        event("unavailable reason=$reason retryMs=$RETRY_MS")
        if (started) worker.postDelayed(retry, RETRY_MS)
    }

    private fun release() {
        worker.removeCallbacks(poll)
        worker.removeCallbacks(retry)
        worker.removeCallbacks(bindTimeout)
        manager = null
        val previous = connection
        connection = null
        if (previous != null) runCatching { context.unbindService(previous) }
    }

    private fun publish(value: Boolean?) {
        if (night == value) return
        night = value
        // Deliver the latest value, so an old queued sample cannot override a later disconnect.
        main.post {
            val latest = night
            if (!hasDelivered || deliveredNight != latest) {
                hasDelivered = true
                deliveredNight = latest
                onChanged(latest)
            }
        }
    }

    private fun updateSnapshot(state: String) {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        snapshot = "state=$state night=${night ?: "unknown"} raw=${lastRaw ?: "unknown"} " +
            "attempts=$attempts reads=$reads lastSuccessElapsedMs=${lastSuccess ?: "none"} " +
            "pollMs=$POLL_MS retryMs=$RETRY_MS"
    }

    private fun event(message: String) {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        val line = "QISI_POSITION_LIGHT elapsedMs=${SystemClock.elapsedRealtime()} $message"
        synchronized(events) {
            if (events.size >= 64) events.removeFirst()
            events.addLast(line)
        }
        record(line)
    }

    companion object {
        const val POLL_MS = 500L
        const val RETRY_MS = 3_000L
        const val BIND_TIMEOUT_MS = 10_000L
    }
}

/** Process ownership keeps the read alive through UI changes and background CarPlay sessions. */
internal object HongqiPositionLights {
    @Volatile private var monitor: QisiPositionLightMonitor? = null

    @Synchronized fun initialize(context: Context) {
        if (monitor != null || !Hs5HeadUnit.isHeadUnit(context)) return
        val app = context.applicationContext
        val thread = HandlerThread("DiPlay-QisiLights").apply { start() }
        monitor = QisiPositionLightMonitor(app, Handler(thread.looper), Handler(Looper.getMainLooper()), { night ->
            CarPlayHostSettingsBridge.notifyVehicleNightChanged(app, night)
            AppAppearanceRuntime.invalidateVehicleLight()
        }, { line ->
            DiagnosticLogging.logcat { Log.i("DiPlay-QisiLights", line) }
            DiagnosticLogging.record { CarPlayStartupDiagnostics.capture(line) }
        }).also { it.start() }
    }

    fun enabled(): Boolean = monitor != null
    fun night(): Boolean? = monitor?.night
    fun report(): String = monitor?.report() ?: "QiSi position lights not enabled on this device."
}
