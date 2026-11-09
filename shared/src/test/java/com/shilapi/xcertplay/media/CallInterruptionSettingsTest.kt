package com.shilapi.xcertplay.media

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class CallInterruptionSettingsTest {
    @Test fun defaultsOnAndOnlyPreservesMusicDuringAnActiveCall() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay_call_audio", 0).edit().clear().commit()
        CarPlayTelephonyAudio.clear()
        assertTrue(CallInterruptionSettings.enabled(app))
        CallInterruptionSettings.save(app, false)
        assertFalse(CallInterruptionSettings.enabled(app))
        assertFalse(CallInterruptionSettings.keepMusic(app))
        val id = com.shilapi.xcertplay.airplay.AudioStreamId(77, "telephony")
        try {
            CarPlayTelephonyAudio.started(id, "telephony")
            assertTrue(CallInterruptionSettings.keepMusic(app))
            CallInterruptionSettings.save(app, true)
            assertFalse(CallInterruptionSettings.keepMusic(app))
        } finally {
            CarPlayTelephonyAudio.clear()
            app.getSharedPreferences("diplay_call_audio", 0).edit().clear().commit()
        }
    }
}
