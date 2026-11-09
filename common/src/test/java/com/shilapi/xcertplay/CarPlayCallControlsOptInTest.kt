package com.shilapi.xcertplay

import com.shilapi.xcertplay.hud.Hs5NavigationOutputs
import com.shilapi.xcertplay.hud.Hs5OutputSettings
import com.shilapi.xcertplay.hud.CarPlayCallState
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CarPlayCallControlsOptInTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @After fun cleanup() {
        Hs5OutputSettings.setEnabled(app, true)
        Hs5NavigationOutputs.endNow()
    }

    @Test fun newCallKeysCannotConsumeOrSendAnythingWithoutOptIn() {
        val controller = mock(CarPlayController::class.java)
        for (code in listOf(313, 314, 309)) {
            assertFalse(CarPlayCallKeys.onKey(app, code, true, controller))
            assertFalse(CarPlayCallKeys.onKey(app, code, false, controller))
        }
        verifyNoInteractions(controller)
    }

    @Test fun explicitOptInEnablesAnswerAndDisableRestoresPassThrough() {
        val controller = mock(CarPlayController::class.java)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        Hs5NavigationOutputs.onFrame(Iap2Messages.buildRaw(CarPlayCallState.CALL_STATE_UPDATE) {
            u8(2, 2); string(4, "incoming-call")
        })
        Hs5OutputSettings.setEnabled(app, true)
        assertTrue(CarPlayCallKeys.onKey(app, 313, true, controller))
        assertTrue(CarPlayCallKeys.onKey(app, 313, false, controller))
        verify(controller).answerCall()
        Hs5OutputSettings.setEnabled(app, false)
        assertFalse(CarPlayCallKeys.onKey(app, 314, false, controller))
        verify(controller, never()).endCall()
    }
}
