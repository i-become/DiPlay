package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5NavigationOutputs
import com.shilapi.xcertplay.hud.Hs5OutputSettings
import com.shilapi.xcertplay.hud.CarPlayCallKeyPolicy
import com.shilapi.xcertplay.orchestration.CarPlayController

/** Handles the Hongqi steering-wheel call keys while a CarPlay call is active. */
internal object CarPlayCallKeys {
    private const val TAG = "DiPlay-CallKeys"

    /** Returns true when the key belongs to a CarPlay call and must not reach the car. */
    fun onKey(context: Context, keyCode: Int, down: Boolean, controller: CarPlayController? = currentController()): Boolean {
        if (!Hs5OutputSettings.carPlayCallControls(context)) return false
        val action = CarPlayCallKeyPolicy.onKey(keyCode, down, Hs5NavigationOutputs.carPlayCall(), controller.hasSession())
        when (action) {
            CarPlayCallKeyPolicy.Action.PASS -> return false
            CarPlayCallKeyPolicy.Action.CONSUME -> Unit
            CarPlayCallKeyPolicy.Action.ANSWER -> {
                val sent = controller?.answerCall() == true
                DiagnosticLogging.logcat { Log.i(TAG, "call key $keyCode: answer CarPlay call sent=$sent") }
            }
            CarPlayCallKeyPolicy.Action.END -> {
                val sent = controller?.endCall() == true
                DiagnosticLogging.logcat { Log.i(TAG, "key $keyCode: end CarPlay call sent=$sent") }
            }
        }
        return true
    }

    private fun currentController(): CarPlayController? = CarPlayBackgroundSession.snapshot()?.controller

    private fun CarPlayController?.hasSession(): Boolean = this?.activeAirPlaySessionToken() != null
}
