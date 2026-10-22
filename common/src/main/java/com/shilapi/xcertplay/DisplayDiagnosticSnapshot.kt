package com.shilapi.xcertplay

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Small, redacted snapshot retained separately from the rotating session logs. */
internal object DisplayDiagnosticSnapshot {
    private const val PREFS = "diplay_display_diagnostics"
    private val fields = listOf("selection", "request", "capability", "effective", "phone", "decoder", "output")
    private fun stamp() = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())

    @Synchronized
    fun selection(context: Context, previous: Int, selected: Int, reconnect: Boolean) {
        if (!DiagnosticLogging.enabled) return
        DiagnosticLogging.write {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("selection",
            "${stamp()} Size setting previous=$previous% selected=$selected% reconnect=$reconnect").apply()
        }
    }

    @Synchronized
    fun begin(context: Context, request: String, capability: String, effective: String): String? {
        if (!DiagnosticLogging.enabled) return null
        val attempt = UUID.randomUUID().toString()
        DiagnosticLogging.write {
        val editor = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        fields.filter { it != "selection" }.forEach(editor::remove)
        editor.putString("attempt", attempt)
        for ((key, line) in listOf("request" to request, "capability" to capability, "effective" to effective)) {
            DiagnosticRedactor.redact(line)?.let { editor.putString(key, "${stamp()} $it") }
        }
        editor.apply()
        }
        return attempt
    }

    @Synchronized
    fun currentAttempt(context: Context): String? = if (DiagnosticLogging.enabled)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("attempt", null) else null

    @Synchronized
    fun record(context: Context, attempt: String?, message: String) {
        if (!DiagnosticLogging.enabled) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (attempt == null || prefs.getString("attempt", null) != attempt) return
        val field = when {
            message.startsWith("Phone display peer ") -> "phone"
            message.startsWith("Video: decoder=") || message.startsWith("Video: decoder configuration failed") -> "decoder"
            message.startsWith("Video: output format ") -> "output"
            else -> return
        }
        val safe = DiagnosticRedactor.redact(message) ?: return
        DiagnosticLogging.write { prefs.edit().putString(field, "${stamp()} $safe").apply() }
    }

    @Synchronized
    fun report(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return fields.mapNotNull { prefs.getString(it, null)?.let(DiagnosticRedactor::redact) }
            .joinToString("\n").ifEmpty { "No display negotiation recorded yet." }
    }
}
