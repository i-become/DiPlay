package com.shilapi.xcertplay.newui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.RemovableStorageVolume
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiChevron
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiCompactNavRow
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSettingIcon
import com.shilapi.xcertplay.newui.NewUiSplitPage
import com.shilapi.xcertplay.newui.NewUiToggle
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.newUiGlassPanel

@Composable
fun NewUiDiagnosticsScreen(
    sessionLogRecordingEnabled: Boolean,
    storagePath: String,
    reports: List<NewUiDiagnosticFile>,
    saving: Boolean,
    usbSaveRoots: List<RemovableStorageVolume>,
    onSessionLogRecordingChanged: (Boolean) -> Unit,
    onClearSessionLogs: () -> Unit,
    onSaveReport: () -> Unit,
    onSaveReportToUsb: (String) -> Unit,
    onViewReport: (NewUiDiagnosticFile) -> Unit,
    onShareReport: (NewUiDiagnosticFile) -> Unit,
    onDeleteReport: (NewUiDiagnosticFile) -> Unit,
    onEditStorage: () -> Unit,
    onScanDownload: () -> Unit,
) {
    NewUiSplitPage(
        leftWeight = 0.42f,
        rightWeight = 0.58f,
        left = {
            Column(
                Modifier
                    .fillMaxHeight()
                    .newUiGlassPanel(cornerRadius = NewUiLayout.PageCardRadius)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.new_ui_diag_log_record_title),
                            style = NewUiTypography.CompactSettingTitle,
                            color = NewUiColors.TextPrimary,
                        )
                        Text(
                            stringResource(R.string.new_ui_diag_log_record_sub),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextSecondary,
                        )
                    }
                    NewUiToggle(
                        checked = sessionLogRecordingEnabled,
                        onCheckedChange = onSessionLogRecordingChanged,
                    )
                }
                NewUiSecondaryActionButton(
                    label = stringResource(R.string.new_ui_diag_log_clear),
                    onClick = onClearSessionLogs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 16.dp),
                )
                HorizontalDivider(color = NewUiColors.Divider.copy(alpha = 0.4f))
                val saveShape = RoundedCornerShape(12.dp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .heightIn(min = 56.dp)
                        .clip(saveShape)
                        .background(NewUiColors.Accent)
                        .clickable(enabled = !saving && sessionLogRecordingEnabled, onClick = onSaveReport),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (saving) {
                            stringResource(R.string.saving_report)
                        } else {
                            stringResource(R.string.save_diagnostic_report)
                        },
                        style = NewUiTypography.SecondaryActionTitle,
                        color = NewUiColors.OnAccent,
                    )
                }
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_diag_qr_title),
                    subtitle = stringResource(R.string.new_ui_diag_qr_sub),
                    onClick = { if (sessionLogRecordingEnabled) onScanDownload() },
                )
                NewUiCompactNavRow(
                    title = stringResource(R.string.new_ui_diag_storage_title),
                    subtitle = storagePath,
                    onClick = onEditStorage,
                    showDivider = false,
                )
                if (usbSaveRoots.isNotEmpty()) {
                    Text(
                        stringResource(R.string.new_ui_diag_storage_usb_hint),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
                    )
                    usbSaveRoots.forEach { volume ->
                        NewUiSecondaryActionButton(
                            label = stringResource(R.string.new_ui_diag_save_usb, volume.label),
                            enabled = !saving && sessionLogRecordingEnabled,
                            onClick = { onSaveReportToUsb(volume.rootPath) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                        )
                    }
                }
            }
        },
        right = {
            Column(
                Modifier
                    .fillMaxHeight()
                    .newUiGlassPanel(cornerRadius = NewUiLayout.PageCardRadius),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.new_ui_diag_recent),
                        style = NewUiTypography.NavTab,
                        color = NewUiColors.TextPrimary,
                    )
                    Text(
                        stringResource(R.string.new_ui_diag_report_count, reports.size),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                    )
                }
                HorizontalDivider(color = NewUiColors.Divider.copy(alpha = 0.4f))
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (reports.isEmpty()) {
                        Text(
                            stringResource(R.string.new_ui_diag_empty),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextMuted,
                            modifier = Modifier.padding(24.dp),
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
                                HorizontalDivider(
                                    color = NewUiColors.Divider.copy(alpha = 0.3f),
                                    modifier = Modifier.padding(horizontal = 24.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
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
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewUiSettingIcon(R.drawable.new_ui_ic_file)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                file.name,
                style = NewUiTypography.CompactSettingTitle,
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
            DiagnosticIconButton(
                R.drawable.new_ui_ic_trash,
                stringResource(R.string.new_ui_diag_delete),
                onDelete,
                tint = NewUiColors.Danger,
            )
        }
    }
}

@Composable
private fun DiagnosticIconButton(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    tint: Color = NewUiColors.Accent,
) {
    val shape = RoundedCornerShape(8.dp)
    Icon(
        painter = painterResource(iconRes),
        contentDescription = label,
        tint = tint,
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .border(1.dp, NewUiColors.CardBorder, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
    )
}
