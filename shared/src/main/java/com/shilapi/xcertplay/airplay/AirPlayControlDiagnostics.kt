package com.shilapi.xcertplay.airplay

/** Only fixed protocol labels, counts, status and timings survive into the diagnostic report. */
internal object AirPlayControlDiagnostics {
    fun request(method: String, path: String, bytes: Int): String {
        return "airplay control request method=${verb(method)} route=${route(path)} contentBytes=$bytes"
    }

    fun timing(
        method: String, path: String, sequence: Int, status: Int,
        elapsedMillis: Long, gapMillis: Long, processingMillis: Long, writeMillis: Long,
    ): String = "airplay control timing method=${verb(method)} route=${route(path)} request=$sequence " +
        "status=$status elapsedMs=$elapsedMillis gapMs=$gapMillis processingMs=$processingMillis writeMs=$writeMillis"

    private fun verb(method: String): String =
        method.takeIf { it in setOf("GET", "POST", "SETUP", "RECORD", "TEARDOWN", "OPTIONS", "GET_PARAMETER", "SET_PARAMETER") } ?: "OTHER"

    private fun route(path: String): String =
        path.substringBefore('?').substringBefore('#').substringAfterLast('/').lowercase()
            .takeIf { it in setOf("info", "pair-setup", "pair-verify", "auth-setup", "fp-setup") } ?: "other"
}
