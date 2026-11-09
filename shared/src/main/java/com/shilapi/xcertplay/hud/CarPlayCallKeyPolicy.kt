package com.shilapi.xcertplay.hud

import android.view.KeyEvent

/** Steering-wheel call keys while a CarPlay call is active on the Hongqi head unit. */
object CarPlayCallKeyPolicy {
    const val KEYCODE_HS5_DIAL_ANSWER = 313
    const val KEYCODE_HS5_HANG_UP = 314
    const val KEYCODE_HS5_MULTIPLEXING = 309

    enum class Action { PASS, CONSUME, ANSWER, END }

    fun isAnswerKey(keyCode: Int): Boolean = keyCode == KEYCODE_HS5_DIAL_ANSWER || keyCode == KeyEvent.KEYCODE_CALL

    fun isEndKey(keyCode: Int): Boolean = keyCode == KeyEvent.KEYCODE_ENDCALL ||
        keyCode == KEYCODE_HS5_HANG_UP || keyCode == KEYCODE_HS5_MULTIPLEXING

    /** A key event while CarPlay runs with [call] on the iPhone. */
    fun onKey(keyCode: Int, down: Boolean, call: CarPlayCallCard?, session: Boolean): Action {
        if (!session || call == null) return Action.PASS
        val ringing = call.phase == CarPlayCallCard.Phase.RINGING
        return when {
            isAnswerKey(keyCode) -> when {
                down -> Action.CONSUME
                ringing -> Action.ANSWER
                else -> Action.CONSUME
            }
            isEndKey(keyCode) -> if (down) Action.CONSUME else Action.END
            ringing && keyCode == KeyEvent.KEYCODE_MEDIA_NEXT -> if (down) Action.CONSUME else Action.ANSWER
            ringing && keyCode == KeyEvent.KEYCODE_MEDIA_PREVIOUS -> if (down) Action.CONSUME else Action.END
            else -> Action.PASS
        }
    }
}
