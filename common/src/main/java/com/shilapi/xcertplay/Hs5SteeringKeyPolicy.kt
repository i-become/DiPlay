package com.shilapi.xcertplay

import android.content.Context
import android.view.KeyEvent

/** Which QiSi factory hard keys DiPlay may temporarily register for CarPlay. */
internal object Hs5SteeringKeyPolicy {
    fun factoryKeyCodes(context: Context): IntArray {
        val codes = mutableListOf<Int>()
        if (AirPlayPersistence.loadCarPlayWheelMedia(context)) {
            codes += KeyEvent.KEYCODE_MEDIA_PREVIOUS
            codes += KeyEvent.KEYCODE_MEDIA_NEXT
        }
        if (AirPlayPersistence.loadCarPlayWheelSiri(context)) {
            codes += 259
        }
        codes += KeyEvent.KEYCODE_CALL
        codes += 110
        return codes.toIntArray()
    }

    fun shouldRegisterFactoryKeys(context: Context): Boolean =
        AirPlayPersistence.loadCarPlayWheelMedia(context) ||
            AirPlayPersistence.loadCarPlayWheelSiri(context)
}
