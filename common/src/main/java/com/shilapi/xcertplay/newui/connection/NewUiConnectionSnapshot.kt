package com.shilapi.xcertplay.newui.connection

import android.content.Context
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayConnectionProgress
import com.shilapi.xcertplay.ConnectionProgressUiState
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.AirPlayPersistence
import java.net.Inet4Address
import java.net.NetworkInterface

enum class NewUiConnectionPhase {
    NEEDS_SETUP,
    READY,
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    FAILED,
}

data class NewUiConnectionSnapshot(
    val phase: NewUiConnectionPhase,
    val statusTitle: String,
    val phoneName: String,
    val linkSummary: String?,
    val connectionMethodLabel: String,
    val locationReportingEnabled: Boolean,
    val canConnect: Boolean,
    val sessionRunning: Boolean,
    val hasSelectedPhone: Boolean,
    val progressLitCount: Int,
    val progressStepTitle: String,
    val progressStepSubtitle: String?,
    val failureBanner: String?,
) {
    val isConnected: Boolean get() = phase == NewUiConnectionPhase.CONNECTED

    val isFailed: Boolean get() = phase == NewUiConnectionPhase.FAILED

    companion object {
        /** Used before [android.app.Activity.onCreate]; must not touch [Context]-backed prefs. */
        val placeholder = NewUiConnectionSnapshot(
            phase = NewUiConnectionPhase.READY,
            statusTitle = "",
            phoneName = "",
            linkSummary = null,
            connectionMethodLabel = "",
            locationReportingEnabled = false,
            canConnect = true,
            sessionRunning = false,
            hasSelectedPhone = false,
            progressLitCount = 0,
            progressStepTitle = "",
            progressStepSubtitle = null,
            failureBanner = null,
        )
    }
}

internal object NewUiConnectionSnapshotReader {
    fun read(context: Context, setupError: String?): NewUiConnectionSnapshot {
        val disconnected = ConnectionProgressUiState.disconnected()
        val sessionRunning = CarPlayBackgroundSession.hasSession() && !disconnected
        val carPlayActive = CarPlayBackgroundSession.active
        val phoneAddress = DiPlayPreferences.phoneAddress(context)
        val phoneName = DiPlayPreferences.phoneName(context)
        val connectionFailed = ConnectionProgressUiState.failed() && !carPlayActive
        val phase = when {
            setupError != null -> NewUiConnectionPhase.NEEDS_SETUP
            carPlayActive -> NewUiConnectionPhase.CONNECTED
            disconnected -> NewUiConnectionPhase.DISCONNECTED
            connectionFailed -> NewUiConnectionPhase.FAILED
            sessionRunning -> NewUiConnectionPhase.CONNECTING
            DiPlayPreferences.phoneAddress(context) != null -> NewUiConnectionPhase.READY
            else -> NewUiConnectionPhase.READY
        }
        val statusTitle = when {
            setupError != null -> context.getString(R.string.setup_needs_attention)
            carPlayActive -> context.getString(R.string.new_ui_status_connected)
            disconnected -> context.getString(R.string.new_ui_status_disconnected)
            connectionFailed -> context.getString(R.string.new_ui_status_failed)
            sessionRunning -> context.getString(R.string.connecting_to_your_iphone)
            else -> context.getString(R.string.ready_when_you_are)
        }
        val failureBanner = if (connectionFailed) {
            ConnectionProgressUiState.failureDetail()
                ?: context.getString(R.string.new_ui_connection_failed_banner)
        } else {
            null
        }
        val linkSummary = if (carPlayActive || sessionRunning) {
            val mode = wirelessModeLabel(context, AirPlayPersistence.loadWirelessHotspotMode(context))
            val host = localIpv4()?.let { "$mode | $it" } ?: mode
            host
        } else {
            null
        }
        val connectionMethodLabel = wirelessModeLabel(context, AirPlayPersistence.loadWirelessHotspotMode(context))
        val progressLit = when {
            carPlayActive -> CarPlayConnectionProgress.STEP_COUNT
            connectionFailed || sessionRunning -> ConnectionProgressUiState.litCount()
            else -> 0
        }
        val (progressTitle, progressSubtitle) = when {
            setupError != null -> context.getString(R.string.setup_needs_attention) to null
            carPlayActive -> context.getString(R.string.new_ui_connection_progress_complete_title) to
                context.getString(R.string.new_ui_connection_progress_complete_sub)
            disconnected -> context.getString(R.string.new_ui_status_disconnected) to
                context.getString(R.string.new_ui_connection_progress_idle_hint)
            connectionFailed && progressLit > 0 -> {
                context.getString(CarPlayConnectionProgress.stepTitleRes(progressLit)) to
                    (ConnectionProgressUiState.failureDetail()
                        ?: context.getString(R.string.new_ui_connection_progress_failed_sub))
            }
            connectionFailed -> context.getString(R.string.new_ui_status_failed) to
                context.getString(R.string.new_ui_connection_failed_banner)
            sessionRunning && progressLit > 0 -> {
                context.getString(CarPlayConnectionProgress.stepTitleRes(progressLit)) to
                    context.getString(R.string.connecting_to_your_iphone)
            }
            sessionRunning -> context.getString(R.string.connecting_to_your_iphone) to null
            else -> context.getString(R.string.new_ui_connection_progress_idle_hint) to
                context.getString(
                    R.string.new_ui_connection_progress_steps_hint,
                    CarPlayConnectionProgress.STEP_COUNT,
                )
        }
        return NewUiConnectionSnapshot(
            phase = phase,
            statusTitle = statusTitle,
            phoneName = phoneName.ifBlank { context.getString(R.string.connect_phone) },
            linkSummary = linkSummary,
            connectionMethodLabel = connectionMethodLabel,
            locationReportingEnabled = AirPlayPersistence.loadLocationReportingEnabled(context),
            canConnect = setupError == null,
            sessionRunning = sessionRunning,
            hasSelectedPhone = phoneAddress != null,
            progressLitCount = progressLit,
            progressStepTitle = progressTitle,
            progressStepSubtitle = progressSubtitle,
            failureBanner = failureBanner,
        )
    }

    private fun wirelessModeLabel(context: Context, mode: WirelessHotspotMode): String = when (mode) {
        WirelessHotspotMode.WIFI_P2P -> context.getString(R.string.new_ui_link_wifi_direct)
        WirelessHotspotMode.MANUAL -> context.getString(R.string.new_ui_link_car_hotspot)
        WirelessHotspotMode.EXISTING_WIFI -> context.getString(R.string.new_ui_link_existing_wifi)
        WirelessHotspotMode.LOCAL_ONLY_HOTSPOT -> context.getString(R.string.new_ui_link_local_hotspot)
    }

    private fun localIpv4(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()?.toList()?.asSequence()
            ?.flatMap { iface -> iface.inetAddresses.toList().asSequence() }
            ?.firstOrNull { address ->
                !address.isLoopbackAddress && address is Inet4Address && !address.isLinkLocalAddress
            }
            ?.hostAddress
    }.getOrNull()
}
