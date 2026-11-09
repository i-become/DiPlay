package com.shilapi.xcertplay.airplay

import android.view.KeyEvent

/**
 * Hardware media keys → CarPlay media HID presses (indices into [AirPlayHid]'s media report).
 *
 * Hardware play and pause keys both map to the toggle because the head unit picks PLAY or PAUSE from
 * its own idea of the play state, and a wrong guess would make the button do nothing. Explicit play and pause
 * commands from media controllers use [PLAY] and [PAUSE].
 */
object CarPlayMediaButton {
    const val PLAY = 1
    const val PAUSE = 2
    const val PLAY_PAUSE = 3
    const val NEXT = 4
    const val PREVIOUS = 5

    /** HS5 steering-wheel play/pause key; the firmware normally rewrites it to MEDIA_PLAY/PAUSE. */
    const val KEYCODE_HS5_AUTO_MEDIA_PLAY_PAUSE = 353

    /** HS5 steering-wheel voice key: a short press, and the code the wheel sends for a long press. */
    const val KEYCODE_HS5_AUTO_MEDIA_VOICE = 304
    const val KEYCODE_HS5_AUTO_MEDIA_VOICE_LONG = 312

    /**
     * HS5's CarPlay voice key, short and long, delivered only while CarPlay is active.
     */
    const val KEYCODE_HS5_CARPLAY_VOICE = 327
    const val KEYCODE_HS5_CARPLAY_VOICE_LONG = 328

    /**
     * Whether [keyCode] is a voice key that opens Siri. The HS5 wheel sends each press as an
     * instant down/up pair, so a long press arrives as its own key rather than as a held one.
     */
    fun opensSiri(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_VOICE_ASSIST ||
        keyCode == KEYCODE_HS5_AUTO_MEDIA_VOICE || keyCode == KEYCODE_HS5_AUTO_MEDIA_VOICE_LONG

    /** The voice keys sent only for CarPlay; they open Siri while a CarPlay session runs. */
    fun opensSiriWhileCarPlay(keyCode: Int): Boolean =
        keyCode == KEYCODE_HS5_CARPLAY_VOICE || keyCode == KEYCODE_HS5_CARPLAY_VOICE_LONG

    /** The CarPlay press for [keyCode], or null when the key is not a media key CarPlay handles. */
    fun forKeyCode(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PREVIOUS
        KeyEvent.KEYCODE_MEDIA_PLAY,
        KeyEvent.KEYCODE_MEDIA_PAUSE,
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
        KeyEvent.KEYCODE_HEADSETHOOK,
        KEYCODE_HS5_AUTO_MEDIA_PLAY_PAUSE -> PLAY_PAUSE
        else -> null
    }

    /**
     * The CarPlay press the CarPlay screen itself should claim for [keyCode], or null to let the key
     * go on to the car.
     *
     * The head unit hands the steering wheel's next/previous keys to whichever window is in front, and DiPlay's
     * CarPlay screen is that window whenever the driver is looking at it. Claiming them here is what
     * makes the wheel change tracks without the accessibility service, which a head unit with no ADB
     * cannot turn on.
     *
     * Only next and previous are claimed. Play/pause is left alone because the head unit acts on it itself, and the
     * wheel key service owns it. Nothing is claimed outside a CarPlay session, or during
     * a call, where the car's own call handling is what the driver expects from these keys.
     */
    fun forCarPlayWindow(
        keyCode: Int,
        sessionActive: Boolean,
        inCall: Boolean,
    ): Int? {
        if (!sessionActive || inCall) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_MEDIA_NEXT -> NEXT
            KeyEvent.KEYCODE_MEDIA_PREVIOUS -> PREVIOUS
            else -> null
        }
    }
}
