package com.shilapi.xcertplay.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun NewUiSettingIcon(@DrawableRes iconRes: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(shape)
            .background(NewUiSurfaces.iconWellBrush(), shape)
            .border(1.dp, NewUiSurfaces.glassBorderSoft, shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = NewUiColors.Accent,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
fun NewUiPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .newUiGlassPanel(),
    ) {
        content()
    }
}

@Composable
fun NewUiToggleRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { if (enabled) onCheckedChange(!checked) }
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewUiSettingIcon(iconRes)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = NewUiTypography.SettingTitle,
                    color = if (enabled) NewUiColors.TextPrimary else NewUiColors.TextMuted,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            NewUiToggle(
                checked = checked,
                onCheckedChange = { if (enabled) onCheckedChange(it) },
            )
        }
        if (showDivider) {
            HorizontalDivider(color = NewUiSurfaces.glassDivider, thickness = 1.dp)
        }
    }
}

@Composable
fun NewUiValueRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String? = null,
    value: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewUiSettingIcon(iconRes)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = NewUiTypography.SettingTitle, color = NewUiColors.TextPrimary)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                value,
                style = NewUiTypography.ActionSubtitle,
                color = NewUiColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            NewUiChevron(
                tint = NewUiColors.TextMuted,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        if (showDivider) {
            HorizontalDivider(color = NewUiSurfaces.glassDivider, thickness = 1.dp)
        }
    }
}

@Composable
fun NewUiNavRow(
    @DrawableRes iconRes: Int,
    title: String,
    trailing: String? = null,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NewUiSettingIcon(iconRes)
            Spacer(Modifier.width(16.dp))
            Text(
                title,
                style = NewUiTypography.SettingTitle,
                color = NewUiColors.TextPrimary,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Text(
                    trailing,
                    style = NewUiTypography.ActionSubtitle,
                    color = NewUiColors.TextSecondary,
                    modifier = Modifier.padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NewUiChevron(tint = NewUiColors.TextMuted)
        }
        if (showDivider) {
            HorizontalDivider(color = NewUiSurfaces.glassDivider, thickness = 1.dp)
        }
    }
}

@Composable
fun NewUiSegmentedRow(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    showDivider: Boolean = false,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 22.dp),
            verticalAlignment = Alignment.Top,
        ) {
            NewUiSettingIcon(iconRes)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = NewUiTypography.SettingTitle, color = NewUiColors.TextPrimary)
                Text(
                    subtitle,
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 14.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    options.forEachIndexed { index, label ->
                        val selected = index == selectedIndex
                        val shape = RoundedCornerShape(10.dp)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(shape)
                                .then(
                                    if (selected) {
                                        Modifier
                                            .background(NewUiSurfaces.segmentSelectedBrush(), shape)
                                            .border(1.dp, NewUiSurfaces.hairlineBorder, shape)
                                    } else {
                                        Modifier
                                            .background(NewUiSurfaces.pillGlassBrush(), shape)
                                            .border(1.dp, NewUiSurfaces.glassBorderSoft, shape)
                                    },
                                )
                                .clickable { onSelect(index) }
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                label,
                                style = NewUiTypography.ActionTitle,
                                color = if (selected) NewUiColors.TextPrimary else NewUiColors.TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        if (showDivider) {
            HorizontalDivider(color = NewUiSurfaces.glassDivider, thickness = 1.dp)
        }
    }
}
