package com.shilapi.xcertplay.newui.settings

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.AppLocale
import com.shilapi.xcertplay.Hs5SteeringKeys
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateAvailability

data class NewUiSettingsState(
    val autoStartOnBoot: Boolean,
    val carBluetoothAudio: Boolean,
    val advancedAudioChannelMappingSupported: Boolean,
    val advancedAudioChannelMapping: Boolean,
    val audioFocusEnabled: Boolean,
    val mediaAudioChannel: Int,
    val navigationAudioChannel: Int,
    val vehicleIconCustom: Boolean,
    val vehicleName: String,
    val languageLabel: String,
    val updateSummary: String,
    val versionName: String,
    val showHs5SteeringKeys: Boolean,
    val carPlayWheelMedia: Boolean,
    val carPlayWheelSiri: Boolean,
) {
    /** Local stream routing applies only when CarPlay audio goes through the vehicle Bluetooth path. */
    val showLocalAudioRouting: Boolean get() = carBluetoothAudio

    fun mediaChannelLabel(context: Context): String = channelLabel(context, mediaAudioChannel)

    fun navigationChannelLabel(context: Context): String = channelLabel(context, navigationAudioChannel)

    companion object {
        val placeholder = NewUiSettingsState(
            autoStartOnBoot = true,
            carBluetoothAudio = false,
            advancedAudioChannelMappingSupported = false,
            advancedAudioChannelMapping = false,
            audioFocusEnabled = false,
            mediaAudioChannel = 0,
            navigationAudioChannel = 0,
            vehicleIconCustom = false,
            vehicleName = "",
            languageLabel = "",
            updateSummary = "",
            versionName = "",
            showHs5SteeringKeys = false,
            carPlayWheelMedia = true,
            carPlayWheelSiri = true,
        )

        private fun channelLabel(context: Context, channel: Int): String =
            if (channel == 0) {
                context.getString(R.string.auto_label)
            } else {
                channel.toString()
            }
    }
}

internal object NewUiSettingsStateReader {
    fun read(context: Context): NewUiSettingsState {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
        val update = UpdateAvailability.available(context, version ?: "")
        val updateSummary = if (update != null) {
            context.getString(R.string.new_ui_update_available, update.tagName)
        } else {
            context.getString(R.string.new_ui_update_latest)
        }
        return NewUiSettingsState(
            autoStartOnBoot = AirPlayPersistence.loadAutoStartOnBoot(context),
            carBluetoothAudio = AirPlayPersistence.loadCarBluetoothAudio(context),
            advancedAudioChannelMappingSupported =
                context.resources.getBoolean(R.bool.config_advanced_audio_channel_mapping),
            advancedAudioChannelMapping = AirPlayPersistence.loadAdvancedAudioChannelMapping(context),
            audioFocusEnabled = AirPlayPersistence.loadAudioFocusEnabled(context),
            mediaAudioChannel = AirPlayPersistence.loadMediaAudioChannel(context),
            navigationAudioChannel = AirPlayPersistence.loadNavigationAudioChannel(context),
            vehicleIconCustom = AirPlayPersistence.loadCustomAirPlayIconFile(context) != null,
            vehicleName = AirPlayPersistence.loadOemLabel(context),
            languageLabel = AppLocale.displayName(context, AppLocale.preference(context)),
            updateSummary = updateSummary,
            versionName = version ?: "?",
            showHs5SteeringKeys = Hs5HeadUnit.isHeadUnit(context),
            carPlayWheelMedia = AirPlayPersistence.loadCarPlayWheelMedia(context),
            carPlayWheelSiri = AirPlayPersistence.loadCarPlayWheelSiri(context),
        )
    }
}

internal object NewUiSettingsActions {
    fun saveAutoStart(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveAutoStartOnBoot(context, enabled)
    }

    fun saveCarBluetoothAudio(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveCarBluetoothAudio(context, enabled)
    }

    fun saveAudioFocusEnabled(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveAudioFocusEnabled(context, enabled)
    }

    fun saveAdvancedAudioChannelMapping(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveAdvancedAudioChannelMapping(context, enabled)
    }

    fun saveMediaAudioChannel(context: Context, channel: Int) {
        AirPlayPersistence.saveMediaAudioChannel(context, channel)
    }

    fun saveNavigationAudioChannel(context: Context, channel: Int) {
        AirPlayPersistence.saveNavigationAudioChannel(context, channel)
    }

    fun saveCarPlayWheelMedia(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveCarPlayWheelMedia(context, enabled)
        Hs5SteeringKeys.applySettings(context)
    }

    fun saveCarPlayWheelSiri(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveCarPlayWheelSiri(context, enabled)
        Hs5SteeringKeys.applySettings(context)
    }
}
