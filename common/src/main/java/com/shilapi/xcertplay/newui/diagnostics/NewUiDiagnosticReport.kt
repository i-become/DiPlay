package com.shilapi.xcertplay.newui.diagnostics

import android.content.Context
import android.os.Build
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayStartupDiagnostics
import com.shilapi.xcertplay.ClusterMapPresentation
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.Hs5ClusterActivityOutput
import com.shilapi.xcertplay.HongqiPositionLights
import com.shilapi.xcertplay.HongqiClusterVehicleData
import com.shilapi.xcertplay.CarPlayNightModePolicy
import com.shilapi.xcertplay.StartupDiagnosticSnapshot
import com.shilapi.xcertplay.WheelKeyService
import com.shilapi.xcertplay.Hs5SteeringKeys
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateDiagnostics
import java.io.File
import java.io.RandomAccessFile

internal object NewUiDiagnosticReport {
    private const val LOG_TAIL_BYTES = 192 * 1024L

    fun build(context: Context, versionName: String): String {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return "Session log recording is disabled."
        return buildString {
        appendLine("DiPlay $versionName · diagnostic report")
        appendLine("Application: ${context.packageName}")
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
        appendLine("Export wallEpochMs=${System.currentTimeMillis()} elapsedMs=${android.os.SystemClock.elapsedRealtime()} " +
            "uptimeMs=${android.os.SystemClock.uptimeMillis()}")
        appendLine()
        appendLine("--- Startup timeline (current process; independent of UI/session log) ---")
        appendLine(CarPlayStartupDiagnostics.timelineReport())
        appendLine("Evidence: enable accepted is a request result; broadcasts and API reads are separate observations.")
        appendLine("Bluetooth state: 10=OFF 11=TURNING_ON 12=ON 13=TURNING_OFF; sameAdapter=true means both API paths share an object.")
        appendLine("Coverage: passive radio sampling for 180s; key events for 600s; no observations before the app process starts.")
        appendLine("Wi-Fi P2P legacy firmware may not expose phone association; first TCP is the end-to-end reachability evidence.")
        appendLine()
        appendLine("--- Startup timeline (previous process; may belong to an earlier boot) ---")
        appendLine(CarPlayStartupDiagnostics.previousTraceReport())
        appendLine()
        appendLine("--- Boot Bluetooth events ---")
        appendLine(CarPlayStartupDiagnostics.eventReport())
        appendLine()
        appendLine("--- Online update ---")
        appendLine(UpdateAvailability.report(context, System.currentTimeMillis()))
        appendLine(UpdateDiagnostics.report(context))
        appendLine()
        appendLine("--- Day/night and QiSi position lights ---")
        appendLine("CarPlay mode=${AirPlayPersistence.loadCarPlayNightMode(context).key} " +
            "appAppearance=${AirPlayPersistence.loadAppAppearance(context).key} " +
            "resolvedWithoutHostNight=${CarPlayNightModePolicy.effectiveNight(context)}")
        appendLine("Automatic priority: position lights (0=day, 1=night), ambient sensor, Android theme. Fixed/scheduled modes override lights.")
        appendLine(HongqiPositionLights.report())
        appendLine("--- HS5 instrument vehicle data ---")
        appendLine(HongqiClusterVehicleData.report())
        appendLine()
        appendLine("--- Cluster displays ---")
        appendLine(runCatching { ClusterMapPresentation.diagnosticReport(context) }.getOrElse { "unavailable: ${it.javaClass.simpleName}" })
        appendLine()
        appendLine("--- Cluster fly screen window ---")
        appendLine(Hs5ClusterActivityOutput.diagnosticReport())
        appendLine(com.shilapi.xcertplay.hud.BydNavigationOutputs.hs5ClusterDiagnosticReport())
        appendLine()
        appendLine("--- Steering keys ---")
        appendLine(WheelKeyService.report(context))
        appendLine(Hs5SteeringKeys.diagnosticReport(context))
        appendLine()
        appendLine("--- diplay.log ---")
        append(logTail(File(File(context.filesDir, "logs"), "diplay.log")))
        }
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
