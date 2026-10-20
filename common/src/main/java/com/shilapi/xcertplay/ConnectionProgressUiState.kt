package com.shilapi.xcertplay

/**
 * Process-local connection step counter for the New UI connection page (27-dot progress).
 * Updated from [CarPlayHostActivity] while a session is starting; reset when the session ends.
 */
internal object ConnectionProgressUiState {
    @Volatile
    private var litCount: Int = 0

    @Volatile
    private var failed: Boolean = false

    @Volatile
    private var failureDetail: String? = null

    fun litCount(): Int = litCount

    fun failed(): Boolean = failed

    fun failureDetail(): String? = failureDetail

    fun advance(step: Int) {
        val clamped = step.coerceIn(0, CarPlayConnectionProgress.STEP_COUNT)
        if (clamped > litCount) litCount = clamped
    }

    fun markFailed(detail: String? = null) {
        failed = true
        if (!detail.isNullOrBlank()) {
            failureDetail = detail
        }
    }

    fun reset() {
        litCount = 0
        failed = false
        failureDetail = null
    }
}
