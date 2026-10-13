package com.shilapi.xcertplay.newui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun NewUiSingleChoicePanel(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    showDividerOnLast: Boolean = true,
) {
    NewUiPanel {
        options.forEachIndexed { index, label ->
            NewUiSingleChoiceRow(
                label = label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
            )
            val showDivider = index < options.lastIndex || showDividerOnLast
            if (showDivider && index < options.lastIndex) {
                HorizontalDivider(color = NewUiSurfaces.glassDivider, thickness = 1.dp)
            }
        }
    }
}

@Composable
fun NewUiSingleChoiceRow(
    label: String,
    subtitle: String? = null,
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
        Column(Modifier.weight(1f)) {
            Text(label, style = NewUiTypography.SettingTitle, color = NewUiColors.TextPrimary)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        val shape = CircleShape
        Row(
            modifier = Modifier
                .size(22.dp)
                .clip(shape)
                .border(
                    2.dp,
                    if (selected) NewUiColors.Accent else NewUiColors.CardBorder,
                    shape,
                )
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selected) {
                Row(
                    Modifier
                        .size(12.dp)
                        .clip(shape)
                        .background(NewUiColors.Accent),
                ) {}
            }
        }
    }
}

@Composable
fun NewUiAccentActionButton(
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = NewUiTypography.ActionSubtitle,
        color = NewUiColors.TextPrimary,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (enabled) {
                    Modifier.background(NewUiSurfaces.accentGlossBrush(), shape)
                } else {
                    Modifier.background(NewUiSurfaces.pillGlassBrush(), shape)
                },
            )
            .border(1.dp, if (enabled) NewUiSurfaces.hairlineBorder else Color.Transparent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}

@Composable
fun NewUiSecondaryActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        style = NewUiTypography.ActionSubtitle,
        color = if (enabled) NewUiColors.Accent else NewUiColors.TextMuted,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(NewUiSurfaces.pillGlassBrush(), shape)
            .border(1.dp, NewUiSurfaces.glassBorderSoft, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 16.dp),
    )
}
