package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/** Uses the head unit's routing metadata when its hidden OEM factory is available. */
internal object HongqiAudioAttributes {
    private const val CARPLAY_NAVIGATION_USAGE = 19
    fun navigationOrNull(context: Context?): AudioAttributes? {
        if (context == null || !Hs5HeadUnit.isHeadUnit(context)) return null
        return runCatching {
            val factory = Class.forName("android.car.media.CarAudioAttribute")
                .getMethod("createPlayAudioAttributes", Int::class.javaPrimitiveType)
            factory.invoke(null, CARPLAY_NAVIGATION_USAGE) as? AudioAttributes
        }.getOrNull()
    }
}
