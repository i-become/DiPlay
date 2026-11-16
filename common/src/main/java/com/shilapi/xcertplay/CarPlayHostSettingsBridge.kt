package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Context
import java.lang.ref.WeakReference

/** Lets [NewUiActivity] push display settings into a running [CarPlayHostActivity]. */
internal object CarPlayHostSettingsBridge {
    private var hostRef: WeakReference<CarPlayHostActivity>? = null
    private var clusterExitRequest: (() -> Unit)? = null
    private val vehicleNightCommands by lazy {
        java.util.concurrent.Executors.newSingleThreadExecutor { task ->
            Thread(task, "DiPlay-VehicleNight").apply { isDaemon = true }
        }
    }

    fun register(host: CarPlayHostActivity) {
        val reference = WeakReference(host)
        hostRef = reference
        val request: () -> Unit = {
            val target = reference.get()
            if (target != null) closeFlyScreenFromVehicle(target)
        }
        clusterExitRequest = request
        com.shilapi.xcertplay.hud.Hs5NavigationOutputs.setHs5ClusterExitListener(request)
    }

    fun unregister(host: CarPlayHostActivity) {
        if (hostRef?.get() === host) {
            hostRef = null
            clusterExitRequest?.let(com.shilapi.xcertplay.hud.Hs5NavigationOutputs::clearHs5ClusterExitListener)
            clusterExitRequest = null
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
        com.shilapi.xcertplay.carlink.CarlinkConnectionActivity.current?.get()?.updateCluster()
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
    fun closeFlyScreen(reason: String): Boolean = hostRef?.get()?.closeFlyScreen(reason) == true ||
        com.shilapi.xcertplay.carlink.CarlinkConnectionActivity.current?.get()?.closeFlyScreen() == true

    internal fun closeFlyScreenFromVehicle(expected: CarPlayHostActivity): Boolean =
        hostRef?.get() === expected && expected.closeFlyScreen("Qisi full-screen navigation exit")

    /** Identity is captured before posting a key, so a late event cannot close a replacement UI. */
    fun wheelBackTarget(): CarPlayHostActivity? = hostRef?.get()?.takeIf {
        it.projectionShown() || it.flyScreenShown()
    }

    fun wheelBackTargetShown(): Boolean = wheelBackTarget() != null

    fun closeFromWheel(expected: CarPlayHostActivity, reason: String): Boolean {
        if (hostRef?.get() !== expected) return false
        return if (expected.flyScreenShown()) expected.closeFlyScreen(reason)
        else expected.exitProjection(reason)
    }

    fun closeFromAndroidBack(reason: String): Boolean =
        wheelBackTarget()?.let {
            if (it.projectionShown()) it.exitProjection(reason) else it.closeFlyScreen(reason)
        } == true

    fun notifySessionLogRecordingChanged() {
        hostRef?.get()?.syncSessionLogRecordingFromSettings()
    }

    fun clearSessionLogs(context: Context) {
        hostRef?.get()?.clearRecordedSessionLogs() ?: DiPlaySessionLogFiles.clearAll(context)
    }
}
