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

/** Worker owns subscriptions, event-triggered tire reads, and RPM/object detection probes. */
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
    private val leadVehicleReader: Hs5LeadVehicleReader = Hs5OemLeadVehicleReader(),
    private val drivingInfoEnabled: () -> Boolean = { false },
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
    private val assistancePushes = mutableListOf<Pair<Hs5VehicleBinder, Hs5AssistancePush>>()
    private var lastLogAt = Long.MIN_VALUE
    private var lastRpmPollAt = Long.MIN_VALUE
    private var lastTireReadAt = Long.MIN_VALUE
    private var tireRefreshQueued = false
    private var drivingRefreshRequested = false
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
    private val tireRefresh = Runnable { refreshTires() }

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
            "objectDetectionRaw=${value.leadVehicle.raw} objectDetectionSource=${value.leadVehicle.source} " +
            "objectDetectionAgeMs=${age(now, value.leadVehicleAt)} leadVehicle=${displayed.assistance?.leadVehicleDetected} " +
            "remainFuelRaw=${value.fuel.value} fuelWarningRaw=${value.fuelWarning.value} " +
            "speedUnits=raw-div-100-kph rpmSource=background-rpc-750ms " +
            "coolantDisplay=native fuelDisplay=native extensionPolling=disabled " +
            "tireRefresh=vehicle-events-and-missing-wheel-retry tireReadIntervalMs=$TIRE_READ_INTERVAL_MS " +
            "tirePressureKpa=${value.tires.map { it.value }} tireWarningRaw=${value.tireWarnings.map { it.value }} " +
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
                    lastTireReadAt = clock()
                    refreshDrivingInfo(binder)
                    subscribeAssistance(service, binder)
                    publish()
                    event()
                    if (sample.tires.any { !validTirePressure(it.value) }) requestTireRefresh(refreshDriving = false)
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
        if (code == 48) { // onTyrePressureSystemWarning: refresh each wheel, not a wheel index.
            requestTireRefresh(immediate = true)
            return
        }
        val getter = when (code) { 10 -> 7; 11 -> 6; 29 -> 58; 30 -> 31; 31 -> 2; else -> return }
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
            31 -> value.copy(remainingMileage = timed)
            else -> return
        }
        pushTimes[getter] = at
        if (code == 10) speedPushes++
        publish()
        // Dedicated tire notifications are not guaranteed on 322200. Use existing
        // vehicle events to refresh its getters, without an independent polling loop.
        requestTireRefresh()
        if (code == 10 || code == 11) {
            requestRpmPoll()
        }
        if (DiagnosticLogging.enabled && (lastLogAt == Long.MIN_VALUE || at - lastLogAt >= 5000)) {
            lastLogAt = at
            event()
        }
    }

    private fun receiveTire(code: Int, wheel: Int, raw: Int, at: Long) {
        if (wheel !in 0..3) {
            // Firmware variants can use a different index convention. Read verified
            // wheel getters rather than guessing which tire this event describes.
            requestTireRefresh(immediate = true)
            return
        }
        val key = code to wheel
        if (at < (tirePushTimes[key] ?: Long.MIN_VALUE)) return
        val values = if (code == 47) sample.tires else sample.tireWarnings
        val valid = if (code == 47) validTirePressure(raw) else raw == 0 || raw == 1
        val next = values.toMutableList().apply { if (valid) this[wheel] = Hs5TimedInt(raw, at) }
        sample = if (code == 47) sample.copy(tires = next) else sample.copy(tireWarnings = next)
        tirePushTimes[key] = at
        publish()
        if (!valid) requestTireRefresh(refreshDriving = false)
    }

    private fun subscribeAssistance(root: IBinder, vehicle: Hs5VehicleBinder) {
        for (module in Hs5AssistancePush.Module.entries) {
            val manager = Hs5VehicleBinder.query(root, module.id, module.descriptor) ?: continue
            val listener = Hs5AssistancePush(module) { code, raw ->
                val at = clock()
                worker.post {
                    if (active && state === vehicle) {
                        receiveAssistance(module, code, raw, at)
                        requestRpmPoll()
                    }
                }
            }
            if (!manager.listenAt(module.register, listener)) continue
            assistancePushes += manager to listener
            // Bootstrap only zero-argument getters. Cruise/AEB HUD getters require an
            // unverified channel argument, so use their change notifications instead.
            val initial = if (module == Hs5AssistancePush.Module.HUD) listOf(3 to 10, 13 to 17)
                else listOf(1 to 5, 2 to 2, 5 to 7, 6 to 8, 9 to 15, 10 to 14, 14 to 27)
            initial.forEach { (callback, getter) ->
                manager.read(getter)?.let { receiveAssistance(module, callback, it, clock()) }
            }
        }
    }

    private fun receiveAssistance(module: Hs5AssistancePush.Module, code: Int, raw: Int, at: Long) {
        val timed = Hs5TimedInt(raw.takeIf { it >= 0 && it != 254 && it != 255 }, at)
        sample = if (module == Hs5AssistancePush.Module.HUD) when (code) {
            3 -> sample.copy(hudAdasDisplay = timed)
            9 -> sample.copy(hudCruiseDisplay = timed)
            13 -> sample.copy(hudFcwDisplay = timed)
            else -> return
        } else when (code) {
            1 -> sample.copy(cruiseMode = timed)
            2 -> sample.copy(acc = timed)
            5 -> sample.copy(aeb = timed)
            6 -> sample.copy(fcw = timed)
            9 -> sample.copy(lka = timed)
            10 -> sample.copy(ldw = timed)
            14 -> sample.copy(aebDisplay = timed)
            else -> return
        }
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
        worker.removeCallbacks(tireRefresh)
        tireRefreshQueued = false
        drivingRefreshRequested = false
        lastTireReadAt = Long.MIN_VALUE
        assistancePushes.forEach { (manager, listener) ->
            if (manager.alive) manager.listenAt(listener.module.unregister, listener)
        }
        assistancePushes.clear()
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

    private fun requestTireRefresh(immediate: Boolean = false, refreshDriving: Boolean = true) {
        if (!active || state?.alive != true) return
        drivingRefreshRequested = drivingRefreshRequested || refreshDriving
        if (tireRefreshQueued) {
            if (!immediate) return // Frequent speed callbacks must not postpone a queued read.
            worker.removeCallbacks(tireRefresh)
        }
        val elapsed = if (lastTireReadAt == Long.MIN_VALUE) Long.MAX_VALUE else clock() - lastTireReadAt
        val delay = if (immediate) 0L else (TIRE_READ_INTERVAL_MS - elapsed).coerceAtLeast(0L)
        tireRefreshQueued = true
        worker.postDelayed(tireRefresh, delay)
    }

    private fun refreshTires() {
        tireRefreshQueued = false
        val binder = state ?: return
        if (!active || !binder.alive) return
        val previous = sample
        val readings = List(4) { wheel -> binder.read(26, wheel) }
        val tires = List(4) { wheel -> readings[wheel]?.takeIf { validTirePressure(it) }
            ?.let { Hs5TimedInt(it, clock()) } ?: previous.tires[wheel] }
        val warnings = List(4) { wheel -> binder.read(80, wheel)?.takeIf { it == 0 || it == 1 }
            ?.let { Hs5TimedInt(it, clock()) } ?: previous.tireWarnings[wheel] }
        lastTireReadAt = clock()
        sample = previous.copy(tires = tires, tireWarnings = warnings)
        if (drivingRefreshRequested) refreshDrivingInfo(binder)
        drivingRefreshRequested = false
        if (tires.map { it.value } != previous.tires.map { it.value } ||
            warnings.map { it.value } != previous.tireWarnings.map { it.value } ||
            sample.averageFuelConsumption.value != previous.averageFuelConsumption.value ||
            sample.totalMileage.value != previous.totalMileage.value || sample.remainingMileage.value != previous.remainingMileage.value) publish()
        // Bootstrap can precede TPMS initialization. Recover missing wheels even parked;
        // once all four have valid readings, return to event-driven refreshes.
        if (readings.any { !validTirePressure(it) }) requestTireRefresh(refreshDriving = false)
    }

    private fun validTirePressure(raw: Int?) = raw != null && raw in 1..700

    private fun requestRpmPoll() {
        if (!active || state?.alive != true) return
        val elapsed = if (lastRpmPollAt == Long.MIN_VALUE) Long.MAX_VALUE else clock() - lastRpmPollAt
        val delay = if (elapsed >= 500L) 0L else 500L - elapsed
        worker.removeCallbacks(rpmPoll)
        worker.postDelayed(rpmPoll, delay.coerceAtLeast(0L))
    }

    fun requestDrivingInfo() { worker.post {
        if (active) state?.let { refreshDrivingInfo(it); publish() }
    } }

    private fun refreshDrivingInfo(binder: Hs5VehicleBinder) {
        if (!drivingInfoEnabled()) return
        // The target 322200 returns average fuel and odometer in display units; range is integer km.
        // Called on subscription and existing vehicle events, never a new timer.
        sample = sample.copy(averageFuelConsumption = Hs5TimedInt(binder.read(25), clock()),
            totalMileage = Hs5TimedInt(binder.read(24), clock()),
            // The getter can lag behind onRemainMileage on this firmware. Once a
            // push has supplied range, retain it until another push or rebind.
            remainingMileage = if (pushTimes.containsKey(2)) sample.remainingMileage
                else Hs5TimedInt(binder.read(2), clock()))
    }

    private fun pollRpm() {
        if (!active || state?.alive != true) return
        lastRpmPollAt = clock()
        val current = sample
        val nextCan = try {
            val reading = rpmReader.read(current.speed.value, current.gear.value)
            Hs5CanReadings(reading.rpmPrimary, null,
                reading.rpmOem, reading.rpmRule, reading.rpmDebugByte7, reading.note,
                reading.rpcAvailable)
        } catch (_: RuntimeException) {
            Hs5CanReadings(note = "RPM probe failed")
        }
        val lead = try { leadVehicleReader.read() } catch (_: RuntimeException) { Hs5LeadVehicleReading() }
        sample = current.copy(can = nextCan, canAt = clock(), leadVehicle = lead, leadVehicleAt = clock())
        if (nextCan != current.can || lead != current.leadVehicle) publish()
        if (active && state?.alive == true) worker.postDelayed(rpmPoll, 750L)
    }

    private fun event() { if (DiagnosticLogging.enabled) record(traceSummary()) }

    private fun validReading(code: Int, value: Int): Boolean = when (code) {
        2 -> value in 0..1022
        7 -> Hs5VehicleSpeed.kph(value) != null
        6 -> value in 0..8
        31 -> value in 0..100
        58 -> value >= 0 && value != 254 && value != 255
        else -> false
    }

    private companion object {
        const val TIRE_READ_INTERVAL_MS = 1000L
    }
}
