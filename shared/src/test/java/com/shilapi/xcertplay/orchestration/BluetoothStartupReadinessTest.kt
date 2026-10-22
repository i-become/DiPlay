package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter.*
import org.junit.Assert.*
import org.junit.Test

class BluetoothStartupReadinessTest {
    private var now = 0L
    private fun await(boot: Boolean = true, cancelled: () -> Boolean = { false }, state: () -> Int) =
        awaitBluetoothStartup(state, boot, cancelled, { now }, { now += it })

    @Test fun enabledRadioHasNoStartupDelay() {
        assertTrue(await { STATE_ON })
        assertEquals(0L, now)
    }

    @Test fun oemEnablesRadioDuringBootWithoutRestartingTheAttempt() {
        assertTrue(await { if (now < 700) STATE_OFF else if (now < 1_100) STATE_TURNING_ON else STATE_ON })
        assertEquals(1_100L, now)
    }

    @Test fun anOffRadioOutsideBootIsNotWaitedForOrEnabled() {
        assertFalse(await(boot = false) { STATE_OFF })
        assertEquals(0L, now)
    }

    @Test fun turningOnOutsideBootIsWaitedFor() {
        assertTrue(await(boot = false) { if (now < 200) STATE_TURNING_ON else STATE_ON })
        assertEquals(200L, now)
    }

    @Test fun offRadioAndStuckTurningOnAreBothBounded() {
        for (state in listOf(STATE_OFF, STATE_TURNING_ON)) {
            now = 0
            assertFalse(await { state })
            assertEquals(8_000L, now)
        }
    }

    @Test fun cancellationStopsPollingImmediately() {
        assertThrows(InterruptedException::class.java) {
            await(cancelled = { now >= 300 }) { STATE_OFF }
        }
        assertEquals(300L, now)
    }

    @Test fun radioTurningOffOrFailingToStartIsNotOverridden() {
        assertFalse(await { STATE_TURNING_OFF })
        assertEquals(0L, now)
        assertFalse(await { if (now < 200) STATE_TURNING_ON else STATE_OFF })
        assertEquals(200L, now)
    }
}
