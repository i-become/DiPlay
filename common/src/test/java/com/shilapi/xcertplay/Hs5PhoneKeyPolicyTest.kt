package com.shilapi.xcertplay

import com.shilapi.xcertplay.Hs5SteeringKeys.Hs5PhoneKeyPolicy
import com.shilapi.xcertplay.Hs5SteeringKeys.Hs5PhoneKeyPolicy.Action
import com.shilapi.xcertplay.hud.CarPlayCallCard.Phase
import org.junit.Assert.assertEquals
import org.junit.Test

class Hs5PhoneKeyPolicyTest {
    @Test fun weChatSpeechUsesIapAcceptOnRingingAndActive() {
        assertEquals(
            Action.VOIP_SPEECH_ANSWER,
            Hs5PhoneKeyPolicy.onPhoneKey(0, Phase.RINGING, held = false, voipAudio = true, iapVoipAccept = false, weChatSpeechMix = true),
        )
        assertEquals(
            Action.VOIP_SPEECH_ANSWER,
            Hs5PhoneKeyPolicy.onPhoneKey(0, Phase.ACTIVE, held = false, voipAudio = true, iapVoipAccept = false, weChatSpeechMix = true),
        )
        assertEquals(
            Action.END,
            Hs5PhoneKeyPolicy.onPhoneKey(0, Phase.DIALING, held = false, voipAudio = false, iapVoipAccept = false, weChatSpeechMix = false),
        )
    }

    @Test fun noIapPhaseUsesIapWhenAlertOrSpeech() {
        assertEquals(
            Action.VOIP_SPEECH_ANSWER,
            Hs5PhoneKeyPolicy.onPhoneKey(0, null, held = false, voipAudio = true, iapVoipAccept = true, weChatSpeechMix = false),
        )
        assertEquals(
            Action.VOIP_SPEECH_ANSWER,
            Hs5PhoneKeyPolicy.onPhoneKey(0, null, held = false, voipAudio = true, iapVoipAccept = false, weChatSpeechMix = false),
        )
        assertEquals(
            Action.NONE,
            Hs5PhoneKeyPolicy.onPhoneKey(0, null, held = false, voipAudio = false, iapVoipAccept = false, weChatSpeechMix = false),
        )
        assertEquals(
            Action.VOIP_END,
            Hs5PhoneKeyPolicy.onPhoneKey(1, null, held = false, voipAudio = true, iapVoipAccept = true, weChatSpeechMix = false),
        )
    }

    @Test fun cellularRingingStillUsesHookAnswer() {
        assertEquals(
            Action.ANSWER,
            Hs5PhoneKeyPolicy.onPhoneKey(0, Phase.RINGING, held = false, voipAudio = false, iapVoipAccept = false, weChatSpeechMix = false),
        )
        assertEquals(
            Action.END,
            Hs5PhoneKeyPolicy.onPhoneKey(1, Phase.RINGING, held = false, voipAudio = false, iapVoipAccept = false, weChatSpeechMix = false),
        )
        assertEquals(
            Action.NONE,
            Hs5PhoneKeyPolicy.onPhoneKey(2, Phase.RINGING, held = true, voipAudio = false, iapVoipAccept = false, weChatSpeechMix = false),
        )
    }
}
