package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Bounded opt-in evidence survives flight-screen close/reopen. No Binder work at export. */
internal class Hs5VehicleEvidence {
    private data class Reading(val at: Long, val sample: Hs5VehicleSample, val can: String) {
        fun report() = "elapsedMs=$at speedRaw=${sample.speed.fresh(at)} gearRaw=${sample.gear.fresh(at)} " +
            "brakeRaw=${sample.brake.fresh(at)} rpmCandidate=${sample.can.rpm} " +
            "rpmOem=${sample.can.rpmOem} engineSpeedRule=${sample.can.rpmRule} " +
            "rpmDebugByte7=${sample.can.rpmDebugByte7} rpmNote=${sample.can.note} " +
            "coolantCandidate=${sample.can.coolant} remainFuelRaw=${sample.fuel.fresh(at)} " +
            "objectDetectionRaw=${sample.leadVehicle.raw} objectDetectionSource=${sample.leadVehicle.source} " +
            "hudCruiseDisplay=${sample.hudCruiseDisplay.value} hudFcwDisplay=${sample.hudFcwDisplay.value} " +
            "acc=${sample.acc.value} cruiseMode=${sample.cruiseMode.value} " +
            "tirePressureKpa=${sample.tires.map { it.value }} tireWarningRaw=${sample.tireWarnings.map { it.value }} $can"
    }

    @Volatile private var readings: List<Reading> = emptyList()
    @Volatile private var generation = Long.MIN_VALUE

    fun observe(at: Long, sample: Hs5VehicleSample, can: () -> String) {
        if (!DiagnosticLogging.enabled) return
        val token = DiagnosticLogging.generation
        DiagnosticLogging.write(token) {
            if (generation != token) {
                readings = emptyList()
                generation = token
                DiagnosticLogging.onDisabled(::clear)
            }
            val previous = readings.lastOrNull()
            if (previous != null && at >= previous.at && at - previous.at < 1000L) return@write
            readings = readings.takeLast(599) + Reading(at, sample, can())
        }
    }

    fun report(): String {
        if (!DiagnosticLogging.enabled || generation != DiagnosticLogging.generation) return "vehicleEvidence=[]"
        val snapshot = readings
        return snapshot.joinToString(prefix = "vehicleEvidence=[\n", postfix = "\n]", separator = "\n") { it.report() }
    }

    private fun clear() { readings = emptyList(); generation = Long.MIN_VALUE }
}
