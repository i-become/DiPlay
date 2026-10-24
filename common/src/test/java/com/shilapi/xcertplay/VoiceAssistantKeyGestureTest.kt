package com.shilapi.xcertplay

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceAssistantKeyGestureTest {
    private var clock = 0L
    private val gesture = VoiceAssistantKeyGesture(clockMillis = { clock })
    private val pending = mutableListOf<Runnable>()
    private val cancelled = mutableListOf<Runnable>()
    private val launched = mutableListOf<VoiceAssistantKeyTarget>()
    private val scheduler = object : VoiceKeyScheduler {
        override fun postDelayed(delayMillis: Long, runnable: Runnable) { pending += runnable }
        override fun cancelDelayed(runnable: Runnable) { pending.remove(runnable); cancelled += runnable }
    }

    private fun press(single: VoiceAssistantKeyTarget, double: VoiceAssistantKeyTarget, at: Long = clock) {
        gesture.onVoiceKeyDown(scheduler, single, double, { launched += it }, at)
    }

    private fun finishSingle() {
        clock += 451
        pending.toList().forEach { it.run() }
    }

    @Test fun repeatedSinglePressesAlwaysUseConfiguredTarget() {
        repeat(3) {
            press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI)
            assertEquals(it, launched.size)
            finishSingle()
        }
        assertEquals(List(3) { VoiceAssistantKeyTarget.HONGQI }, launched)
    }

    @Test fun bothConfigurationsKeepSingleAndDoubleIndependentAcrossRepeatedRequests() {
        VoiceAssistantKeyTarget.entries.forEach { single ->
            val double = VoiceAssistantKeyTarget.entries.first { it != single }
            repeat(2) {
                press(single, double)
                clock += 350
                press(single, double)
                assertEquals(double, launched.last())
                finishSingle()
                press(single, double)
                finishSingle()
                assertEquals(single, launched.last())
            }
        }
        assertEquals(8, launched.size)
    }

    @Test fun changingSettingsOrDisconnectingCancelsEvenAlreadyDequeuedRunnable() {
        press(VoiceAssistantKeyTarget.SIRI, VoiceAssistantKeyTarget.HONGQI)
        val old = pending.single()
        gesture.reset()
        old.run()
        assertEquals(emptyList<VoiceAssistantKeyTarget>(), launched)
        assertEquals(listOf(old), cancelled)
    }

    @Test fun doublePressCancelsDelayedSingleEvenIfRunnableHasAlreadyBeenDequeued() {
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI)
        val old = pending.single()
        clock = 100
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI)
        old.run()
        assertEquals(listOf(VoiceAssistantKeyTarget.SIRI), launched)
    }

    @Test fun expiredSingleIsNotLostWhenMainThreadIsBusy() {
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI)
        clock = 800
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI)
        assertEquals(listOf(VoiceAssistantKeyTarget.HONGQI), launched)
        finishSingle()
        assertEquals(List(2) { VoiceAssistantKeyTarget.HONGQI }, launched)
    }

    @Test fun eventReceiptTimeControlsDoublePressWhenDispatchIsDelayed() {
        clock = 400
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI, at = 100)
        clock = 420
        press(VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI, at = 300)
        assertEquals(listOf(VoiceAssistantKeyTarget.SIRI), launched)
    }

    @Test fun wakeFailureDoesNotLeaveAStickyAssistantState() {
        gesture.onVoiceKeyDown(scheduler, VoiceAssistantKeyTarget.HONGQI, VoiceAssistantKeyTarget.SIRI,
            launch = { throw IllegalStateException("wake failed") })
        runCatching { finishSingle() }
        press(VoiceAssistantKeyTarget.SIRI, VoiceAssistantKeyTarget.HONGQI)
        finishSingle()
        assertEquals(listOf(VoiceAssistantKeyTarget.SIRI), launched)
    }
}
