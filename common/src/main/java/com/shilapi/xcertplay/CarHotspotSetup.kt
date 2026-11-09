package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import android.provider.Settings
import android.util.Log
import com.shilapi.xcertplay.adb.AdbKeys
import com.shilapi.xcertplay.adb.LocalAdb
import com.shilapi.xcertplay.hud.Hs5OutputSettings
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.network.CarHotspotSettings
import com.shilapi.xcertplay.network.WirelessStartupPolicy
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

/** Each grant is requested explicitly from settings; startup never calls this authorization path. */
internal object CarHotspotSetup {
    /** True when the current device exposes the verified Hongqi HS5/Qisi vehicle service. */
    fun isHongqiHeadUnit(context: Context): Boolean =
        Hs5HeadUnit.isHeadUnit(context) || Hs5OutputSettings.navigationAvailable(context)

    enum class Permission(val appOp: String) {
        HOTSPOT("WRITE_SETTINGS"), BOOT_LAUNCH("SYSTEM_ALERT_WINDOW");

        fun granted(context: Context): Boolean = when (this) {
            HOTSPOT -> Settings.System.canWrite(context)
            BOOT_LAUNCH -> Settings.canDrawOverlays(context)
        }
    }

    fun check(context: Context, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access = adb.use {
        it.connect(mayAsk = false)
    }

    fun grant(context: Context, permissions: List<Permission>, adb: LocalAdb = LocalAdb(AdbKeys.load(context))): LocalAdb.Access =
        adb.use {
            val access = it.connect(mayAsk = true)
            DiagnosticLogging.logcat { Log.i("DiPlay-ADB", "switch connection: $access") }
            if (access == LocalAdb.Access.READY) {
                for (permission in permissions) {
                    if (!permission.granted(context)) {
                        DiagnosticLogging.logcat { Log.i("DiPlay-ADB", "request permission: ${permission.appOp}") }
                        it.shell("appops set ${context.packageName} ${permission.appOp} allow")
                    }
                    val granted = permission.granted(context)
                    DiagnosticLogging.logcat { Log.i("DiPlay-ADB", "permission ${permission.appOp}: granted=$granted") }
                    if (!granted) break
                }
            }
            access
        }

    fun shouldStartOnLaunch(context: Context, hasSession: Boolean): Boolean {
        if (hasSession) return false
        val mode = AirPlayPersistence.loadWirelessHotspotMode(context)
        if (Hs5HeadUnit.isHeadUnit(context) &&
            WirelessStartupPolicy.hongqiFastBringUp(context, mode)
        ) {
            return false
        }
        return CarHotspotSettings.shouldEnable(context,
            AirPlayPersistence.loadWirelessEnabled(context), mode) &&
            ManualHotspotValidation.error(AirPlayPersistence.loadManualHotspotSsid(context),
                AirPlayPersistence.loadManualHotspotPassphrase(context)) == null
    }
}
