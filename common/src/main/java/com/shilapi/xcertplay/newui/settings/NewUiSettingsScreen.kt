package com.shilapi.xcertplay.newui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiNavRow
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiTabScrollColumn
import com.shilapi.xcertplay.newui.NewUiToggleRow

@Composable
fun NewUiSettingsScreen(
    state: NewUiSettingsState,
    onAutoStartChanged: (Boolean) -> Unit,
    onCarBluetoothAudioChanged: (Boolean) -> Unit,
    onAudioFocusChanged: (Boolean) -> Unit,
    onAdvancedAudioMappingChanged: (Boolean) -> Unit,
    onPickMediaChannel: () -> Unit,
    onPickNavigationChannel: () -> Unit,
    onVehicleIcon: () -> Unit,
    onVehicleName: () -> Unit,
    onPermissions: () -> Unit,
    onLanguage: () -> Unit,
    onCheckUpdate: () -> Unit,
    onCarPlayWheelMediaChanged: (Boolean) -> Unit,
    onCarPlayWheelSiriChanged: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    NewUiTabScrollColumn {
        NewUiPanel {
            NewUiToggleRow(
                iconRes = R.drawable.new_ui_ic_power,
                title = stringResource(R.string.new_ui_settings_boot_start),
                subtitle = stringResource(R.string.new_ui_settings_boot_start_sub),
                checked = state.autoStartOnBoot,
                onCheckedChange = onAutoStartChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_audio,
                title = stringResource(R.string.settings_car_bluetooth_audio),
                subtitle = "",
                checked = state.carBluetoothAudio,
                onCheckedChange = onCarBluetoothAudioChanged,
            )
            if (state.showHongqiWheelKeys) {
                val wheelKeysEnabled = !state.carBluetoothAudio
                NewUiToggleRow(
                    iconRes = R.drawable.ic_dp_audio,
                    title = stringResource(R.string.settings_carplay_wheel_media),
                    subtitle = stringResource(R.string.settings_carplay_wheel_media_description),
                    checked = state.carPlayWheelMedia && wheelKeysEnabled,
                    enabled = wheelKeysEnabled,
                    onCheckedChange = onCarPlayWheelMediaChanged,
                )
                NewUiToggleRow(
                    iconRes = R.drawable.ic_dp_audio,
                    title = stringResource(R.string.settings_carplay_wheel_siri),
                    subtitle = stringResource(R.string.settings_carplay_wheel_siri_description),
                    checked = state.carPlayWheelSiri && wheelKeysEnabled,
                    enabled = wheelKeysEnabled,
                    onCheckedChange = onCarPlayWheelSiriChanged,
                )
            }
            if (state.showLocalAudioRouting) {
                NewUiToggleRow(
                    iconRes = R.drawable.ic_dp_audio,
                    title = stringResource(R.string.contrib_audio_home_toggle_audio_focus),
                    subtitle = stringResource(R.string.contrib_audio_home_toggle_audio_focus_desc),
                    checked = state.audioFocusEnabled,
                    onCheckedChange = onAudioFocusChanged,
                )
                if (state.advancedAudioChannelMappingSupported) {
                    NewUiToggleRow(
                        iconRes = R.drawable.ic_dp_audio,
                        title = stringResource(R.string.advanced_audio_channel_mapping),
                        subtitle = stringResource(R.string.use_usage_content_type_routing_instead_of_stream_type),
                        checked = state.advancedAudioChannelMapping,
                        onCheckedChange = onAdvancedAudioMappingChanged,
                    )
                }
                NewUiNavRow(
                    iconRes = R.drawable.ic_dp_audio,
                    title = stringResource(R.string.contrib_audio_home_media_channel_label),
                    trailing = state.mediaChannelLabel(context),
                    onClick = onPickMediaChannel,
                )
                NewUiNavRow(
                    iconRes = R.drawable.ic_dp_audio,
                    title = stringResource(R.string.contrib_audio_home_nav_channel_label),
                    trailing = state.navigationChannelLabel(context),
                    onClick = onPickNavigationChannel,
                    showDivider = true,
                )
            }
            NewUiNavRow(
                iconRes = R.drawable.ic_dp_permissions,
                title = stringResource(R.string.new_ui_settings_permissions),
                onClick = onPermissions,
            )
            NewUiNavRow(
                iconRes = R.drawable.ic_dp_car,
                title = stringResource(R.string.new_ui_settings_vehicle_icon),
                trailing = if (state.vehicleIconCustom) {
                    stringResource(R.string.new_ui_settings_vehicle_icon_custom)
                } else {
                    stringResource(R.string.default_icon)
                },
                onClick = onVehicleIcon,
            )
            NewUiNavRow(
                iconRes = R.drawable.ic_dp_vehicle,
                title = stringResource(R.string.new_ui_settings_vehicle_name),
                trailing = state.vehicleName,
                onClick = onVehicleName,
            )
            NewUiNavRow(
                iconRes = R.drawable.ic_dp_language,
                title = stringResource(R.string.language_section_title),
                trailing = state.languageLabel,
                onClick = onLanguage,
            )
            NewUiNavRow(
                iconRes = R.drawable.new_ui_ic_update,
                title = stringResource(R.string.new_ui_settings_check_update),
                trailing = state.updateSummary,
                onClick = onCheckUpdate,
                showDivider = false,
            )
        }
    }
}
