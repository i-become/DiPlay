package com.shilapi.xcertplay

import android.view.KeyEvent
import android.view.ViewConfiguration

/** Android key route: distinguish a NEXT tap from a hold without sending an unwanted skip first. */
internal class NextTrackPress {
    private var press: Pair<Int, Long>? = null
    private var held = false

    fun dispatch(event: KeyEvent, next: () -> Unit, toggle: () -> Unit): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_MEDIA_NEXT) return false
        val identity = event.deviceId to event.downTime
        val long = event.isLongPress || event.eventTime - event.downTime >= ViewConfiguration.getLongPressTimeout()
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) { press = identity; held = false }
                if (press == identity && !held && long && !event.isCanceled) {
                    held = true
                    toggle()
                }
            }
            KeyEvent.ACTION_UP -> if (press == identity) {
                if (!event.isCanceled && !held) { if (long) toggle() else next() }
                press = null
                held = false
            }
        }
        return true
    }
}
