package com.shilapi.xcertplay.carlink

/** Recovery rules from the validated HS5 session lifecycle. */
internal object CarlinkSessionPolicy {
    fun canAdvertise(state: CarlinkLinkState, pin: String?, busy: Boolean, casting: Boolean): Boolean =
        state != CarlinkLinkState.CONNECTED && pin.isNullOrBlank() && !busy && !casting

    fun retryDelay(error: Int, streak: Int): Long =
        if (error in setOf(10003, 10006, 10008))
            (1200L * (1L shl (streak - 1).coerceIn(0, 3))).coerceAtMost(8000L)
        else 1200L
}
