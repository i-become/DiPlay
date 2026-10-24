package com.shilapi.xcertplay

/** Single/double press routing. A submitted wake request is never treated as assistant state. */
internal class VoiceAssistantKeyGesture(
    private val clockMillis: () -> Long,
    private val doubleTapWindowMillis: Long = DOUBLE_TAP_WINDOW_MILLIS,
) {
    private var lastPressMillis: Long? = null
    private var pendingSingle: Runnable? = null
    private var pendingScheduler: VoiceKeyScheduler? = null

    fun onVoiceKeyDown(
        scheduler: VoiceKeyScheduler,
        singleTarget: VoiceAssistantKeyTarget,
        doubleTarget: VoiceAssistantKeyTarget,
        launch: (VoiceAssistantKeyTarget) -> Unit,
        pressTimeMillis: Long = clockMillis(),
    ) {
        val previous = lastPressMillis
        if (previous != null && pressTimeMillis - previous in 0..doubleTapWindowMillis) {
            reset()
            launch(doubleTarget)
            return
        }
        // If the main thread was busy, finish the expired single before starting another gesture.
        pendingSingle?.run()
        reset()
        lastPressMillis = pressTimeMillis
        val action = object : Runnable {
            override fun run() {
                if (pendingSingle !== this) return
                reset()
                launch(singleTarget)
            }
        }
        pendingSingle = action
        pendingScheduler = scheduler
        val remaining = (doubleTapWindowMillis - (clockMillis() - pressTimeMillis)).coerceAtLeast(0L)
        scheduler.postDelayed(remaining, action)
    }

    fun reset() {
        pendingSingle?.let { pendingScheduler?.cancelDelayed(it) }
        pendingSingle = null
        pendingScheduler = null
        lastPressMillis = null
    }

    companion object {
        const val DOUBLE_TAP_WINDOW_MILLIS = 450L
    }
}
