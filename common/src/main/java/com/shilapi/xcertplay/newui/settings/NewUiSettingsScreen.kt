package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiCompactNavRow
import com.shilapi.xcertplay.newui.NewUiCompactToggleRow
import com.shilapi.xcertplay.newui.NewUiMainPageTitle
import com.shilapi.xcertplay.newui.NewUiTwoColumnPage

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
    onCallsInterruptMusicChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    Column(Modifier.fillMaxSize()) {
        NewUiMainPageTitle(stringResource(R.string.new_ui_nav_settings))
        NewUiTwoColumnPage(
            left = {
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_settings_boot_start),
                    subtitle = stringResource(R.string.new_ui_shell_settings_boot_sub),
                    checked = state.autoStartOnBoot,
                    onCheckedChange = onAutoStartChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.settings_car_bluetooth_audio),
                    checked = state.carBluetoothAudio,
                    onCheckedChange = onCarBluetoothAudioChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.settings_calls_interrupt_music),
                    subtitle = stringResource(R.string.settings_calls_interrupt_music_help),
                    checked = state.callsInterruptMusic,
                    onCheckedChange = onCallsInterruptMusicChanged,
                )
                if (state.showHs5SteeringKeys) {
                    val wheelKeysEnabled = !state.carBluetoothAudio
                    NewUiCompactToggleRow(
                        title = stringResource(R.string.settings_carplay_wheel_media),
                        subtitle = stringResource(R.string.new_ui_shell_settings_wheel_media_sub),
                        checked = state.carPlayWheelMedia && wheelKeysEnabled,
                        enabled = wheelKeysEnabled,
                        onCheckedChange = onCarPlayWheelMediaChanged,
                    )
                    NewUiCompactToggleRow(
                        title = stringResource(R.string.settings_carplay_wheel_siri),
                        subtitle = stringResource(R.string.settings_carplay_wheel_siri_description),
                        checked = state.carPlayWheelSiri,
                        onCheckedChange = onCarPlayWheelSiriChanged,
                    )
                }
                if (state.showLocalAudioRouting) {
                    NewUiCompactToggleRow(
                        title = stringResource(R.string.contrib_audio_home_toggle_audio_focus),
                        subtitle = stringResource(R.string.new_ui_shell_settings_audio_focus_sub),
                        checked = state.audioFocusEnabled,
                        onCheckedChange = onAudioFocusChanged,
                    )
                    if (state.advancedAudioChannelMappingSupported) {
                        NewUiCompactToggleRow(
                            title = stringResource(R.string.advanced_audio_channel_mapping),
                            subtitle = "",
                            checked = state.advancedAudioChannelMapping,
                            onCheckedChange = onAdvancedAudioMappingChanged,
                        )
                    }
                    NewUiCompactNavRow(
                        title = stringResource(R.string.contrib_audio_home_media_channel_label),
                        trailing = state.mediaChannelLabel(context),
                        onClick = onPickMediaChannel,
                    )
                    NewUiCompactNavRow(
                        title = stringResource(R.string.contrib_audio_home_nav_channel_label),
                        trailing = state.navigationChannelLabel(context),
                        onClick = onPickNavigationChannel,
                        showDivider = false,
                    )
                }
            },
            right = {
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_shell_settings_permissions_title),
                    onClick = onPermissions,
                )
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_shell_settings_vehicle_icon_title),
                    trailing = if (state.vehicleIconCustom) {
                        stringResource(R.string.new_ui_settings_vehicle_icon_custom)
                    } else {
                        stringResource(R.string.default_icon)
                    },
                    onClick = onVehicleIcon,
                )
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_shell_settings_vehicle_name_title),
                    trailing = state.vehicleName,
                    onClick = onVehicleName,
                )
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_shell_settings_language_title),
                    trailing = state.languageLabel,
                    onClick = onLanguage,
                )
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_settings_check_update),
                    subtitle = state.updateSummary,
                    onClick = onCheckUpdate,
                    showDivider = false,
                )
            },
        )
    }
}
