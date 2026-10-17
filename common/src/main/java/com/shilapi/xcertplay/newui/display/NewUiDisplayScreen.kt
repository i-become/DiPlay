package com.shilapi.xcertplay.newui.display

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiSegmentedRow
import com.shilapi.xcertplay.newui.NewUiTabScrollColumn
import com.shilapi.xcertplay.newui.NewUiToggleRow
import com.shilapi.xcertplay.newui.NewUiValueRow

@Composable
fun NewUiDisplayScreen(
    state: NewUiDisplayState,
    onPickFps: () -> Unit,
    onSmoothVideoChanged: (Boolean) -> Unit,
    onDirectVideoOutputChanged: (Boolean) -> Unit,
    onLowLatencyDecoderChanged: (Boolean) -> Unit,
    onMtkDecoderTuningChanged: (Boolean) -> Unit,
    onHudNavigationChanged: (Boolean) -> Unit,
    onClusterMapChanged: (Boolean) -> Unit,
    onClusterFlyScreenDefaultOnConnectChanged: (Boolean) -> Unit,
    onClusterFlyScreenWindowChanged: (Boolean) -> Unit,
    onClusterGestureChanged: (Boolean) -> Unit,
    onNightModeSelected: (Int) -> Unit,
) {
    NewUiTabScrollColumn {
        NewUiPanel {
            NewUiValueRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.new_ui_display_fps),
                subtitle = stringResource(R.string.new_ui_display_fps_sub),
                value = state.fpsLabel,
                onClick = onPickFps,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.smooth_video),
                subtitle = stringResource(R.string.smooth_video_description),
                checked = state.smoothVideoEnabled,
                onCheckedChange = onSmoothVideoChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.settings_direct_video_output),
                subtitle = stringResource(R.string.settings_direct_video_output_description),
                checked = state.directVideoOutputEnabled,
                onCheckedChange = onDirectVideoOutputChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.settings_low_latency_decoder),
                subtitle = stringResource(R.string.settings_low_latency_decoder_description),
                checked = state.lowLatencyDecoderEnabled,
                onCheckedChange = onLowLatencyDecoderChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.settings_mtk_decoder_tuning),
                subtitle = state.mtkDecoderTuningDescription,
                checked = state.mtkDecoderTuningEnabled,
                onCheckedChange = onMtkDecoderTuningChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_navigation,
                title = stringResource(R.string.new_ui_display_hud_nav),
                subtitle = stringResource(R.string.new_ui_display_hud_nav_sub),
                checked = state.hudNavigationEnabled,
                onCheckedChange = onHudNavigationChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_dashboard,
                title = stringResource(R.string.new_ui_display_cluster_map),
                subtitle = stringResource(R.string.new_ui_display_cluster_map_sub),
                checked = state.clusterMapEnabled,
                onCheckedChange = onClusterMapChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_dashboard,
                title = stringResource(R.string.new_ui_display_cluster_fly_default),
                subtitle = stringResource(R.string.new_ui_display_cluster_fly_default_sub),
                checked = state.clusterMapEnabled && state.clusterFlyScreenDefaultOnConnect,
                enabled = state.clusterMapEnabled,
                onCheckedChange = onClusterFlyScreenDefaultOnConnectChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_dashboard,
                title = stringResource(R.string.new_ui_display_cluster_fly_window),
                subtitle = stringResource(R.string.new_ui_display_cluster_fly_window_sub),
                checked = state.clusterMapEnabled && state.clusterFlyScreenWindowEnabled,
                enabled = state.clusterMapEnabled,
                onCheckedChange = onClusterFlyScreenWindowChanged,
            )
            NewUiToggleRow(
                iconRes = R.drawable.new_ui_ic_gesture_swipe,
                title = stringResource(R.string.new_ui_display_cluster_gesture),
                subtitle = stringResource(R.string.new_ui_display_cluster_gesture_sub),
                checked = state.clusterMapEnabled && state.clusterHorizontalGestureEnabled,
                enabled = state.clusterMapEnabled,
                onCheckedChange = onClusterGestureChanged,
            )
            NewUiSegmentedRow(
                iconRes = R.drawable.new_ui_ic_theme,
                title = stringResource(R.string.new_ui_display_carplay_theme),
                subtitle = stringResource(R.string.new_ui_display_carplay_theme_sub),
                options = listOf(
                    stringResource(R.string.new_ui_theme_follow_system),
                    stringResource(R.string.new_ui_theme_day),
                    stringResource(R.string.new_ui_theme_night),
                ),
                selectedIndex = state.nightModeIndex,
                onSelect = onNightModeSelected,
            )
        }
    }
}
