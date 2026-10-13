package com.shilapi.xcertplay.newui.connection

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.host.R

internal object NewUiConnectionSettingsIntents {
    fun openCarHotspotSettings(activity: ComponentActivity) {
        val hotspot = Intent("com.android.settings.WIFI_TETHER_SETTINGS")
        val target = activity.packageManager.resolveActivity(hotspot, 0)?.activityInfo?.packageName
        if (target == null) {
            openSystem(activity, Intent(Settings.ACTION_WIRELESS_SETTINGS))
            return
        }
        if (target == "com.byd.carsettings") {
            runCatching { activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        }
        if (runCatching { activity.startActivity(hotspot) }.isSuccess) return
        openSystem(activity, Intent(Settings.ACTION_WIRELESS_SETTINGS))
    }

    fun openCarWifiSettings(activity: ComponentActivity) {
        val wifi = Intent(Settings.ACTION_WIFI_SETTINGS)
        if (activity.packageManager.resolveActivity(wifi, 0)?.activityInfo?.packageName == "com.byd.carsettings") {
            runCatching { activity.startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
        }
        openSystem(activity, wifi)
    }

    private fun openSystem(activity: ComponentActivity, intent: Intent) {
        runCatching { activity.startActivity(intent) }
            .onFailure {
                Toast.makeText(
                    activity,
                    R.string.open_this_setting_from_your_car_s_settings_app,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }
}
