package com.shilapi.xcertplay

import android.os.Handler
import android.os.Looper

internal interface VoiceKeyScheduler {
    fun postDelayed(delayMillis: Long, runnable: Runnable)

    fun cancelDelayed(runnable: Runnable)
}

internal object MainVoiceKeyScheduler : VoiceKeyScheduler {
    private val handler = Handler(Looper.getMainLooper())

    override fun postDelayed(delayMillis: Long, runnable: Runnable) {
        handler.postDelayed(runnable, delayMillis)
    }

    override fun cancelDelayed(runnable: Runnable) {
        handler.removeCallbacks(runnable)
    }
}
