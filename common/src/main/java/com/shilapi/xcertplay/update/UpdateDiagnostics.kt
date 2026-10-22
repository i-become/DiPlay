package com.shilapi.xcertplay.update

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.os.SystemClock
import android.util.Log

/** Bounded history survives an Activity recreation or a failed installer opening. No URLs or paths. */
internal object UpdateDiagnostics {
    private const val PREFS = "diplay_update_diagnostics"
    private const val EVENTS = "events"
    private const val MAX_EVENTS = 40

    @Synchronized fun record(context: Context, event: String) {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        DiagnosticLogging.write { runCatching {
            val safe = event.replace('\n', ' ').replace('\r', ' ').take(500)
            val line = "epochMs=${System.currentTimeMillis()} uptimeMs=${SystemClock.elapsedRealtime()} $safe"
            DiagnosticLogging.logcat { Log.i("DiPlay-Update", line) }
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val lines = prefs.getString(EVENTS, "").orEmpty().lineSequence()
                .filter { it.isNotBlank() }.toList().takeLast(MAX_EVENTS - 1) + line
            prefs.edit().putString(EVENTS, lines.joinToString("\n")).apply()
        } }
    }

    @Synchronized fun report(context: Context): String = runCatching {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(EVENTS, null)
            ?: "No foreground update events recorded by this version."
    }.getOrDefault("Update diagnostics unavailable.")
}
