package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
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
            Hs5SteeringKeyPolicy.factoryKeyCodes(context),
        )
        assertTrue(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
    }

    @Test fun registersNothingWhenBothDisabled() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertFalse(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
    }
}
