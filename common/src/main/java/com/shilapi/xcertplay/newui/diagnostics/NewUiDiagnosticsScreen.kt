package com.shilapi.xcertplay.newui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.RemovableStorageVolume
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiToggleRow
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiChevron
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiSettingIcon
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiDiagnosticsScreen(
    sessionLogRecordingEnabled: Boolean,
    debugSimulatedClusterEnabled: Boolean,
    storagePath: String,
    reports: List<NewUiDiagnosticFile>,
    saving: Boolean,
    usbSaveRoots: List<RemovableStorageVolume>,
    onSessionLogRecordingChanged: (Boolean) -> Unit,
    onClearSessionLogs: () -> Unit,
    onDebugSimulatedClusterChanged: (Boolean) -> Unit,
    onSaveReport: () -> Unit,
    onSaveReportToUsb: (String) -> Unit,
    onViewReport: (NewUiDiagnosticFile) -> Unit,
    onShareReport: (NewUiDiagnosticFile) -> Unit,
    onDeleteReport: (NewUiDiagnosticFile) -> Unit,
    onEditStorage: () -> Unit,
    onScanDownload: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
    ) {
        NewUiPanel {
            NewUiToggleRow(
                iconRes = R.drawable.new_ui_ic_file,
                title = stringResource(R.string.new_ui_diag_log_record_title),
                subtitle = stringResource(R.string.new_ui_diag_log_record_sub),
                checked = sessionLogRecordingEnabled,
                onCheckedChange = onSessionLogRecordingChanged,
                showDivider = false,
            )
            NewUiSecondaryActionButton(
                label = stringResource(R.string.new_ui_diag_log_clear),
                onClick = onClearSessionLogs,
                modifier = Modifier
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 20.dp),
            )
        }
        NewUiPanel {
            NewUiToggleRow(
                iconRes = R.drawable.ic_dp_display,
                title = stringResource(R.string.new_ui_diag_simulated_cluster_title),
                subtitle = stringResource(R.string.new_ui_diag_simulated_cluster_sub),
                checked = debugSimulatedClusterEnabled,
                onCheckedChange = onDebugSimulatedClusterChanged,
                showDivider = false,
            )
        }
        NewUiPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewUiSettingIcon(R.drawable.ic_dp_diagnostics)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.new_ui_diag_save_title),
                        style = NewUiTypography.SettingTitle,
                        color = NewUiColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.new_ui_diag_save_sub),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                val shape = RoundedCornerShape(10.dp)
                Text(
                    text = if (saving) {
                        stringResource(R.string.saving_report)
                    } else {
                        stringResource(R.string.save_diagnostic_report)
                    },
                    style = NewUiTypography.ActionSubtitle,
                    color = NewUiColors.TextPrimary,
                    modifier = Modifier
                        .clip(shape)
                        .background(NewUiColors.Accent)
                        .clickable(enabled = !saving, onClick = onSaveReport)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
        NewUiPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScanDownload)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewUiSettingIcon(R.drawable.new_ui_ic_share)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.new_ui_diag_qr_title),
                        style = NewUiTypography.SettingTitle,
                        color = NewUiColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.new_ui_diag_qr_sub),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                NewUiChevron(tint = NewUiColors.TextMuted)
            }
        }
        if (usbSaveRoots.isNotEmpty()) {
            NewUiPanel {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 18.dp)) {
                    Text(
                        stringResource(R.string.new_ui_diag_storage_usb_hint),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
                    )
                    usbSaveRoots.forEach { volume ->
                        NewUiSecondaryActionButton(
                            label = stringResource(R.string.new_ui_diag_save_usb, volume.label),
                            enabled = !saving,
                            onClick = { onSaveReportToUsb(volume.rootPath) },
                            modifier = Modifier.padding(bottom = NewUiLayout.RowGap),
                        )
                    }
                }
            }
        }
        NewUiPanel {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditStorage)
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NewUiSettingIcon(R.drawable.new_ui_ic_folder)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.new_ui_diag_storage_title),
                        style = NewUiTypography.SettingTitle,
                        color = NewUiColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.new_ui_diag_storage_path, storagePath),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                NewUiChevron(tint = NewUiColors.TextMuted)
            }
        }
        Text(
            stringResource(R.string.new_ui_diag_recent),
            style = NewUiTypography.ActionTitle,
            color = NewUiColors.TextPrimary,
            modifier = Modifier.padding(start = 4.dp, top = 4.dp),
        )
        NewUiPanel {
            if (reports.isEmpty()) {
                Text(
                    stringResource(R.string.new_ui_diag_empty),
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextMuted,
                    modifier = Modifier.padding(22.dp),
                )
            } else {
                reports.forEachIndexed { index, file ->
                    DiagnosticFileRow(
                        file = file,
                        onView = { onViewReport(file) },
                        onShare = { onShareReport(file) },
                        onDelete = { onDeleteReport(file) },
                    )
                    if (index < reports.lastIndex) {
                        HorizontalDivider(color = NewUiColors.Divider)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticFileRow(
    file: NewUiDiagnosticFile,
    onView: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewUiSettingIcon(R.drawable.new_ui_ic_file)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = NewUiTypography.SettingTitle,
                color = NewUiColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${file.timeLabel} · ${file.sizeLabel}",
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DiagnosticIconButton(R.drawable.new_ui_ic_view, stringResource(R.string.new_ui_diag_view), onView)
            DiagnosticIconButton(R.drawable.new_ui_ic_share, stringResource(R.string.share), onShare)
            DiagnosticIconButton(R.drawable.new_ui_ic_trash, stringResource(R.string.new_ui_diag_delete), onDelete)
        }
    }
}

@Composable
private fun DiagnosticIconButton(iconRes: Int, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Icon(
        painter = painterResource(iconRes),
        contentDescription = label,
        tint = NewUiColors.Accent,
        modifier = Modifier
            .size(36.dp)
            .clip(shape)
            .border(1.dp, NewUiColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    )
}
