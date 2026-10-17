package com.shilapi.xcertplay.media

import com.shilapi.xcertplay.hud.BydCarPlayCall

/**
 * Whether the iPhone plays call-like CarPlay audio. Cellular calls use [TELEPHONY]; WeChat VoIP on
 * recent iOS often uses [SPEECH_RECOGNITION] playback and uplink instead, without iAP2 call state.
 */
object CarPlayTelephonyAudio {
    const val TELEPHONY = "telephony"
    const val SPEECH_RECOGNITION = "speechrecognition"
    const val ALERT = "alert"

    fun tracksCallLikeAudio(audioType: String): Boolean =
        audioType == TELEPHONY || audioType == SPEECH_RECOGNITION || audioType == ALERT

    private val streamTypes = HashMap<Any, String>()

    /** Called whenever call-like audio starts or stops, on the audio thread. */
    @Volatile var changed: ((Boolean) -> Unit)? = null

    /**
     * When call-like audio starts but iAP2 has not published [BydCarPlayCall] yet (common for WeChat
     * ringtone-only phases), the controller may re-send Communications subscriptions so the iPhone
     * promotes the CarPlay call UI.
     */
    @Volatile var communicationsRefreshRequest: (() -> Unit)? = null

    fun active(): Boolean = synchronized(streamTypes) { streamTypes.isNotEmpty() }

    /** WeChat-style VoIP: speechrecognition without a telephony downlink stream. */
    fun speechOnlyCallActive(): Boolean = synchronized(streamTypes) {
        streamTypes.values.none { it == TELEPHONY } &&
            streamTypes.values.any { it == SPEECH_RECOGNITION }
    }

    fun speechRecognitionActive(): Boolean = synchronized(streamTypes) {
        streamTypes.values.any { it == SPEECH_RECOGNITION }
    }

    fun telephonyActive(): Boolean = synchronized(streamTypes) {
        streamTypes.values.any { it == TELEPHONY }
    }

    /** Wheel / iAP AcceptCall path: WeChat often mixes telephony, alert ring, and speech streams. */
    fun voipAcceptUsesIap(): Boolean = synchronized(streamTypes) {
        val types = streamTypes.values
        types.any { it == SPEECH_RECOGNITION || it == ALERT } || speechOnlyCallActive()
    }

    fun started(stream: Any, audioType: String) {
        if (!tracksCallLikeAudio(audioType)) return
        update { streamTypes[stream] = audioType }
    }

    fun stopped(stream: Any) = update { streamTypes.remove(stream) }

    fun clear() = update { streamTypes.clear() }

    private fun update(change: () -> Unit) {
        val (before, after) = synchronized(streamTypes) {
            val before = streamTypes.isNotEmpty()
            change()
            before to streamTypes.isNotEmpty()
        }
        if (before != after) {
            changed?.invoke(after)
            if (after && BydCarPlayCall.current() == null) {
                communicationsRefreshRequest?.invoke()
            }
        }
    }
}
