package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class Hs5SteeringKeyPolicyTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun disableMapShortcutByDefault() {
        AirPlayPersistence.saveClusterMapEnabled(context, false)
    }

    @Test fun mapShortcutRegistersPreviousIndependentlyOfMediaAndBluetooth() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        AirPlayPersistence.saveClusterMapEnabled(context, true)
        assertTrue(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
        assertArrayEquals(intArrayOf(KeyEvent.KEYCODE_MEDIA_PREVIOUS),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false))
        AirPlayPersistence.saveClusterMapEnabled(context, false)
        assertFalse(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
    }

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
            intArrayOf(231, 259, 110, 4),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false, flyScreen = true),
        )
        assertArrayEquals(
            intArrayOf(231, 259, 110),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false, flyScreen = false),
        )
    }

    @Test fun registersNothingWhenBothDisabled() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertFalse(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
        assertArrayEquals(intArrayOf(), Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = false))
    }

    @Test fun flyScreenBackDoesNotDependOnMediaOrSiri() {
        AirPlayPersistence.saveCarPlayWheelMedia(context, false)
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertTrue(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context, flyScreen = true))
        assertArrayEquals(
            intArrayOf(4),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = true, flyScreen = true),
        )
    }

    @Test fun bluetoothAudioLeavesMediaWithCarButAllowsExplicitVoiceCustomization() {
        AirPlayPersistence.saveCarBluetoothAudio(context, true)
        AirPlayPersistence.saveCarPlayWheelMedia(context, true)
        AirPlayPersistence.saveCarPlayWheelSiri(context, true)
        assertTrue(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
        assertArrayEquals(
            intArrayOf(231, 259, 110, 4),
            Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = true, flyScreen = true),
        )
        assertArrayEquals(intArrayOf(231, 259, 110), Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = true))
        AirPlayPersistence.saveCarPlayWheelSiri(context, false)
        assertFalse(Hs5SteeringKeyPolicy.shouldRegisterFactoryKeys(context))
        assertArrayEquals(intArrayOf(4), Hs5SteeringKeyPolicy.factoryKeyCodes(context, carPlayCall = true, flyScreen = true))
    }

    @Test fun onlySystemServiceBackShortClickClosesVisibleFlyScreen() {
        assertTrue(Hs5SteeringKeyPolicy.closesFlyScreen(4, 0, flyScreen = true))
        assertFalse(Hs5SteeringKeyPolicy.closesFlyScreen(4, 1, flyScreen = true))
        assertFalse(Hs5SteeringKeyPolicy.closesFlyScreen(4, 2, flyScreen = true))
        assertFalse(Hs5SteeringKeyPolicy.closesFlyScreen(4, 0, flyScreen = false))
        assertFalse(Hs5SteeringKeyPolicy.closesFlyScreen(9, 0, flyScreen = true))
        assertFalse(Hs5SteeringKeyPolicy.closesFlyScreen(KeyEvent.KEYCODE_ESCAPE, 0, flyScreen = true))
    }
}
