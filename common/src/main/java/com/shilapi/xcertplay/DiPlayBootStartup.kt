package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.content.Intent
import android.util.Log
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.newui.NewUiActivity
import com.shilapi.xcertplay.newui.ClusterOnlyBootActivity
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/** Earliest Hongqi boot bring-up: start the headless connection path before selecting the UI. */
internal object DiPlayBootStartup {
    fun isBootIntent(action: String?): Boolean = when (action) {
        Intent.ACTION_BOOT_COMPLETED,
        "android.intent.action.QUICKBOOT_POWERON",
        "com.htc.intent.action.QUICKBOOT_POWERON" -> true
        else -> false
    }

    fun runPrewarm(app: Context, launchEnabled: Boolean) {
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("prewarm-enter launchEnabled=$launchEnabled") }
        if (!Hs5HeadUnit.isHeadUnit(app)) {
            PhoneAudioRoute.attach(app)
            return
        }
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        // Independent radio and authentication workers must not wait for OEM audio setup.
        Hs5HongqiStartupWarmup.schedule(app)
        if (launchEnabled) {
            // Schedule directly from process/boot entry, before Service or Activity dispatch.
            Hs5BootSession.schedule(app)
            DiPlaySessionService.startWarmup(app)
        }
        PhoneAudioRoute.attach(app)
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("prewarm-return") }
    }

    fun launchUi(app: Context, onComplete: () -> Unit = {}) {
        val clusterOnly = AirPlayPersistence.loadClusterOnlyMode(app)
        val activity = if (clusterOnly) ClusterOnlyBootActivity::class.java else NewUiActivity::class.java
        fun launch() = Intent(app, activity).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            if (clusterOnly) {
                putExtra(NewUiActivity.EXTRA_CLUSTER_ONLY_BOOT, true)
            }
        }
        Thread({
            try {
                // Boot prewarm owns the headless connection attempt. Launch only the selected UI
                // here: it can inspect the live session immediately, or render Home while the
                // connection continues in the service without an Activity competing for startup.
                app.startActivity(launch())
                DiagnosticLogging.logcat {
                    Log.i(TAG, "boot launch ${activity.simpleName} clusterOnly=$clusterOnly " +
                        "sessionActive=${CarPlayBackgroundSession.active}")
                }
                StartupDiagnosticSnapshot.launchResult(app)
            } catch (error: RuntimeException) {
                StartupDiagnosticSnapshot.launchResult(app, error)
                DiagnosticLogging.logcat { Log.w(TAG, "Boot auto-start could not launch ${NewUiActivity::class.java.simpleName}", error) }
            } finally {
                onComplete()
            }
        }, "diplay-boot-activity-launch").apply {
            isDaemon = true
            start()
        }
    }

    private const val TAG = "xcertplay-boot"
}
