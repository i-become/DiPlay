// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.compat.closeCompat
import com.shilapi.xcertplay.host.R

internal object WirelessRecovery {
    fun showResetConfirm(activity: ComponentActivity, connectWireless: () -> Unit) {
        activity.appDialogBuilder()
            .setTitle(activity.getString(R.string.reset_carplay_wi_fi_2))
            .setMessage(activity.getString(R.string.this_ends_the_existing_wi_fi_direct_connection_including_o))
            .setPositiveButton(activity.getString(R.string.reset_and_connect)) { _, _ ->
                CarPlayBackgroundSession.stop {
                    activity.runOnUiThread { resetWirelessGroup(activity, connectWireless) }
                }
            }
            .setNegativeButton(activity.getString(R.string.cancel), null)
            .show()
    }

    private fun resetWirelessGroup(activity: ComponentActivity, connectWireless: () -> Unit) {
        val manager = activity.getSystemService(android.net.wifi.p2p.WifiP2pManager::class.java)
        if (manager == null) {
            toast(activity, activity.getString(R.string.this_head_unit_does_not_support_wi_fi_direct))
            return
        }
        val handler = Handler(Looper.getMainLooper())
        val channel = manager.initialize(activity, activity.mainLooper, null)
        try {
            manager.requestGroupInfo(channel) { group ->
                if (group == null) {
                    channel.closeCompat()
                    connectWireless()
                    return@requestGroupInfo
                }
                manager.removeGroup(channel, object : android.net.wifi.p2p.WifiP2pManager.ActionListener {
                    override fun onSuccess() {
                        val deadline = android.os.SystemClock.elapsedRealtime() + 4000
                        fun waitUntilRemoved() {
                            manager.requestGroupInfo(channel) { remaining ->
                                when {
                                    remaining == null -> {
                                        channel.closeCompat()
                                        if (!activity.isFinishing && !activity.isDestroyed) connectWireless()
                                    }
                                    android.os.SystemClock.elapsedRealtime() >= deadline -> {
                                        channel.closeCompat()
                                        toast(
                                            activity,
                                            activity.getString(R.string.wi_fi_direct_is_still_busy_close_the_other_projection_app),
                                        )
                                    }
                                    else -> handler.postDelayed({ waitUntilRemoved() }, 200)
                                }
                            }
                        }
                        waitUntilRemoved()
                    }

                    override fun onFailure(reason: Int) {
                        channel.closeCompat()
                        toast(
                            activity,
                            activity.getString(R.string.could_not_reset_wi_fi_direct_close_the_other_projection_ap),
                        )
                    }
                })
            }
        } catch (_: SecurityException) {
            channel.closeCompat()
            activity.appDialogBuilder()
                .setTitle(activity.getString(R.string.wireless_permissions))
                .setMessage(activity.getString(R.string.allow_nearby_devices_and_on_older_android_versions_locatio))
                .setPositiveButton(activity.getString(R.string.app_settings)) { _, _ ->
                    runCatching {
                        activity.startActivity(
                            Intent(
                                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:${activity.packageName}"),
                            ),
                        )
                    }.onFailure {
                        toast(activity, activity.getString(R.string.open_this_setting_from_your_car_s_settings_app))
                    }
                }
                .setNegativeButton(activity.getString(R.string.later), null)
                .show()
        }
    }

    private fun toast(activity: ComponentActivity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
    }
}
