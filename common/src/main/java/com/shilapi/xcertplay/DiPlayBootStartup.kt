package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.newui.NewUiActivity
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/** Earliest Hongqi boot bring-up: prewarm before [BootReceiver], then open the New UI shell. */
internal object DiPlayBootStartup {
    fun isBootIntent(action: String?): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON" -> true
        else -> false
    }

    fun runPrewarm(app: Context, launchEnabled: Boolean) {
        PhoneAudioRoute.attach(app)
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        if (launchEnabled) {
            DiPlaySessionService.startWarmup(app)
        }
        Hs5HongqiStartupWarmup.schedule(app)
    }

    fun launchUi(app: Context) {
        val launch = Intent(app, NewUiActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
        }
        try {
            app.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(app)
            Log.i(TAG, "boot launch ${NewUiActivity::class.java.simpleName}")
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(app, error)
            Log.w(TAG, "Boot auto-start could not launch ${NewUiActivity::class.java.simpleName}", error)
        }
    }

    private const val TAG = "xcertplay-boot"
}
