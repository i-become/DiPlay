package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiToggleRow
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsUpdateScreen(
    versionName: String,
    backgroundChecksEnabled: Boolean,
    state: NewUiUpdateUiState,
    onBack: () -> Unit,
    onBackgroundChecksChanged: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val primaryLabel = if (state.installPreparing) null else when (state.stage) {
        NewUiUpdateStage.IDLE, NewUiUpdateStage.FAILED -> stringResource(R.string.update_check)
        NewUiUpdateStage.AVAILABLE -> stringResource(R.string.update_download)
        NewUiUpdateStage.READY -> stringResource(R.string.update_install, state.releaseTag.orEmpty())
        NewUiUpdateStage.CHECKING,
        NewUiUpdateStage.DOWNLOADING,
        NewUiUpdateStage.VERIFYING,
        -> null
    }
    val primaryAction: (() -> Unit)? = if (state.installPreparing) null else when (state.stage) {
        NewUiUpdateStage.IDLE, NewUiUpdateStage.FAILED -> onCheck
        NewUiUpdateStage.AVAILABLE -> onDownload
        NewUiUpdateStage.READY -> onInstall
        else -> null
    }
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_settings_check_update)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            NewUiPanel {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                    val hasUpdate = state.stage == NewUiUpdateStage.AVAILABLE
                    val latest = state.releaseTag
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.new_ui_update_current_version, versionName),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (hasUpdate) {
                            Text(
                                stringResource(R.string.update_available_home),
                                style = NewUiTypography.SettingSubtitle,
                                color = NewUiColors.TextPrimary,
                                modifier = Modifier
                                    .background(
                                        NewUiColors.HongqiRed.copy(alpha = if (NewUiColors.night) 0.78f else 0.14f),
                                        RoundedCornerShape(50),
                                    )
                                    .padding(horizontal = 14.dp, vertical = 7.dp),
                            )
                        }
                    }
                    Text(
                        if (hasUpdate && latest != null) stringResource(R.string.new_ui_update_available, latest)
                        else stringResource(R.string.new_ui_update_latest_version, when {
                            state.stage == NewUiUpdateStage.CHECKING -> stringResource(R.string.update_checking)
                            latest != null -> latest
                            else -> stringResource(R.string.new_ui_update_latest_unknown)
                        }),
                        style = if (hasUpdate) NewUiTypography.PrimaryActionTitle else NewUiTypography.SettingTitle,
                        color = if (hasUpdate) NewUiColors.HongqiRed else NewUiColors.TextPrimary,
                    )
                    val releaseTitle = state.releaseTitle?.takeIf { it.isNotBlank() }
                    if (latest != null && releaseTitle != null) {
                        Text(
                            releaseTitle,
                            style = NewUiTypography.ActionTitle,
                            color = NewUiColors.TextPrimary,
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    state.releaseDescription?.takeIf { it.isNotBlank() }?.let { description ->
                        Text(
                            description,
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextSecondary,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
            NewUiPanel(modifier = Modifier.padding(top = NewUiLayout.RowGap)) {
                NewUiToggleRow(
                    iconRes = R.drawable.new_ui_ic_update,
                    title = stringResource(R.string.settings_background_update_checks),
                    subtitle = stringResource(R.string.settings_background_update_checks_description),
                    checked = backgroundChecksEnabled,
                    onCheckedChange = onBackgroundChecksChanged,
                    showDivider = false,
                )
            }
            state.message?.let {
                Text(
                    it,
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary,
                    modifier = Modifier.padding(top = NewUiLayout.MainGap, bottom = 12.dp),
                )
            }
            when (state.stage) {
                NewUiUpdateStage.CHECKING -> {
                    Text(
                        stringResource(R.string.update_checking),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextMuted,
                        modifier = Modifier.padding(top = NewUiLayout.MainGap),
                    )
                }
                NewUiUpdateStage.DOWNLOADING -> {
                    Text(
                        stringResource(R.string.update_downloading, state.progress ?: 0),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextMuted,
                        modifier = Modifier.padding(top = NewUiLayout.MainGap),
                    )
                }
                NewUiUpdateStage.VERIFYING -> {
                    Text(
                        stringResource(R.string.update_verifying),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextMuted,
                        modifier = Modifier.padding(top = NewUiLayout.MainGap),
                    )
                }
                NewUiUpdateStage.READY -> {
                    state.savedPath?.let { path ->
                        Text(
                            stringResource(R.string.update_saved_to, path),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextMuted,
                            modifier = Modifier.padding(top = NewUiLayout.MainGap),
                        )
                    }
                }
                else -> Unit
            }
            if (!state.installPreparing && state.stage != NewUiUpdateStage.IDLE && state.stage != NewUiUpdateStage.FAILED && primaryLabel == null) {
                NewUiSecondaryActionButton(
                    label = stringResource(R.string.update_check),
                    modifier = Modifier.padding(top = NewUiLayout.MainGap),
                    onClick = onCheck,
                )
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
            primaryLabel = primaryLabel,
            onPrimary = primaryAction,
        )
    }
}
