// SPDX-License-Identifier: AGPL-3.0-only

package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.Activity
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.shilapi.xcertplay.hud.Hs5HeadUnit

internal object DiPlayLauncher {
    /** Start the configured Hongqi connection before constructing either full UI. */
    fun startAutomaticConnection(context: Context, fromBoot: Boolean = false): Boolean {
        if (AirPlayPersistence.loadClusterOnlyMode(context)) return false
        if (DiPlayPreferences.phoneLinkMode(context) == DiPlayPreferences.PhoneLinkMode.CARLINK) return false
        if (ConnectionProgressUiState.disconnected() || HongqiScreenStatus.screenOff) return false
        if (Hs5BootSession.isPreparing()) {
            val app = context.applicationContext
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                startAutomaticConnection(app, fromBoot)
            }, 50L)
            return true
        }
        val diagnosticStarted = android.os.SystemClock.elapsedRealtime()
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("automatic-launch enter fromBoot=$fromBoot") }
        val wireless = DiPlayPreferences.autoConnectWireless(context)
        if (!Hs5BootLaunchPolicy.opensCarPlayDirectly(
                hongqi = Hs5HeadUnit.isHeadUnit(context),
                autoConnect = DiPlayPreferences.autoConnect(context),
                phoneAddress = DiPlayPreferences.phoneAddress(context),
                wireless = wireless,
                hotspotMode = AirPlayPersistence.loadWirelessHotspotMode(context),
                notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED,
                // A boot service may already be connecting. The Activity adopts that controller.
                sessionRunning = CarPlayBackgroundSession.hasSession() && !CarPlayBackgroundSession.isOwner(Hs5BootSession),
                setupReady = true,
            )) {
            DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("automatic-launch result=policy_skipped elapsedMs=${android.os.SystemClock.elapsedRealtime() - diagnosticStarted}") }
            return false
        }
        if (!CarPlayBackgroundSession.hasSession() && runCatching {
                DiPlayBootstrap.ensure(context, AirPlayPersistence.loadMfiTarget(context))
            }.onFailure {
                DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("automatic-launch result=bootstrap_failed failureClass=${it.javaClass.simpleName}") }
            }.isFailure) return false
        AirPlayPersistence.saveWirelessEnabled(context, wireless)
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("automatic-launch request=CarPlayHostActivity wireless=$wireless " +
            "elapsedMs=${android.os.SystemClock.elapsedRealtime() - diagnosticStarted}") }
        context.startActivity(
            Intent(context, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_NEW_UI_CONNECTION_HUB, true)
                .putExtra(EXTRA_BOOT_AUTO_START, fromBoot)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
        (context as? Activity)?.overridePendingTransition(0, 0)
        DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("automatic-launch result=startActivity_returned elapsedMs=${android.os.SystemClock.elapsedRealtime() - diagnosticStarted}") }
        return true
    }

    fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            !intent.hasExtra(EXTRA_PAGE)

    fun openCarPlayProjection(context: Context) {
        context.startActivity(
            Intent(context, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_ENTER_CARPLAY_PROJECTION, true)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        (context as? Activity)?.overridePendingTransition(0, 0)
    }

    /** Starts [CarPlayHostActivity] for connection while [NewUiActivity] stays visible on top. */
    fun startConnectionHub(activity: Activity) {
        activity.startActivity(
            Intent(activity, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_NEW_UI_CONNECTION_HUB, true)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        activity.overridePendingTransition(0, 0)
    }

    /** Start or continue bring-up under [CarPlayHostActivity] while keeping [NewUiActivity] in front. */
    const val EXTRA_NEW_UI_CONNECTION_HUB = "com.shihab.diplay.NEW_UI_CONNECTION_HUB"

    /** Open the video surface; do not stay on the host waiting/preparation screen. */
    const val EXTRA_ENTER_CARPLAY_PROJECTION = "com.shihab.diplay.ENTER_CARPLAY_PROJECTION"

    const val EXTRA_PAGE = "page"
}
