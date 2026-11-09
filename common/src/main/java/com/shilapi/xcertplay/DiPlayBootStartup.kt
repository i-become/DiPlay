package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.newui.NewUiActivity
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/** Earliest Hongqi boot bring-up: prewarm, then connect directly when setup permits. */
internal object DiPlayBootStartup {
    fun isBootIntent(action: String?): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON" -> true
        else -> false
    }

    fun runPrewarm(app: Context, launchEnabled: Boolean) {
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("prewarm-enter launchEnabled=$launchEnabled") }
        PhoneAudioRoute.attach(app)
        if (!Hs5HeadUnit.isHeadUnit(app)) return
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        if (launchEnabled) {
            // Schedule directly from process/boot entry, before Service or Activity dispatch.
            Hs5BootSession.schedule(app)
            DiPlaySessionService.startWarmup(app)
        }
        Hs5HongqiStartupWarmup.schedule(app)
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("prewarm-return") }
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
            val connecting = DiPlayLauncher.startAutomaticConnection(app, fromBoot = true)
            if (!connecting) app.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(app)
            DiagnosticLogging.logcat { Log.i(TAG, "boot launch ${if (connecting) "CarPlayHostActivity" else "NewUiActivity"}") }
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(app, error)
            DiagnosticLogging.logcat { Log.w(TAG, "Boot auto-start could not launch ${NewUiActivity::class.java.simpleName}", error) }
        }
    }

    private const val TAG = "xcertplay-boot"
}
