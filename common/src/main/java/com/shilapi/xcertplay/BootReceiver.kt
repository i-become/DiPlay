package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
/** Starts DiPlay after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!DiPlayBootStartup.isBootIntent(intent.action)) return
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("boot-delivery action=${intent.action}") }
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        val app = context.applicationContext
        DiPlayBootStartup.runPrewarm(app, launchEnabled)
        if (!launchEnabled) return

        val pending = goAsync()
        DiPlayBootStartup.launchUi(app, onComplete = pending::finish)
    }
}
