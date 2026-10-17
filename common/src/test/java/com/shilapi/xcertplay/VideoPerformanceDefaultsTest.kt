package com.shilapi.xcertplay

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class VideoPerformanceDefaultsTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun videoPerformanceTogglesDefaultOnForFreshInstall() {
        assertTrue(AirPlayPersistence.loadSmoothVideo(context))
        assertTrue(AirPlayPersistence.loadDirectVideoOutput(context))
        assertTrue(AirPlayPersistence.loadLowLatencyDecoder(context))
        assertTrue(AirPlayPersistence.loadMtkDecoderTuning(context))
        assertFalse(AirPlayPersistence.loadFpsCounter(context))
    }

    @Test fun savedVideoPerformanceChoiceIsRespected() {
        AirPlayPersistence.saveSmoothVideo(context, false)
        assertFalse(AirPlayPersistence.loadSmoothVideo(context))
    }
}
