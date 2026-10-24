package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import java.lang.ref.WeakReference

/** Lets [NewUiActivity] push display settings into a running [CarPlayHostActivity]. */
internal object CarPlayHostSettingsBridge {
    private var hostRef: WeakReference<CarPlayHostActivity>? = null
    private val vehicleNightCommands by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "DiPlay-VehicleNight").apply { isDaemon = true }
        }
    }

    fun register(host: CarPlayHostActivity) {
        val reference = WeakReference(host)
        hostRef = reference
        com.shilapi.xcertplay.hud.BydNavigationOutputs.setHs5ClusterExitListener {
            val target = reference.get()
            if (target != null) closeFlyScreenFromVehicle(target)
        }
    }

    fun unregister(host: CarPlayHostActivity) {
        if (hostRef?.get() === host) {
            hostRef = null
            com.shilapi.xcertplay.hud.BydNavigationOutputs.setHs5ClusterExitListener(null)
        }
    }

    fun notifyCarPlayNightModeChanged(context: Context) {
        val host = hostRef?.get()
        if (host != null) {
            host.reloadCarPlayNightModeFromSettings()
            return
        }
        val night = CarPlayNightModePolicy.effectiveNight(context)
        CarPlayBackgroundSession.snapshot()?.controller?.syncCarPlayNightMode(night)
    }

    fun notifyClusterMapSettingsChanged() {
        hostRef?.get()?.reloadClusterMapFromSettings()
    }

    fun notifyVehicleNightChanged(context: Context, night: Boolean?) {
        val host = hostRef?.get()
        if (host != null) {
            host.vehicleNightChanged(night)
        } else {
            // A retained CarPlay session can outlive its host Activity.
            val controller = CarPlayBackgroundSession.snapshot()?.controller ?: return
            vehicleNightCommands.execute {
                val mode = AirPlayPersistence.loadCarPlayNightMode(context)
                if ((mode == CarPlayNightMode.SYSTEM || mode == CarPlayNightMode.AMBIENT) &&
                    CarPlayBackgroundSession.snapshot()?.controller === controller) {
                    val target = CarPlayNightModePolicy.effectiveNight(context)
                    val sent = controller.syncCarPlayNightMode(target)
                    DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("QISI_POSITION_LIGHT background-sync night=$target commandWritten=$sent") }
                }
            }
        }
    }

    fun flyScreenShown(): Boolean = hostRef?.get()?.flyScreenShown() == true

    /** A wheel key closes the fly screen as the three-finger gesture does; the gesture reopens it. */
    fun closeFlyScreen(reason: String): Boolean = hostRef?.get()?.closeFlyScreen(reason) == true

    internal fun closeFlyScreenFromVehicle(expected: CarPlayHostActivity): Boolean =
        hostRef?.get() === expected && expected.closeFlyScreen("Qisi full-screen navigation exit")

    /** Identity is captured before posting a key, so a late event cannot close a replacement UI. */
    fun wheelBackTarget(): CarPlayHostActivity? = hostRef?.get()?.takeIf {
        it.projectionShown() || it.flyScreenShown()
    }

    fun wheelBackTargetShown(): Boolean = wheelBackTarget() != null

    fun closeFromWheel(expected: CarPlayHostActivity, reason: String): Boolean {
        if (hostRef?.get() !== expected) return false
        return if (expected.projectionShown()) expected.exitProjection(reason)
        else expected.closeFlyScreen(reason)
    }

    fun closeFromAndroidBack(reason: String): Boolean =
        wheelBackTarget()?.let { closeFromWheel(it, reason) } == true

    fun notifySessionLogRecordingChanged() {
        hostRef?.get()?.syncSessionLogRecordingFromSettings()
    }

    fun clearSessionLogs(context: Context) {
        hostRef?.get()?.clearRecordedSessionLogs() ?: DiPlaySessionLogFiles.clearAll(context)
    }
}
