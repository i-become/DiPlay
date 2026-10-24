package com.shilapi.xcertplay

import android.content.Context
import android.os.SystemClock

/** Main-thread router for saved single/double-click preferences. */
internal object VoiceAssistantKeyController {
    private val gesture = VoiceAssistantKeyGesture(clockMillis = { SystemClock.uptimeMillis() })
    private var configuration: Triple<Boolean, VoiceAssistantKeyTarget, VoiceAssistantKeyTarget>? = null
    private var lastFactoryCode: Int? = null
    private var lastFactoryTime = 0L

    fun applySettings(context: Context) {
        val next = Triple(
            AirPlayPersistence.loadCarPlayWheelSiri(context),
            AirPlayPersistence.loadVoiceAssistantKeySingle(context),
            AirPlayPersistence.loadVoiceAssistantKeyDouble(context),
        )
        if (configuration != next) reset()
        configuration = next
    }

    fun reset() {
        gesture.reset()
        lastFactoryCode = null
        lastFactoryTime = 0L
    }

    fun onFactoryVoiceKeyDown(context: Context, keyCode: Int, receivedAt: Long) {
        applySettings(context)
        val config = configuration ?: return
        if (!config.first) return
        // Some firmwares emit both SPEECH and SDS_CAR_LINK for the same physical press.
        if (lastFactoryCode != null && lastFactoryCode != keyCode &&
            receivedAt - lastFactoryTime in 0..80L) {
            Hs5SteeringKeys.recordEvent { "voice alias duplicate key=$keyCode" }
            return
        }
        lastFactoryCode = keyCode
        lastFactoryTime = receivedAt
        gesture.onVoiceKeyDown(
            scheduler = MainVoiceKeyScheduler,
            singleTarget = config.second,
            doubleTarget = config.third,
            launch = { target -> Hs5SteeringKeys.launchVoiceAssistant(target) },
            pressTimeMillis = receivedAt,
        )
    }
}
