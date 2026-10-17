package com.shilapi.xcertplay.newui.diagnostics

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.ClusterMapPresentation
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.Hs5ClusterActivityOutput
import com.shilapi.xcertplay.StartupDiagnosticSnapshot
import com.shilapi.xcertplay.WheelKeyService
import java.io.File
import java.io.RandomAccessFile

internal object NewUiDiagnosticReport {
    private const val LOG_TAIL_BYTES = 192 * 1024L

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
        appendLine(
            "Cluster fly screen window: setting=${AirPlayPersistence.loadHs5ClusterActivityWindow(context)} " +
                "active=${Hs5ClusterActivityOutput.enabled(context)}",
        )
        appendLine("Auto-connect on open: ${DiPlayPreferences.autoConnect(context)}")
        appendLine("Start with the car: ${AirPlayPersistence.loadAutoStartOnBoot(context)}")
        appendLine(StartupDiagnosticSnapshot.report(context))
        appendLine("Generated from new UI shell")
        appendLine()
        appendLine("--- Cluster displays ---")
        appendLine(runCatching { ClusterMapPresentation.diagnosticReport(context) }.getOrElse { "unavailable: ${it.javaClass.simpleName}" })
        appendLine()
        appendLine("--- Cluster fly screen window ---")
        appendLine(Hs5ClusterActivityOutput.diagnosticReport())
        appendLine()
        appendLine("--- Steering keys ---")
        appendLine(WheelKeyService.report(context))
        appendLine()
        appendLine("--- diplay.log ---")
        append(logTail(File(File(context.filesDir, "logs"), "diplay.log")))
    }

    private fun logTail(file: File): String = runCatching {
        if (!file.isFile) return@runCatching "no session log yet"
        RandomAccessFile(file, "r").use { raf ->
            val start = (raf.length() - LOG_TAIL_BYTES).coerceAtLeast(0L)
            raf.seek(start)
            val bytes = ByteArray((raf.length() - start).toInt())
            raf.readFully(bytes)
            val text = String(bytes, Charsets.UTF_8)
            if (start == 0L) text else text.substringAfter('\n')
        }
    }.getOrElse { "unavailable: ${it.javaClass.simpleName}" }
}
