package com.shilapi.xcertplay

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.telecom.PhoneAudioRoute

/** Starts the CarPlay host after boot when the user has enabled the startup option. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // Starts the watcher for other apps' calls when that setting is on, even without opening DiPlay.
        PhoneAudioRoute.attach(context)
        val launchEnabled = AirPlayPersistence.loadAutoStartOnBoot(context)
        StartupDiagnosticSnapshot.received(context, launchEnabled)
        val app = context.applicationContext
        val hongqi = Hs5HeadUnit.isHeadUnit(app)
        if (hongqi) Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        if (hongqi && launchEnabled) DiPlaySessionService.startWarmup(app)
        Hs5HongqiStartupWarmup.schedule(context)
        if (!launchEnabled) return

        val pending = goAsync()
        Thread({
            try {
                launch(app, directCarPlay(app, hongqi))
            } finally {
                pending?.finish()
            }
        }, "diplay-boot-launch").start()
    }

    private fun directCarPlay(context: Context, hongqi: Boolean): Boolean {
        if (!hongqi) return false
        val wireless = DiPlayPreferences.autoConnectWireless(context)
        val direct = Hs5BootLaunchPolicy.opensCarPlayDirectly(
            hongqi = true,
            autoConnect = DiPlayPreferences.autoConnect(context),
            phoneAddress = DiPlayPreferences.phoneAddress(context),
            wireless = wireless,
            hotspotMode = AirPlayPersistence.loadWirelessHotspotMode(context),
            notificationsAllowed = Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED,
            sessionRunning = CarPlayBackgroundSession.hasSession(),
            setupReady = runCatching {
                DiPlayBootstrap.ensure(context, AirPlayPersistence.loadMfiTarget(context))
            }.isSuccess,
        )
        if (direct) AirPlayPersistence.saveWirelessEnabled(context, wireless)
        return direct
    }

    private fun launch(context: Context, directCarPlay: Boolean) {
        val target = if (directCarPlay) CarPlayHostActivity::class.java else com.shilapi.xcertplay.newui.NewUiActivity::class.java
        val launch = Intent(context, target).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT,
            )
            if (directCarPlay) putExtra(EXTRA_BOOT_AUTO_START, true)
        }
        try {
            context.startActivity(launch)
            StartupDiagnosticSnapshot.launchResult(context)
            Log.i(TAG, "boot launch ${target.simpleName} direct=$directCarPlay")
        } catch (error: RuntimeException) {
            StartupDiagnosticSnapshot.launchResult(context, error)
            Log.w(TAG, "Boot auto-start could not launch ${target.simpleName}", error)
        }
    }

    private companion object {
        const val TAG = "xcertplay-boot"
    }
}
