package com.shilapi.xcertplay.orchestration

/** Optional process-wide metadata sink, installed before a controller or its UI exists. */
object ConnectionDiagnosticTrace {
    @Volatile var sink: ((String) -> Unit)? = null

    fun record(message: String) {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return
        try { sink?.invoke(message) } catch (_: Exception) {
            // Diagnostics must never change a transport callback's result.
        }
    }
}
