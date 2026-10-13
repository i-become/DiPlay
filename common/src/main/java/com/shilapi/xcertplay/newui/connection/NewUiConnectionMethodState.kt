package com.shilapi.xcertplay.newui.connection

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.messageResource
import com.shilapi.xcertplay.network.CarHotspotStatus
import com.shilapi.xcertplay.network.CarHotspotTethering
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

data class NewUiConnectionMethodDraft(
    val mode: WirelessHotspotMode,
    val manualSsid: String,
    val manualPassword: String,
    val existingSsid: String,
    val existingPassword: String,
) {
    companion object {
        fun read(context: Context): NewUiConnectionMethodDraft = NewUiConnectionMethodDraft(
            mode = AirPlayPersistence.loadWirelessHotspotMode(context).let { stored ->
                when (stored) {
                    WirelessHotspotMode.MANUAL,
                    WirelessHotspotMode.WIFI_P2P,
                    WirelessHotspotMode.EXISTING_WIFI,
                    -> stored
                    else -> WirelessHotspotMode.MANUAL
                }
            },
            manualSsid = AirPlayPersistence.loadManualHotspotSsid(context),
            manualPassword = AirPlayPersistence.loadManualHotspotPassphrase(context),
            existingSsid = AirPlayPersistence.loadExistingWifiSsid(context),
            existingPassword = AirPlayPersistence.loadExistingWifiPassphrase(context),
        )
    }
}

internal object NewUiConnectionMethodActions {
    fun manualHotspotOff(context: Context): Boolean =
        AirPlayPersistence.loadWirelessHotspotMode(context) == WirelessHotspotMode.MANUAL &&
            CarHotspotStatus.isEnabled(context) == false &&
            !CarHotspotTethering.permitted(context)

    fun manualConfigured(ssid: String, password: String): Boolean =
        ManualHotspotValidation.error(ssid, password) == null

    fun save(context: Context, draft: NewUiConnectionMethodDraft): SaveResult {
        return when (draft.mode) {
            WirelessHotspotMode.MANUAL -> {
                val error = ManualHotspotValidation.error(draft.manualSsid.trim(), draft.manualPassword)
                if (error != null) {
                    return SaveResult.ValidationError(error.messageResource())
                }
                val ssid = draft.manualSsid.trim()
                val password = draft.manualPassword
                val security = ManualHotspotValidation.securityFor(password)
                AirPlayPersistence.saveManualHotspotSsid(context, ssid)
                AirPlayPersistence.saveManualHotspotPassphrase(context, password)
                AirPlayPersistence.saveManualHotspotSecurity(context, security)
                AirPlayPersistence.saveManualHotspotBand(context, com.shilapi.xcertplay.orchestration.ManualHotspotBand.AUTO)
                AirPlayPersistence.saveManualHotspotChannel(context, 0)
                applyMode(context, WirelessHotspotMode.MANUAL)
                SaveResult.Success
            }
            WirelessHotspotMode.EXISTING_WIFI -> {
                val error = ManualHotspotValidation.error(draft.existingSsid.trim(), draft.existingPassword)
                if (error != null) {
                    return SaveResult.ValidationError(error.messageResource())
                }
                AirPlayPersistence.saveExistingWifiCredentials(
                    context,
                    draft.existingSsid.trim(),
                    draft.existingPassword,
                )
                applyMode(context, WirelessHotspotMode.EXISTING_WIFI)
                SaveResult.Success
            }
            WirelessHotspotMode.WIFI_P2P -> {
                applyMode(context, WirelessHotspotMode.WIFI_P2P)
                SaveResult.Success
            }
            else -> {
                applyMode(context, draft.mode)
                SaveResult.Success
            }
        }
    }

    private fun applyMode(context: Context, mode: WirelessHotspotMode) {
        AirPlayPersistence.saveWirelessHotspotMode(context, mode)
    }

    sealed class SaveResult {
        data object Success : SaveResult()
        data class ValidationError(val messageRes: Int) : SaveResult()
    }
}
