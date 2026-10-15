package com.shilapi.xcertplay.newui.diagnostics

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.DiPlayPreferences

internal object NewUiDiagnosticReport {
    fun build(context: Context, versionName: String): String = buildString {
        appendLine("DiPlay $versionName · diagnostic report")
        appendLine("Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}")
        appendLine("Head unit: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Connection: ${if (AirPlayPersistence.loadWirelessEnabled(context)) "wireless" else "USB"}")
        appendLine("Phone: ${DiPlayPreferences.phoneName(context)} (${DiPlayPreferences.phoneAddress(context) ?: "none"})")
        appendLine("Cluster map: ${AirPlayPersistence.loadClusterMapEnabled(context)}")
        appendLine(
            "Cluster fly screen default on connect: ${AirPlayPersistence.loadClusterFlyScreenDefaultOnConnect(context)}",
        )
        appendLine("Auto-connect on open: ${DiPlayPreferences.autoConnect(context)}")
        appendLine("Generated from new UI shell")
    }
}
