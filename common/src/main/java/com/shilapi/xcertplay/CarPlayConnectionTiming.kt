package com.shilapi.xcertplay

import java.util.Locale
/** Measures wall time from the waiting page through each connection progress step. */
internal class CarPlayConnectionTiming(
    private val nowNanos: () -> Long = System::nanoTime,
) {
    data class StepRecord(
        val step: Int,
        val label: String,
        val sinceStartMs: Long,
        val sincePreviousMs: Long,
    )

    private var startedNs: Long? = null
    private var lastStepNs: Long? = null
    private var completed = false
    private val records = mutableListOf<StepRecord>()

    val isActive: Boolean get() = startedNs != null && !completed

    fun completed(): Boolean = completed

    fun begin(reset: Boolean = true) {
        if (!reset && startedNs != null) return
        startedNs = nowNanos()
        lastStepNs = startedNs
        completed = false
        records.clear()
    }

    fun ensureStarted() {
        if (startedNs == null) begin(reset = true)
    }

    fun elapsedMs(): Long {
        val start = startedNs ?: return 0L
        return (nowNanos() - start).coerceAtLeast(0) / 1_000_000
    }

    fun formatElapsed(secondsFraction: Int = 1): String {
        val ms = elapsedMs()
        val seconds = ms / 1000.0
        return String.format(Locale.getDefault(), "%.${secondsFraction}f s", seconds)
    }

    fun onStep(step: Int, label: String): List<String> {
        if (completed) return emptyList()
        ensureStarted()
        val start = startedNs ?: return emptyList()
        val now = nowNanos()
        val sinceStartMs = (now - start).coerceAtLeast(0) / 1_000_000
        val anchor = lastStepNs ?: start
        val sincePreviousMs = (now - anchor).coerceAtLeast(0) / 1_000_000
        lastStepNs = now
        records += StepRecord(step, label, sinceStartMs, sincePreviousMs)
        return listOf(
            "connection timing step=$step/${CarPlayConnectionProgress.STEP_COUNT} id=$label " +
                "sinceStartMs=$sinceStartMs sincePreviousMs=$sincePreviousMs",
        )
    }

    fun markFinished() {
        completed = true
    }

    fun finishSuccess(): List<String> {
        if (completed) return emptyList()
        completed = true
        val totalMs = elapsedMs()
        val lines = mutableListOf<String>()
        lines += "connection timing success totalMs=$totalMs stepCount=${records.size}"
        for (record in records) {
            lines += "connection timing breakdown step=${record.step} id=${record.label} " +
                "sinceStartMs=${record.sinceStartMs} sincePreviousMs=${record.sincePreviousMs}"
        }
        val slowest = records.maxByOrNull { it.sincePreviousMs }
        if (slowest != null && slowest.sincePreviousMs >= 500) {
            lines += "connection timing slowestStep step=${slowest.step} id=${slowest.label} " +
                "sincePreviousMs=${slowest.sincePreviousMs}"
        }
        return lines
    }

    fun recordsSnapshot(): List<StepRecord> = records.toList()
}
