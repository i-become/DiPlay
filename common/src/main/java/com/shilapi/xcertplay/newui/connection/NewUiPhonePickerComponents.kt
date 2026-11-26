package com.shilapi.xcertplay.newui.connection

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiSettingIcon
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiPhoneHintPanel(
    title: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    NewUiPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewUiSettingIcon(R.drawable.new_ui_ic_stat_bluetooth)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp),
            ) {
                Text(title, style = NewUiTypography.SettingTitle, color = NewUiColors.TextPrimary)
            }
            Text(
                actionLabel,
                style = NewUiTypography.ActionSubtitle,
                color = NewUiColors.Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
internal fun NewUiPhoneDeviceList(
    phones: List<NewUiBondedPhone>,
    selectedAddress: String?,
    onSelect: (NewUiBondedPhone) -> Unit,
) {
    if (phones.isEmpty()) return
    NewUiPanel {
        phones.forEachIndexed { index, phone ->
            NewUiPhoneDeviceRow(
                phone = phone,
                selected = phone.address == selectedAddress,
                onClick = { onSelect(phone) },
            )
            if (index < phones.lastIndex) {
                HorizontalDivider(color = NewUiColors.Divider)
            }
        }
    }
}

@Composable
internal fun NewUiPhoneDeviceRow(
    phone: NewUiBondedPhone,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewUiSettingIcon(R.drawable.new_ui_ic_phone_switch)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        ) {
            Text(
                phone.displayName,
                style = NewUiTypography.SettingTitle,
                color = NewUiColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            phone.subtitle?.let {
                Text(
                    it,
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
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
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(NewUiColors.OnAccent),
                )
            }
        }
    }
}

@Composable
fun NewUiPhonePrimaryFooter(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = NewUiTypography.ActionSubtitle,
        color = if (enabled) NewUiColors.OnAccent else NewUiColors.TextSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                top = NewUiLayout.MainGap,
                bottom = NewUiLayout.ScreenPaddingBottom,
            )
            .clip(RoundedCornerShape(12.dp))
            .background(if (enabled) NewUiColors.Accent else NewUiColors.PillButton)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}

@Composable
fun NewUiCurrentPhoneCard(name: String) {
    NewUiPanel {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewUiSettingIcon(R.drawable.new_ui_ic_phone_switch)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(
                    stringResource(R.string.new_ui_phone_current),
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextMuted,
                )
                Text(
                    name,
                    style = NewUiTypography.SettingTitle,
                    color = NewUiColors.TextPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun NewUiPhoneSecondaryLink(label: String, onClick: () -> Unit) {
    Text(
        label,
        style = NewUiTypography.ActionSubtitle,
        color = NewUiColors.Accent,
        modifier = Modifier
            .padding(top = NewUiLayout.RowGap)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
}
