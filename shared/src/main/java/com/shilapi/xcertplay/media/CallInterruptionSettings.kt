package com.shilapi.xcertplay.media

import android.content.Context
import com.shilapi.xcertplay.hud.Hs5NavigationOutputs

/** Local routing policy; the iPhone still controls whether music is transmitted. */
object CallInterruptionSettings {
    private const val PREFS = "diplay_call_audio"
    private const val KEY = "calls_interrupt_music"
    fun enabled(context: Context?): Boolean = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        ?.getBoolean(KEY, true) ?: true
    fun save(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, enabled).apply()
    }
    fun keepMusic(context: Context?): Boolean = !enabled(context) &&
        (CarPlayTelephonyAudio.active() || Hs5NavigationOutputs.carPlayCall() != null)
}
