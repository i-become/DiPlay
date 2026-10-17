package com.shilapi.xcertplay.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarPlayTelephonyAudioTest {
    @Test fun weChatSpeechRecognitionCountsAsCallLike() {
        assertTrue(CarPlayTelephonyAudio.tracksCallLikeAudio(CarPlayTelephonyAudio.SPEECH_RECOGNITION))
        assertTrue(CarPlayTelephonyAudio.tracksCallLikeAudio(CarPlayTelephonyAudio.TELEPHONY))
        assertTrue(CarPlayTelephonyAudio.tracksCallLikeAudio(CarPlayTelephonyAudio.ALERT))
        assertFalse(CarPlayTelephonyAudio.tracksCallLikeAudio("media"))
    }

    @Test fun activeWhileAnyTrackedStreamRuns() {
        CarPlayTelephonyAudio.clear()
        val stream = Any()
        assertFalse(CarPlayTelephonyAudio.active())
        CarPlayTelephonyAudio.started(stream, CarPlayTelephonyAudio.SPEECH_RECOGNITION)
        assertTrue(CarPlayTelephonyAudio.active())
        assertTrue(CarPlayTelephonyAudio.speechOnlyCallActive())
        CarPlayTelephonyAudio.stopped(stream)
        assertFalse(CarPlayTelephonyAudio.active())
    }

    @Test fun telephonyClearsSpeechOnlyFlag() {
        CarPlayTelephonyAudio.clear()
        val speech = Any()
        val phone = Any()
        CarPlayTelephonyAudio.started(speech, CarPlayTelephonyAudio.SPEECH_RECOGNITION)
        assertTrue(CarPlayTelephonyAudio.speechOnlyCallActive())
        CarPlayTelephonyAudio.started(phone, CarPlayTelephonyAudio.TELEPHONY)
        assertFalse(CarPlayTelephonyAudio.speechOnlyCallActive())
    }

    @Test fun alertRingEnablesIapVoipAccept() {
        CarPlayTelephonyAudio.clear()
        val ring = Any()
        CarPlayTelephonyAudio.started(ring, CarPlayTelephonyAudio.ALERT)
        assertTrue(CarPlayTelephonyAudio.active())
        assertTrue(CarPlayTelephonyAudio.voipAcceptUsesIap())
        assertFalse(CarPlayTelephonyAudio.telephonyActive())
    }
}
