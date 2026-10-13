package com.shilapi.xcertplay.newui

import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R

private val SubpageFooterShape = RoundedCornerShape(12.dp)
private val SubpageFooterPaddingV = 22.dp

@Composable
fun NewUiSubpageTitle(@StringRes titleRes: Int) {
    Text(
        stringResource(titleRes),
        style = NewUiTypography.ActionTitle,
        color = NewUiColors.TextPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = NewUiLayout.MainGap),
    )
}

/** @deprecated Top back link; use [NewUiSubpageTitle] and [NewUiSubpageBottomBar]. */
@Composable
fun NewUiSubpageHeader(
    @StringRes backLabelRes: Int,
    @StringRes titleRes: Int,
    onBack: () -> Unit,
) {
    NewUiSubpageTitle(titleRes)
}

@Composable
fun NewUiSubpageBottomBar(
    backLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
) {
    val showPrimary = primaryLabel != null && onPrimary != null
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = NewUiLayout.MainGap),
        horizontalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewUiSubpageBackButton(
            label = backLabel,
            onClick = onBack,
            modifier = if (showPrimary) Modifier.weight(1f) else Modifier.fillMaxWidth(),
        )
        if (showPrimary) {
            NewUiSubpagePrimaryButton(
                label = primaryLabel!!,
                enabled = primaryEnabled,
                onClick = onPrimary!!,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
fun NewUiSubpageBottomBar(
    @StringRes backLabelRes: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    @StringRes primaryLabelRes: Int? = null,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
) {
    val resolvedPrimary = primaryLabel ?: primaryLabelRes?.let { stringResource(it) }
    NewUiSubpageBottomBar(
        backLabel = stringResource(backLabelRes),
        onBack = onBack,
        modifier = modifier,
        primaryLabel = resolvedPrimary,
        primaryEnabled = primaryEnabled,
        onPrimary = onPrimary,
    )
}

@Composable
private fun NewUiSubpageBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(SubpageFooterShape)
            .border(1.dp, NewUiSurfaces.hairlineBorder, SubpageFooterShape)
            .background(NewUiSurfaces.pillGlassBrush(), SubpageFooterShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = SubpageFooterPaddingV),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_dp_back),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NewUiColors.Accent),
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = label,
            style = NewUiTypography.SettingTitle,
            color = NewUiColors.Accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
fun NewUiSubpagePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = label,
        style = NewUiTypography.SettingTitle,
        color = NewUiColors.TextPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(SubpageFooterShape)
            .then(
                if (enabled) {
                    Modifier.background(NewUiSurfaces.accentGlossBrush(), SubpageFooterShape)
                } else {
                    Modifier.background(NewUiSurfaces.pillGlassBrush(), SubpageFooterShape)
                },
            )
            .border(1.dp, if (enabled) NewUiSurfaces.hairlineBorder else Color.Transparent, SubpageFooterShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = SubpageFooterPaddingV),
    )
}
