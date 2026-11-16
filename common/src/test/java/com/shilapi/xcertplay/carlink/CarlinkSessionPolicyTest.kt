package com.shilapi.xcertplay.carlink

import org.junit.Assert.*
import org.junit.Test

class CarlinkSessionPolicyTest {
    @Test fun discoveryCannotRotateDisplayedPinOrInterruptHandshake() {
        assertFalse(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.PAIRING, "123456", false, false))
        assertFalse(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.ADVERTISING, null, true, false))
        assertFalse(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.CONNECTED, null, false, false))
        assertFalse(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.CONNECTED, null, false, true))
        assertTrue(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.ERROR, null, false, false))
        assertTrue(CarlinkSessionPolicy.canAdvertise(CarlinkLinkState.ADVERTISING, null, false, false))
    }

    @Test fun wirelessTeardownGetsBoundedBackoffAndOtherErrorsDoNot() {
        for (error in listOf(10003, 10006, 10008)) {
            assertEquals(listOf(1200L, 2400L, 4800L, 8000L, 8000L),
                (1..5).map { CarlinkSessionPolicy.retryDelay(error, it) })
        }
        assertEquals(1200L, CarlinkSessionPolicy.retryDelay(10001, 5))
        assertEquals(8000L, CarlinkSessionPolicy.retryDelay(10003, Int.MAX_VALUE))
    }
}
