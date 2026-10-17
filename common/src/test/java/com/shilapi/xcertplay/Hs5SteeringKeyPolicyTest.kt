package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class Hs5SteeringKeyPolicyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun registersOnlyEnabledFeatures() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, true)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertArrayEquals(
            intArrayOf(
                KeyEvent.KEYCODE_MEDIA_PREVIOUS,
                KeyEvent.KEYCODE_MEDIA_NEXT,
                KeyEvent.KEYCODE_CALL,
                110,
            ),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = true),
        )
        assertTrue(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
    }

    @Test fun thePhoneKeyStaysWithTheCarWithoutACarPlayCall() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, true)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertArrayEquals(
            intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS, KeyEvent.KEYCODE_MEDIA_NEXT, 110),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false),
        )
    }

    @Test fun backCodesAreTakenOnlyWhileTheFlyScreenShows() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, true)
        assertArrayEquals(
            intArrayOf(259, 110, KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false, flyScreen = true),
        )
        assertArrayEquals(
            intArrayOf(259, 110),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false, flyScreen = false),
        )
    }

    @Test fun registersNothingWhenBothDisabled() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertFalse(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
    }
}
