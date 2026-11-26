package com.shilapi.xcertplay

import android.content.Context

/** Main-thread router for the steering-wheel Siri switch. Legacy gesture mappings are ignored. */
internal object VoiceAssistantKeyController {
    private var enabled = false
    private var lastFactoryCode: Int? = null
    private var lastFactoryTime = 0L

    fun applySettings(context: Context) {
        val next = AirPlayPersistence.loadCarPlayWheelSiri(context)
        if (enabled != next) reset()
        enabled = next
    }

    fun reset() {
        lastFactoryCode = null
        lastFactoryTime = 0L
    }

    fun onFactoryVoiceKeyDown(context: Context, keyCode: Int, receivedAt: Long) {
        applySettings(context)
        if (!enabled) return
        // Some firmwares emit two aliases for the same physical voice-key press.
        if (lastFactoryCode != null && lastFactoryCode != keyCode &&
            receivedAt - lastFactoryTime in 0..80L) {
            Hs5SteeringKeys.recordEvent { "voice alias duplicate key=$keyCode" }
            return
        }
        lastFactoryCode = keyCode
        lastFactoryTime = receivedAt
        Hs5SteeringKeys.launchVoiceAssistant(VoiceAssistantKeyTarget.SIRI)
    }
}
