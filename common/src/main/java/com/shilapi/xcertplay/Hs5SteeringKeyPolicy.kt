package com.shilapi.xcertplay

import android.content.Context
import android.view.KeyEvent

/** Which QiSi factory hard keys DiPlay may temporarily register for CarPlay. */
internal object Hs5SteeringKeyPolicy {
    /** Back codes taken while the fly screen shows; the engineering key test's "send 4" closes it. */
    val FLY_SCREEN_BACK_CODES = intArrayOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)

    /** The phone key is taken only during a CarPlay call or while CarPlay plays telephony audio. */
    fun factoryKeyCodes(context: Context, carPlayCall: Boolean, flyScreen: Boolean = false): IntArray {
        val codes = LinkedHashSet<Int>()
        if (AirPlayPersistence.loadCarPlayWheelMedia(context)) {
            codes += KeyEvent.KEYCODE_MEDIA_PREVIOUS
            codes += KeyEvent.KEYCODE_MEDIA_NEXT
        }
        if (AirPlayPersistence.loadCarPlayWheelSiri(context)) {
            codes += 259
        }
        if (carPlayCall) codes += KeyEvent.KEYCODE_CALL
        codes += 110
        if (flyScreen) codes += FLY_SCREEN_BACK_CODES.toList()
        return codes.toIntArray()
    }

    fun shouldRegisterFactoryKeys(context: Context): Boolean =
        AirPlayPersistence.loadCarPlayWheelMedia(context) ||
            AirPlayPersistence.loadCarPlayWheelSiri(context)
}
