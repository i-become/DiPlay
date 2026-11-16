package com.shilapi.xcertplay.newui.connection

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiFormField
import com.shilapi.xcertplay.newui.NewUiSurfaces
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.orchestration.ManualHotspotValidation
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode

@Composable
fun NewUiConnectionMethodScreen(
    initial: NewUiConnectionMethodDraft,
    manualHotspotOff: Boolean,
    onBack: () -> Unit,
    onSave: (NewUiConnectionMethodDraft) -> Unit,
    onOpenCarHotspotSettings: () -> Unit,
    onOpenCarWifiSettings: () -> Unit,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    var showManualPassword by remember { mutableStateOf(false) }
    var showExistingPassword by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_connection_method_sheet_title)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ConnectionMethodOption(
                    modifier = Modifier.weight(1f),
                    selected = draft.mode == WirelessHotspotMode.MANUAL,
                    iconRes = R.drawable.new_ui_ic_connection_method,
                    titleRes = R.string.new_ui_connection_method_car_hotspot,
                    onClick = { draft = draft.copy(mode = WirelessHotspotMode.MANUAL) },
                )
                ConnectionMethodOption(
                    modifier = Modifier.weight(1f),
                    selected = draft.mode == WirelessHotspotMode.WIFI_P2P,
                    iconRes = R.drawable.new_ui_ic_stat_signal,
                    titleRes = R.string.wifi_direct,
                    onClick = { draft = draft.copy(mode = WirelessHotspotMode.WIFI_P2P) },
                )
                ConnectionMethodOption(
                    modifier = Modifier.weight(1f),
                    selected = draft.mode == WirelessHotspotMode.EXISTING_WIFI,
                    iconRes = R.drawable.new_ui_ic_stat_wifi,
                    titleRes = R.string.existing_wifi_title,
                    onClick = { draft = draft.copy(mode = WirelessHotspotMode.EXISTING_WIFI) },
                )
            }

            AnimatedVisibility(
                visible = draft.mode == WirelessHotspotMode.MANUAL,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MethodDetailPanel {
                    Text(
                        stringResource(R.string.hotspot_setup),
                        style = NewUiTypography.SettingTitle,
                        color = NewUiColors.TextPrimary,
                    )
                    NewUiFormField(
                        label = stringResource(R.string.hotspot_name),
                        value = draft.manualSsid,
                        onValueChange = { draft = draft.copy(manualSsid = it) },
                    )
                    if (ManualHotspotValidation.nameMayNotReachIphone(draft.manualSsid)) {
                        Text(
                            stringResource(R.string.settings_hotspot_name_non_english),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.HongqiRed,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    NewUiFormField(
                        label = stringResource(R.string.hotspot_password),
                        value = draft.manualPassword,
                        onValueChange = { draft = draft.copy(manualPassword = it) },
                        secret = !showManualPassword,
                        trailingLabel = if (showManualPassword) {
                            stringResource(R.string.new_ui_hide_password)
                        } else {
                            stringResource(R.string.show_password)
                        },
                        onTrailingClick = { showManualPassword = !showManualPassword },
                    )
                    val status = when {
                        !NewUiConnectionMethodActions.manualConfigured(draft.manualSsid, draft.manualPassword) ->
                            stringResource(R.string.new_ui_connection_method_manual_need)
                        manualHotspotOff -> stringResource(R.string.new_ui_connection_method_manual_off)
                        else -> stringResource(R.string.new_ui_connection_method_manual_ready)
                    }
                    Text(
                        status,
                        style = NewUiTypography.SettingSubtitle,
                        color = if (manualHotspotOff) NewUiColors.HongqiRed else NewUiColors.TextMuted,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    TextButton(onClick = onOpenCarHotspotSettings) {
                        Text(
                            stringResource(R.string.open_car_hotspot_settings),
                            color = NewUiColors.Accent,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = draft.mode == WirelessHotspotMode.WIFI_P2P,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MethodDetailPanel {
                    TextButton(onClick = onOpenCarWifiSettings) {
                        Text(
                            stringResource(R.string.open_car_wi_fi_settings),
                            color = NewUiColors.Accent,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = draft.mode == WirelessHotspotMode.EXISTING_WIFI,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                MethodDetailPanel {
                    NewUiFormField(
                        label = stringResource(R.string.existing_wifi_ssid),
                        value = draft.existingSsid,
                        onValueChange = { draft = draft.copy(existingSsid = it) },
                    )
                    NewUiFormField(
                        label = stringResource(R.string.existing_wifi_password),
                        value = draft.existingPassword,
                        onValueChange = { draft = draft.copy(existingPassword = it) },
                        secret = !showExistingPassword,
                        trailingLabel = if (showExistingPassword) {
                            stringResource(R.string.new_ui_hide_password)
                        } else {
                            stringResource(R.string.show_password)
                        },
                        onTrailingClick = { showExistingPassword = !showExistingPassword },
                    )
                    TextButton(onClick = onOpenCarWifiSettings) {
                        Text(
                            stringResource(R.string.open_car_wi_fi_settings),
                            color = NewUiColors.Accent,
                        )
                    }
                }
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_connection,
            onBack = onBack,
            primaryLabelRes = R.string.new_ui_connection_method_done,
            onPrimary = { onSave(draft) },
        )
    }
}

@Composable
private fun MethodDetailPanel(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NewUiSurfaces.panelBrush(), shape)
            .border(1.dp, NewUiSurfaces.glassBorderSoft, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun ConnectionMethodOption(
    modifier: Modifier = Modifier,
    selected: Boolean,
    @DrawableRes iconRes: Int,
    @StringRes titleRes: Int,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val borderColor = if (selected) NewUiColors.CardBorderGlow else NewUiSurfaces.glassBorderSoft
    Box(
        modifier = modifier
            .height(NewUiLayout.ActionRowHeight)
            .clip(shape)
            .background(
                if (selected) NewUiSurfaces.segmentSelectedBrush() else NewUiSurfaces.pillGlassBrush(),
                shape,
            )
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(20.dp)
                .clip(CircleShape)
                .border(
                    2.dp,
                    if (selected) NewUiColors.Accent else NewUiColors.TextMuted,
                    CircleShape,
                )
                .background(if (selected) NewUiColors.Accent else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(NewUiColors.OnAccent),
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (selected) NewUiColors.Accent else NewUiColors.TextSecondary,
                ),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(titleRes),
                style = NewUiTypography.LinkDetail,
                color = NewUiColors.TextPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
