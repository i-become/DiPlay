package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Worker writes immutable snapshots; exporting never performs Binder work.
 * Changes survive a later stop at zero. Off means no raw history or formatting work. */
internal class Hs5SpeedEvidence {
    private data class Reading(val source: String, val raw: Int?, val at: Long) {
        fun report() = "source=$source rawInt=$raw rawHex=${raw?.toUInt()?.toString(16)} " +
            "elapsedMs=$at rangeValidKph=${Hs5VehicleSpeed.kph(raw)} units=raw-div-100-kph " +
            "result=${when { raw == null -> "read-failed"; raw < 0 -> "invalid";
                raw > 26_000 -> "format-or-range-unverified"; else -> "range-valid" }}"
    }

    @Volatile private var readings: List<Reading> = emptyList()
    @Volatile private var generation = Long.MIN_VALUE

    fun observe(source: String, raw: Int?, at: Long) {
        if (!DiagnosticLogging.enabled) return
        val token = DiagnosticLogging.generation
        DiagnosticLogging.write(token) {
            if (generation != token) {
                readings = emptyList()
                generation = token
                DiagnosticLogging.onDisabled(::clear)
            }
            val last = readings.lastOrNull { it.source == source }
            if (last != null && last.raw == raw) return@write
            readings = (readings.takeLast(63) + Reading(source, raw, at))
        }
    }

    fun report(): String {
        if (!DiagnosticLogging.enabled || generation != DiagnosticLogging.generation) return "speedEvidence=[]"
        val snapshot = readings
        return snapshot.joinToString(prefix = "speedEvidence=[", postfix = "]", separator = "; ") { it.report() }
    }

    fun clear() { readings = emptyList(); generation = Long.MIN_VALUE }
}
