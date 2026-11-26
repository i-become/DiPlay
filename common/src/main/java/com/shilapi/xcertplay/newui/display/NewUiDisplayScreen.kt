package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiCompactToggleRow
import com.shilapi.xcertplay.newui.NewUiCompactValueRow
import com.shilapi.xcertplay.newui.NewUiMainPageTitle
import com.shilapi.xcertplay.newui.NewUiTwoColumnPage
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.localizedLabel
import com.shilapi.xcertplay.newui.NewUiAccentActionButton
import com.shilapi.xcertplay.newui.NewUiLayout

@Composable
fun NewUiDisplayScreen(
    state: NewUiDisplayState,
    navMarkerSummary: String,
    onPickFps: () -> Unit,
    onPickNavMarker: () -> Unit,
    onOpenClusterManager: () -> Unit,
    onSmoothVideoChanged: (Boolean) -> Unit,
    onDirectVideoOutputChanged: (Boolean) -> Unit,
    onLowLatencyDecoderChanged: (Boolean) -> Unit,
    onMtkDecoderTuningChanged: (Boolean) -> Unit,
    onHudNavigationChanged: (Boolean) -> Unit,
    onClusterMapChanged: (Boolean) -> Unit,
    onClusterFlyScreenDefaultOnConnectChanged: (Boolean) -> Unit,
    onClusterGestureChanged: (Boolean) -> Unit,
    onNightModeSelected: (Int) -> Unit,
    clusterOnlyMode: Boolean = false,
    onClusterOnlyModeChanged: (Boolean) -> Unit = {},
    onPickIconSize: () -> Unit = {},
    onReconnect: () -> Unit = {},
) {
    Column(Modifier.fillMaxSize()) {
        NewUiMainPageTitle(stringResource(R.string.new_ui_shell_display_page_title))
        NewUiTwoColumnPage(
            modifier = Modifier.weight(1f),
            left = {
                NewUiCompactValueRow(
                    title = stringResource(R.string.settings_carplay_icon_size),
                    subtitle = stringResource(R.string.settings_carplay_icon_size_description),
                    value = state.carPlaySize.localizedLabel(LocalContext.current),
                    onClick = onPickIconSize,
                )
                NewUiCompactValueRow(
                    title = stringResource(R.string.new_ui_shell_display_fps_title),
                    subtitle = stringResource(R.string.new_ui_shell_display_fps_sub),
                    value = state.fpsLabel,
                    onClick = onPickFps,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.smooth_video),
                    subtitle = stringResource(R.string.new_ui_shell_display_smooth_sub),
                    checked = state.smoothVideoEnabled,
                    onCheckedChange = onSmoothVideoChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.settings_direct_video_output),
                    subtitle = stringResource(R.string.new_ui_shell_display_direct_video_sub),
                    checked = state.directVideoOutputEnabled,
                    onCheckedChange = onDirectVideoOutputChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.settings_low_latency_decoder),
                    subtitle = stringResource(R.string.new_ui_shell_display_low_latency_sub),
                    checked = state.lowLatencyDecoderEnabled,
                    onCheckedChange = onLowLatencyDecoderChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.settings_mtk_decoder_tuning),
                    subtitle = stringResource(R.string.new_ui_shell_display_mtk_sub),
                    checked = state.mtkDecoderTuningEnabled,
                    onCheckedChange = onMtkDecoderTuningChanged,
                    showDivider = false,
                )
            },
            right = {
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_cluster_only_title),
                    subtitle = stringResource(R.string.new_ui_cluster_only_sub),
                    checked = clusterOnlyMode, onCheckedChange = onClusterOnlyModeChanged,
                )
                // Style selection and static previews are available without vehicle hardware.
                NewUiCompactValueRow(
                    title = stringResource(R.string.settings_cluster_manager),
                    subtitle = stringResource(R.string.settings_cluster_manager_entry_description),
                    value = stringResource(state.clusterStyle.titleRes),
                    onClick = onOpenClusterManager,
                )
                NewUiCompactValueRow(
                    title = stringResource(R.string.new_ui_display_nav_marker),
                    subtitle = stringResource(R.string.new_ui_display_nav_marker_sub),
                    value = navMarkerSummary,
                    onClick = onPickNavMarker,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_shell_display_hud_title),
                    subtitle = stringResource(R.string.new_ui_shell_display_hud_sub),
                    checked = state.hudNavigationEnabled,
                    onCheckedChange = onHudNavigationChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_shell_display_cluster_title),
                    subtitle = stringResource(R.string.new_ui_shell_display_cluster_sub),
                    checked = state.clusterMapEnabled,
                    onCheckedChange = onClusterMapChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_display_cluster_fly_default),
                    subtitle = stringResource(R.string.new_ui_shell_display_fly_default_sub),
                    checked = state.clusterMapEnabled && state.clusterFlyScreenDefaultOnConnect,
                    enabled = state.clusterMapEnabled,
                    onCheckedChange = onClusterFlyScreenDefaultOnConnectChanged,
                )
                NewUiCompactToggleRow(
                    title = stringResource(R.string.new_ui_display_cluster_gesture),
                    subtitle = stringResource(R.string.new_ui_shell_display_gesture_sub),
                    checked = state.clusterMapEnabled && state.clusterHorizontalGestureEnabled,
                    enabled = state.clusterMapEnabled,
                    onCheckedChange = onClusterGestureChanged,
                )
                CarPlayThemeSegmentedRow(
                    selectedIndex = state.nightModeIndex,
                    onSelect = onNightModeSelected,
                )
            },
        )
        if (state.iconSizeReconnectNeeded) {
            NewUiAccentActionButton(
                label = stringResource(R.string.apply_and_reconnect),
                onClick = onReconnect,
                modifier = Modifier.padding(top = NewUiLayout.MainGap),
            )
        }
    }
}

@Composable
private fun CarPlayThemeSegmentedRow(
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val options = listOf(
        stringResource(R.string.new_ui_theme_follow_system),
        stringResource(R.string.new_ui_theme_day),
        stringResource(R.string.new_ui_theme_night),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        Text(
            stringResource(R.string.new_ui_shell_display_theme_title),
            style = NewUiTypography.CompactSettingTitle,
            color = NewUiColors.TextPrimary,
        )
        Text(
            stringResource(R.string.new_ui_shell_display_theme_sub),
            style = NewUiTypography.SettingSubtitle,
            color = NewUiColors.TextSecondary,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val shape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .then(
                            if (selected) {
                                Modifier.background(NewUiColors.Accent, shape)
                            } else {
                                Modifier
                                    .background(NewUiColors.ButtonNormal.copy(alpha = 0.35f), shape)
                                    .border(1.dp, NewUiColors.Divider.copy(alpha = 0.5f), shape)
                            },
                        )
                        .clickable { onSelect(index) }
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = NewUiTypography.ActionSubtitle,
                        color = if (selected) NewUiColors.OnAccent else NewUiColors.TextPrimary,
                    )
                }
            }
        }
    }
}
