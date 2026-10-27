package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Handler
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Worker owns the Binder subscription and samples only the RPM signal without a push event. */
internal class Hs5VehicleMonitor(
    private val context: Context,
    private val worker: Handler,
    private val main: Handler,
    private val changed: () -> Unit,
    // Retained for compatibility with the existing constructor call sites; RPM uses the HS5 SDK.
    @Suppress("unused") private val canReader: Hs5CanReader = Hs5RpcCanReader(),
    private val rpmReader: Hs5RpmReader = Hs5SdkRpmReader(),
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val record: (String) -> Unit = { line ->
        DiagnosticLogging.logcat { Log.i("Hs5Vehicle", line) }
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture(line) }
    },
) {
    @Volatile var sample = Hs5VehicleSample()
        private set
    @Volatile private var status = "stopped"
    private var active = false
    private var connection: ServiceConnection? = null
    private var state: Hs5VehicleBinder? = null
    private var remote: IBinder? = null
    private var death: IBinder.DeathRecipient? = null
    private var push: Hs5VehiclePush? = null
    private var lastLogAt = Long.MIN_VALUE
    private var lastRpmPollAt = Long.MIN_VALUE
    private val pushTimes = mutableMapOf<Int, Long>()
    private val tirePushTimes = mutableMapOf<Pair<Int, Int>, Long>()
    @Volatile private var speedPushes = 0L
    @Volatile private var rejectedSpeed: Int? = null
    private val speedEvidence = Hs5SpeedEvidence()
    private val vehicleEvidence = Hs5VehicleEvidence()
    private val notify = Runnable { changed() }
    private val retry = Runnable { bind() }
    private val timeout = Runnable { disconnect("bind-timeout") }
    private val rpmPoll = Runnable { pollRpm() }

    fun start() { worker.post {
        if (!active) {
            active = true
            bind()
        }
    } }

    fun stop() { worker.post {
        active = false
        worker.removeCallbacks(retry)
        worker.removeCallbacks(timeout)
        worker.removeCallbacks(rpmPoll)
        lastRpmPollAt = Long.MIN_VALUE
        releaseBinding()
        status = "stopped"
        sample = Hs5VehicleSample()
        // Diagnostic evidence survives closing the window, until the opt-in is disabled.
        main.removeCallbacks(notify)
    } }

    fun report(): String {
        if (!DiagnosticLogging.enabled) return "HS5 vehicle diagnostics disabled"
        val value = sample
        val now = clock()
        val displayed = Hs5VehicleDisplayPolicy.apply(value.vehicle(now, null))
        return "hs5-vehicle state=$status dataMode=push-plus-rpm-poll speed=${displayed.speedKph} " +
            "acceptedSpeedRaw=${value.speed.value} speedAgeMs=${age(now, value.speed.at)} " +
            "speedPushes=$speedPushes rejectedSpeedRaw=$rejectedSpeed gearRaw=${value.gear.value} " +
            "rpm=${value.can.rpm} rpmOem=${value.can.rpmOem} " +
            "engineSpeedRule=${value.can.rpmRule} rpmDebugByte7=${value.can.rpmDebugByte7} " +
            "rpmNote=${value.can.note} rpcAvailable=${value.can.rpcAvailable} " +
            "remainFuelRaw=${value.fuel.value} fuelWarningRaw=${value.fuelWarning.value} " +
            "speedUnits=raw-div-100-kph rpmSource=background-rpc-750ms " +
            "coolantDisplay=native fuelDisplay=native extensionPolling=disabled " +
            "rpmPollIntervalMs=750\n" +
            speedEvidence.report() + "\n" + vehicleEvidence.report()
    }

    private fun age(now: Long, at: Long): Long? = if (at < 0) null else now - at

    // Keep startup's privacy-safe summary separate from raw diagnostic evidence.
    private fun traceSummary(): String = "HS5_VEHICLE state=$status dataMode=push-plus-rpm-poll " +
        "speedRaw=${sample.speed.value} speedKph=${Hs5VehicleSpeed.kph(sample.speed.value)} " +
        "gearRaw=${sample.gear.value} rpm=${sample.can.rpm} " +
        "speedUnits=raw-div-100-kph rpmSource=background-rpc"

    private fun bind() {
        if (!active || connection != null) return
        status = "binding"
        val candidate = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { worker.post {
                if (!active || connection !== this) return@post
                worker.removeCallbacks(timeout)
                try {
                    val manager = QisiPositionLightBinder.manager(service)
                    val binder = Hs5VehicleBinder(manager)
                    state = binder
                    remote = manager
                    val recipient = IBinder.DeathRecipient { worker.post {
                        if (active && state === binder) disconnect("binder-dead")
                    } }
                    death = recipient
                    manager.linkToDeath(recipient, 0)
                    val listener = Hs5VehiclePush(receiveTire = { code, wheel, value ->
                        val receivedAt = clock()
                        worker.post {
                            if (active && connection === this && state === binder) receiveTire(code, wheel, value, receivedAt)
                        }
                    }) { code, value ->
                        val receivedAt = clock()
                        worker.post {
                            if (active && connection === this && state === binder) receive(code, value, receivedAt)
                        }
                    }
                    if (!binder.listen(listener, true)) {
                        disconnect("listener-unavailable")
                        return@post
                    }
                    push = listener
                    status = "connected-push"
                    sample = Hs5VehicleSample(pushConnected = true)
                    // Register first. Pushes received during the one-off reads are queued
                    // on this worker and override the bootstrap cache when the reads complete.
                    fun initial(code: Int): Hs5TimedInt {
                        val raw = binder.read(code)
                        if (code == 7 && DiagnosticLogging.enabled) speedEvidence.observe("getter7-initial", raw, clock())
                        if (raw == null || !validReading(code, raw)) {
                            if (code == 7) rejectedSpeed = raw
                            return Hs5TimedInt()
                        }
                        return Hs5TimedInt(raw, clock())
                    }
                    sample = sample.copy(speed = initial(7), gear = initial(6),
                        fuel = initial(31), fuelWarning = initial(58),
                        tires = List(4) { wheel -> Hs5TimedInt(binder.read(26, wheel), clock()) },
                        tireWarnings = List(4) { wheel -> Hs5TimedInt(binder.read(80, wheel), clock()) })
                    publish()
                    event()
                    requestRpmPoll()
                } catch (_: Exception) { disconnect("manager-unavailable") }
            } }

            override fun onServiceDisconnected(name: ComponentName) = lost("service-disconnected")
            override fun onBindingDied(name: ComponentName) = lost("binding-died")
            override fun onNullBinding(name: ComponentName) = lost("null-binding")
            private fun lost(reason: String) { worker.post {
                if (active && connection === this) disconnect(reason)
            } }
        }
        connection = candidate
        val accepted = try {
            context.bindService(Intent().setComponent(ComponentName(QisiPositionLightBinder.PACKAGE,
                QisiPositionLightBinder.SERVICE)), candidate, Context.BIND_AUTO_CREATE)
        } catch (_: RuntimeException) { false }
        if (accepted) worker.postDelayed(timeout, 8000L) else {
            connection = null
            disconnect("bind-rejected")
        }
    }

    private fun receive(code: Int, raw: Int, at: Long) {
        val getter = when (code) { 10 -> 7; 11 -> 6; 29 -> 58; 30 -> 31; else -> return }
        if (code == 10 && DiagnosticLogging.enabled) speedEvidence.observe("push10", raw, at)
        if (at < (pushTimes[getter] ?: Long.MIN_VALUE)) return
        val valid = validReading(getter, raw)
        if (!valid && code != 10) return
        // Invalid speed clears the needle rather than retaining a false earlier speed.
        if (!valid) rejectedSpeed = raw
        val timed = Hs5TimedInt(raw.takeIf { valid }, at)
        val value = sample
        sample = when (code) {
            10 -> value.copy(speed = timed)
            11 -> value.copy(gear = timed)
            29 -> value.copy(fuelWarning = timed)
            30 -> value.copy(fuel = timed)
            else -> return
        }
        pushTimes[getter] = at
        if (code == 10) speedPushes++
        publish()
        if (code == 10 || code == 11) {
            requestRpmPoll()
        }
        if (DiagnosticLogging.enabled && (lastLogAt == Long.MIN_VALUE || at - lastLogAt >= 5000)) {
            lastLogAt = at
            event()
        }
    }

    private fun receiveTire(code: Int, wheel: Int, raw: Int, at: Long) {
        if (wheel !in 0..3) return
        val key = code to wheel
        if (at < (tirePushTimes[key] ?: Long.MIN_VALUE)) return
        val values = if (code == 47) sample.tires else sample.tireWarnings
        val valid = if (code == 47) raw in 1..253 else raw == 0 || raw == 1
        val next = values.toMutableList().apply { this[wheel] = Hs5TimedInt(raw.takeIf { valid }, at) }
        sample = if (code == 47) sample.copy(tires = next) else sample.copy(tireWarnings = next)
        tirePushTimes[key] = at
        publish()
    }

    private fun disconnect(reason: String) {
        worker.removeCallbacks(timeout)
        worker.removeCallbacks(rpmPoll)
        releaseBinding()
        sample = Hs5VehicleSample()
        status = reason
        publish()
        event()
        worker.removeCallbacks(retry)
        if (active) worker.postDelayed(retry, 3000L)
    }

    private fun releaseBinding() {
        val binder = state
        val listener = push
        state = null
        push = null
        pushTimes.clear()
        tirePushTimes.clear()
        val watched = remote
        val recipient = death
        remote = null
        death = null
        if (watched != null && recipient != null) try { watched.unlinkToDeath(recipient, 0) } catch (_: RuntimeException) { }
        if (binder != null && listener != null && binder.alive) binder.listen(listener, false)
        val previous = connection
        connection = null
        if (previous != null) try { context.unbindService(previous) } catch (_: RuntimeException) { }
    }

    private fun publish() {
        main.removeCallbacks(notify)
        main.post(notify)
        if (DiagnosticLogging.enabled) vehicleEvidence.observe(clock(), sample) {
            "rpmPollIntervalMs=750 rpmSource=background-rpc canFrameId=0"
        }
    }

    private fun requestRpmPoll() {
        if (!active || state?.alive != true) return
        val elapsed = if (lastRpmPollAt == Long.MIN_VALUE) Long.MAX_VALUE else clock() - lastRpmPollAt
        val delay = if (elapsed >= 500L) 0L else 500L - elapsed
        worker.removeCallbacks(rpmPoll)
        worker.postDelayed(rpmPoll, delay.coerceAtLeast(0L))
    }

    private fun pollRpm() {
        if (!active || state?.alive != true) return
        lastRpmPollAt = clock()
        val current = sample
        try {
            val reading = rpmReader.read(current.speed.value, current.gear.value)
            val nextCan = Hs5CanReadings(reading.rpmPrimary, null,
                reading.rpmOem, reading.rpmRule, reading.rpmDebugByte7, reading.note,
                reading.rpcAvailable)
            sample = current.copy(can = nextCan, canAt = clock())
            if (nextCan != current.can) publish()
        } catch (_: RuntimeException) {
            val nextCan = Hs5CanReadings(note = "RPM probe failed")
            sample = current.copy(can = nextCan, canAt = clock())
            if (nextCan != current.can) publish()
        }
        if (active && state?.alive == true) worker.postDelayed(rpmPoll, 750L)
    }

    private fun event() { if (DiagnosticLogging.enabled) record(traceSummary()) }

    private fun validReading(code: Int, value: Int): Boolean = when (code) {
        7 -> Hs5VehicleSpeed.kph(value) != null
        6 -> value in 0..8
        31 -> value in 0..100
        58 -> value >= 0 && value != 254 && value != 255
        else -> false
    }
}
