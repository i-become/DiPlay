package com.shilapi.xcertplay.carlink

import org.junit.Assert.*
import org.junit.Test

class CarlinkGesturePolicyTest {
    private fun CarlinkGesturePolicy.event(mask: Int, count: Int = 3, x: Float = 0f, y: Float = 0f,
        settings: Int = 3, cluster: Boolean = true) = accept(mask, count, x, y, settings, cluster, 72f)

    @Test fun downSwipeOpensSettingsAndConsumesAllRemainingFingers() {
        val policy = CarlinkGesturePolicy()
        assertFalse(policy.event(0, 1).consume)
        assertTrue(policy.event(5).cancelPhone)
        assertEquals(CarlinkGesturePolicy.Action.SETTINGS, policy.event(2, y = 90f).action)
        assertNull(policy.event(2, x = -150f).action)
        assertTrue(policy.event(6).consume)
        assertTrue(policy.event(2, 2).consume)
        assertTrue(policy.event(1, 1).consume)
        assertFalse(policy.event(0, 1).consume)
    }

    @Test fun horizontalDirectionAndSettingsPreferencesMatchHost() {
        for ((x, action) in listOf(-90f to CarlinkGesturePolicy.Action.SHOW_CLUSTER,
            90f to CarlinkGesturePolicy.Action.HIDE_CLUSTER)) {
            val policy = CarlinkGesturePolicy()
            policy.event(5)
            assertEquals(action, policy.event(2, x = x).action)
        }
        val disabled = CarlinkGesturePolicy()
        assertFalse(disabled.event(5, settings = 0, cluster = false).consume)
        val two = CarlinkGesturePolicy()
        assertTrue(two.event(5, count = 2, settings = 2).cancelPhone)
        assertEquals(CarlinkGesturePolicy.Action.SETTINGS, two.event(2, count = 2, y = 80f, settings = 2).action)
    }

    @Test fun diagonalAndInterruptedGesturesDoNotTrigger() {
        val policy = CarlinkGesturePolicy()
        policy.event(5)
        assertNull(policy.event(2, x = 80f, y = 80f).action)
        policy.event(6)
        assertNull(policy.event(2, y = 150f).action)
        assertTrue(policy.event(3).consume)
        assertFalse(policy.event(2, 1).consume)
    }

    @Test fun additionalFingerCanSelectSettingsWithoutSendingAnotherCancel() {
        val policy = CarlinkGesturePolicy()
        assertTrue(policy.event(5, count = 3, settings = 4).cancelPhone)
        assertFalse(policy.event(5, count = 4, settings = 4).cancelPhone)
        assertEquals(CarlinkGesturePolicy.Action.SETTINGS,
            policy.event(2, count = 4, y = 90f, settings = 4).action)
    }
}
