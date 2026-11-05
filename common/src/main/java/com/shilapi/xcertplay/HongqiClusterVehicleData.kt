package com.shilapi.xcertplay

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/** A shared reader only while an HS5 instrument window is visible; no work on the boot path. */
internal object HongqiClusterVehicleData {
    private val listeners = linkedSetOf<() -> Unit>() // Main thread only.
    private var monitor: Hs5VehicleMonitor? = null
    @Volatile private var drivingReaders = 0

    fun subscribe(context: Context, changed: () -> Unit, includeDrivingInfo: Boolean = false): () -> Unit {
        check(Looper.myLooper() == Looper.getMainLooper())
        if (!Hs5HeadUnit.isHeadUnit(context)) return {}
        if (monitor == null) {
            val worker = HandlerThread("Hs5Vehicle").also { it.start() }
            monitor = Hs5VehicleMonitor(context.applicationContext, Handler(worker.looper),
                Handler(Looper.getMainLooper()), { listeners.toList().forEach { it() } },
                drivingInfoEnabled = { drivingReaders > 0 })
        }
        if (includeDrivingInfo) drivingReaders++
        listeners.add(changed)
        if (listeners.size == 1) monitor?.start()
        if (includeDrivingInfo) monitor?.requestDrivingInfo()
        var closed = false
        return {
            check(Looper.myLooper() == Looper.getMainLooper())
            if (!closed) {
                closed = true
                if (includeDrivingInfo) drivingReaders--
                listeners.remove(changed)
                if (listeners.isEmpty()) monitor?.stop()
            }
        }
    }

    fun snapshot(): OemClusterVehicleState =
        Hs5VehicleDisplayPolicy.apply((if (listeners.isEmpty()) null else monitor?.sample)
            ?.vehicle(SystemClock.elapsedRealtime(), HongqiPositionLights.night())
            ?: OemClusterVehicleState(positionLights = HongqiPositionLights.night()))

    fun report(): String = if (!DiagnosticLogging.enabled) "HS5 vehicle diagnostics disabled"
        else monitor?.report() ?: "hs5-vehicle state=not-started (instrument window not shown)"
}
