package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.*

/** Static previews only: opening this page never starts a decoder or vehicle observer. */
@Composable
fun NewUiClusterManagerScreen(
    selectedStyle: ClusterStyle,
    onBack: () -> Unit,
    onApply: (ClusterStyle) -> Unit,
    onConfigureOriginal: () -> Unit = {},
    onConfigureCopyRegion: (ClusterStyle) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().testTag("cluster-manager")) {
        NewUiSubpageTitle(R.string.settings_cluster_manager)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NewUiLayout.ClusterCardGap),
        ) {
            Text(stringResource(R.string.settings_cluster_manager_description),
                color = NewUiColors.TextSecondary, style = NewUiTypography.SettingSubtitle)
            ClusterStyle.entries.forEach { style ->
                ClusterStyleCard(style, style == selectedStyle, onConfigureOriginal, onConfigureCopyRegion) { onApply(style) }
            }
        }
        NewUiSubpageBottomBar(backLabelRes = R.string.new_ui_back_to_display, onBack = onBack)
    }
}

@Composable
private fun ClusterStyleCard(style: ClusterStyle, selected: Boolean, onConfigureOriginal: () -> Unit,
    onConfigureCopyRegion: (ClusterStyle) -> Unit, onApply: () -> Unit) {
    val shape = RoundedCornerShape(NewUiLayout.PageCardRadius)
    BoxWithConstraints(
        Modifier.fillMaxWidth().clip(shape).background(NewUiColors.CardNormal)
            .border(if (selected) NewUiLayout.ClusterSelectionBorder else NewUiLayout.ClusterCardBorder,
                if (selected) NewUiColors.Accent.copy(alpha = .8f) else NewUiColors.CardBorder, shape)
            .padding(NewUiLayout.ClusterCardPadding).testTag("cluster-card-" + style.preferenceValue),
    ) {
        // Large text receives the same stacked layout as a narrow head-unit window.
        val wide = maxWidth >= NewUiLayout.ClusterCardWideThreshold && LocalDensity.current.fontScale <= 1.5f
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(NewUiLayout.ClusterCardGap),
                verticalAlignment = Alignment.CenterVertically) {
                ClusterStylePreviewImage(style, Modifier.weight(1.4f))
                ClusterStyleDetails(style, selected, onApply, onConfigureOriginal, onConfigureCopyRegion, Modifier.weight(1f))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                ClusterStylePreviewImage(style, Modifier.fillMaxWidth())
                ClusterStyleDetails(style, selected, onApply, onConfigureOriginal, onConfigureCopyRegion, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ClusterStylePreviewImage(style: ClusterStyle, modifier: Modifier) {
    val context = LocalContext.current
    val bitmap = remember(context, style) { ClusterStylePreview.create(context, style) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    // Compose may retain this bitmap in a render node after disposal; let GC release it.
    Image(image, contentDescription = stringResource(R.string.settings_cluster_style_preview,
        stringResource(style.titleRes)), contentScale = ContentScale.Fit,
        modifier = modifier.widthIn(max = NewUiLayout.ClusterPreviewMaxWidth)
            .aspectRatio(ClusterStylePreview.ASPECT)
            .clip(RoundedCornerShape(NewUiLayout.CardRadius)))
}

@Composable
private fun ClusterStyleDetails(style: ClusterStyle, selected: Boolean, onApply: () -> Unit, onConfigureOriginal: () -> Unit, onConfigureCopyRegion: (ClusterStyle) -> Unit, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
        Text(stringResource(style.titleRes), color = NewUiColors.TextPrimary,
            style = NewUiTypography.ActionTitle)
        Text(stringResource(style.descriptionRes), color = NewUiColors.TextSecondary,
            style = NewUiTypography.SettingSubtitle)
        if (style == ClusterStyle.ORIGINAL) Text(
            text = stringResource(R.string.settings_cluster_style_original_author),
            color = NewUiColors.TextSecondary,
            style = NewUiTypography.SettingSubtitle,
        )
        NewUiAccentActionButton(
            label = stringResource(if (selected) R.string.settings_cluster_style_applied
                else R.string.settings_cluster_style_apply),
            enabled = !selected, onClick = onApply,
            modifier = Modifier.testTag("cluster-apply-" + style.preferenceValue),
        )
        if (style == ClusterStyle.ORIGINAL) NewUiSecondaryActionButton(
            label = stringResource(R.string.settings_original_cluster_config),
            onClick = onConfigureOriginal, modifier = Modifier.testTag("cluster-configure-original"))
        NewUiSecondaryActionButton(
            label = stringResource(R.string.new_ui_cluster_copy_region), onClick = { onConfigureCopyRegion(style) },
            modifier = Modifier.testTag("cluster-copy-region-config-${style.preferenceValue}"))
    }
}

