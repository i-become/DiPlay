package com.shilapi.xcertplay

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Queued entries retain only their exact file target and a redacted, bounded metadata string. */
internal object AsyncDiagnosticLog {
    private data class Entry(val target: SessionLogFile, val line: String)
    @Volatile private var writer: BoundedDiagnosticWriter<Entry>? = null

    @Synchronized private fun activeWriter(): BoundedDiagnosticWriter<Entry>? {
        if (!DiagnosticLogging.enabled) return null
        return writer ?: BoundedDiagnosticWriter<Entry> { it.target.append(it.line) }.also { next ->
            writer = next
            DiagnosticLogging.onDisabled {
                synchronized(this) { next.cancel(); if (writer === next) writer = null }
            }
        }
    }

    fun append(target: SessionLogFile?, message: String, nowMillis: Long = System.currentTimeMillis()) {
        if (!DiagnosticLogging.enabled || target == null || !target.recordingActive) return
        val safe = DiagnosticRedactor.redact(message) ?: return
        val timestamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date(nowMillis))
        runCatching { activeWriter()?.enqueue(Entry(target, "$timestamp  $safe")) }
    }

    internal fun awaitIdle(timeoutMillis: Long): Boolean = writer?.awaitIdle(timeoutMillis) ?: true
}
