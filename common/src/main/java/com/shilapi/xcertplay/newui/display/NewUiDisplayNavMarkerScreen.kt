package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.Slider
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSurfaces
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.newUiAccentGloss
import com.shilapi.xcertplay.newui.newUiGlassTile
import com.shilapi.xcertplay.newui.newUiPressScale
import kotlin.math.roundToInt

@Composable
fun NewUiDisplayNavMarkerScreen(
    xPercent: Int,
    yPercent: Int,
    onBack: () -> Unit,
    onSave: (x: Int, y: Int) -> Unit,
    onResetDefault: () -> Unit,
    navigationCrop: Int = 0,
    onNavigationCrop: (Int) -> Unit = {},
    navigationZoom: Int = 100,
    onNavigationZoom: (Int) -> Unit = {},
) {
    var editing by remember { mutableStateOf(false) }
    var draftX by remember { mutableIntStateOf(xPercent) }
    var draftY by remember { mutableIntStateOf(yPercent) }
    SideEffect {
        if (!editing) {
            draftX = xPercent
            draftY = yPercent
        }
    }
    val showX = if (editing) draftX else xPercent
    val showY = if (editing) draftY else yPercent
    val previewShape = RoundedCornerShape(NewUiLayout.HeroPanelRadius)
    val previewBorder = if (editing) NewUiColors.PrimaryBlue else NewUiColors.Divider

    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight()
                .clip(previewShape)
                .border(
                    width = if (editing) 2.dp else 1.dp,
                    color = previewBorder,
                    shape = previewShape,
                )
                .newUiGlassTile(cornerRadius = NewUiLayout.HeroPanelRadius)
                .padding(12.dp),
        ) {
            NavMarkerMapPreview(
                xPercent = showX,
                yPercent = showY,
                editing = editing,
                onMarkerMoved = { x, y ->
                    draftX = x
                    draftY = y
                },
                modifier = Modifier.fillMaxSize(),
            )
            if (!editing) {
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    NavMarkerLegendChip(
                        dotColor = NewUiColors.PrimaryBlue,
                        label = stringResource(R.string.new_ui_display_nav_marker_legend_center),
                    )
                    NavMarkerLegendChip(
                        dotColor = Color(0xFFFFB347),
                        label = stringResource(R.string.new_ui_display_nav_marker_legend_marker),
                    )
                }
            }
        }
        val settingsScroll = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight()
                .verticalScroll(settingsScroll),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            ) {
                Text(
                    stringResource(R.string.new_ui_display_nav_marker),
                    style = NewUiTypography.ActionTitle,
                    color = NewUiColors.TextPrimary,
                )
                if (editing) {
                    Text(
                        stringResource(R.string.new_ui_display_nav_marker_editing),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.PrimaryBlue,
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, NewUiColors.PrimaryBlue.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(NewUiLayout.PageCardRadius))
                    .border(1.dp, NewUiSurfaces.glassBorderSoft, RoundedCornerShape(NewUiLayout.PageCardRadius))
                    .newUiGlassTile(cornerRadius = NewUiLayout.PageCardRadius),
            ) {
                NavMarkerStatRow(
                    title = stringResource(R.string.new_ui_display_nav_marker_horizontal),
                    value = stringResource(R.string.new_ui_display_nav_marker_percent, showX),
                    highlight = editing,
                )
                NavMarkerStatRow(
                    title = stringResource(R.string.new_ui_display_nav_marker_vertical),
                    value = stringResource(R.string.new_ui_display_nav_marker_percent, showY),
                    highlight = editing,
                    showDivider = false,
                )
                if (!editing) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NavMarkerPrimaryButton(
                            label = stringResource(R.string.new_ui_display_nav_marker_edit),
                            onClick = { editing = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        NewUiSecondaryActionButton(
                            label = stringResource(R.string.new_ui_display_nav_marker_reset),
                            onClick = onResetDefault,
                        )
                    }
                }
            }
            NavigationCropCard(navigationCrop, onNavigationCrop)
            NavigationZoomCard(navigationZoom, onNavigationZoom)
            if (editing) {
                NewUiSubpageBottomBar(
                    backLabel = stringResource(R.string.cancel),
                    onBack = {
                        editing = false
                        draftX = xPercent
                        draftY = yPercent
                    },
                    primaryLabel = stringResource(R.string.save),
                    onPrimary = {
                        onSave(draftX, draftY)
                        editing = false
                    },
                    modifier = Modifier.padding(top = NewUiLayout.MainGap),
                )
            } else {
                NewUiSubpageBottomBar(
                    backLabelRes = R.string.new_ui_back_to_display,
                    onBack = onBack,
                    modifier = Modifier.padding(top = NewUiLayout.MainGap),
                )
            }
        }
    }
}

@Composable
private fun NavigationCropCard(value: Int, onSave: (Int) -> Unit) {
    var draft by remember(value) { mutableIntStateOf(value) }
    val title = stringResource(R.string.settings_cluster_navigation_crop)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = NewUiLayout.MainGap)
            .testTag("navigation-crop")
            .newUiGlassTile(cornerRadius = NewUiLayout.PageCardRadius)
            .padding(NewUiLayout.ClusterCardPadding),
        verticalArrangement = Arrangement.spacedBy(NewUiLayout.MainGap),
    ) {
        Text(title, style = NewUiTypography.CompactSettingTitle, color = NewUiColors.TextPrimary)
        Text(stringResource(R.string.settings_cluster_navigation_crop_help),
            style = NewUiTypography.SettingSubtitle, color = NewUiColors.TextSecondary)
        Text(stringResource(R.string.settings_cluster_navigation_crop_value, draft),
            style = NewUiTypography.ActionSubtitle, color = NewUiColors.TextPrimary)
        Slider(
            value = draft.toFloat(),
            onValueChange = { draft = it.roundToInt(); onSave(draft) },
            valueRange = -60f..60f,
            steps = 119,
            modifier = Modifier.fillMaxWidth().testTag("navigation-crop-slider")
                .semantics { contentDescription = title },
        )
        NewUiSecondaryActionButton(
            label = stringResource(R.string.settings_cluster_navigation_crop_reset),
            onClick = { draft = 0; onSave(0) },
            modifier = Modifier.fillMaxWidth().testTag("navigation-crop-reset"),
        )
    }
}

@Composable
private fun NavigationZoomCard(value: Int, onSave: (Int) -> Unit) {
    val zoomTitle = stringResource(R.string.settings_cluster_navigation_zoom)
    Column(Modifier.fillMaxWidth().padding(top = NewUiLayout.MainGap)
        .newUiGlassTile(cornerRadius = NewUiLayout.PageCardRadius)
        .padding(NewUiLayout.ClusterCardPadding),
        verticalArrangement = Arrangement.spacedBy(NewUiLayout.MainGap)) {
        Text(stringResource(R.string.settings_cluster_navigation_zoom),
            style = NewUiTypography.CompactSettingTitle, color = NewUiColors.TextPrimary)
        Text(stringResource(R.string.settings_cluster_navigation_zoom_help),
            style = NewUiTypography.SettingSubtitle, color = NewUiColors.TextSecondary)
        Text("${value}%", style = NewUiTypography.ActionSubtitle, color = NewUiColors.TextPrimary)
        Slider(value = value.toFloat(), onValueChange = { onSave(it.roundToInt()) },
            valueRange = 50f..200f, steps = 149,
            modifier = Modifier.fillMaxWidth().testTag("navigation-zoom-slider")
                .semantics { contentDescription = zoomTitle })
        NewUiSecondaryActionButton(label = stringResource(R.string.settings_cluster_navigation_crop_reset),
            onClick = { onSave(100) }, modifier = Modifier.fillMaxWidth().testTag("navigation-zoom-reset"))
    }
}

@Composable
private fun NavMarkerLegendChip(dotColor: Color, label: String) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, NewUiColors.Divider.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dotColor)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
        )
        Text(label, style = NewUiTypography.SettingSubtitle, color = NewUiColors.TextSecondary)
    }
}

@Composable
private fun NavMarkerStatRow(
    title: String,
    value: String,
    highlight: Boolean,
    showDivider: Boolean = true,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = NewUiLayout.CompactRowHeight)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = NewUiTypography.CompactSettingTitle, color = NewUiColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(
                value,
                style = NewUiTypography.ActionSubtitle,
                color = if (highlight) NewUiColors.PrimaryBlue else NewUiColors.TextSecondary,
            )
        }
        if (showDivider) {
            HorizontalDivider(
                color = NewUiSurfaces.glassDivider.copy(alpha = 0.4f),
                thickness = 1.dp,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
    }
}

@Composable
private fun NavMarkerPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(NewUiLayout.ActionButtonRadius)
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .heightIn(min = NewUiLayout.ActionButtonMinHeight)
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = NewUiColors.PrimaryGlowSoft,
                spotColor = NewUiColors.PrimaryGlowStrong,
            )
            .newUiAccentGloss(cornerRadius = NewUiLayout.ActionButtonRadius)
            .clip(shape)
            .newUiPressScale(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = NewUiTypography.ActionTitle,
            color = NewUiColors.TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun NavMarkerMapPreview(
    xPercent: Int,
    yPercent: Int,
    editing: Boolean,
    onMarkerMoved: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = Color(0xFF263C5A).copy(alpha = 0.35f)
    val roadColor = Color(0xFF2A4568)
    val roadMain = Color(0xFF3D5F8A)
    val crossColor = NewUiColors.PrimaryBlue.copy(alpha = if (editing) 0.55f else 0.4f)
    val markerSize = if (editing) 52.dp else 48.dp
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = modifier
            .then(
                if (editing) {
                    Modifier.pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val w = size.width.toFloat().coerceAtLeast(1f)
                            val h = size.height.toFloat().coerceAtLeast(1f)
                            val nx = ClusterTurnCardOverlay.snap(
                                (change.position.x / w * 100f).roundToInt().coerceIn(5, 95),
                                CarPlayClusterDisplay.markerXPercents,
                            )
                            val ny = ClusterTurnCardOverlay.snap(
                                (change.position.y / h * 100f).roundToInt().coerceIn(5, 95),
                                CarPlayClusterDisplay.markerYPercents,
                            )
                            onMarkerMoved(nx, ny)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.TopStart,
    ) {
        val mapWidth = maxWidth
        val mapHeight = maxHeight
        Canvas(Modifier.fillMaxSize()) {
                    val step = 40.dp.toPx()
                    var y = 0f
                    while (y < size.height) {
                        drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                        y += step
                    }
                    var x = 0f
                    while (x < size.width) {
                        drawLine(gridColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                        x += step
                    }
                    val path = Path().apply {
                        moveTo(size.width * 0.1f, size.height * 0.74f)
                        cubicTo(
                            size.width * 0.27f, size.height * 0.67f,
                            size.width * 0.42f, size.height * 0.35f,
                            size.width * 0.62f, size.height * 0.28f,
                        )
                        cubicTo(
                            size.width * 0.82f, size.height * 0.21f,
                            size.width * 0.92f, size.height * 0.15f,
                            size.width * 0.96f, size.height * 0.12f,
                        )
                    }
            drawPath(path, roadMain, style = Stroke(width = 26f, cap = StrokeCap.Round))
            drawLine(
                roadColor,
                Offset(0f, size.height * 0.5f),
                Offset(size.width, size.height * 0.5f),
                strokeWidth = 16f,
                cap = StrokeCap.Round,
            )
            drawLine(
                roadColor,
                Offset(size.width * 0.5f, 0f),
                Offset(size.width * 0.5f, size.height),
                strokeWidth = 16f,
                cap = StrokeCap.Round,
            )
            val cx = size.width * 0.5f
            val cy = size.height * 0.5f
            val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
            drawLine(crossColor, Offset(cx, cy - 80f), Offset(cx, cy + 80f), strokeWidth = 2f, pathEffect = dashEffect)
            drawLine(crossColor, Offset(cx - 80f, cy), Offset(cx + 80f, cy), strokeWidth = 2f, pathEffect = dashEffect)
        }
        Box(
            modifier = Modifier
                .size(markerSize)
                .offset {
                    with(density) {
                        IntOffset(
                            (mapWidth.toPx() * xPercent / 100f - markerSize.toPx() / 2f).roundToInt(),
                            (mapHeight.toPx() * yPercent / 100f - markerSize.toPx() / 2f).roundToInt(),
                        )
                    }
                }
                .then(
                    if (editing) {
                        Modifier.border(3.dp, NewUiColors.PrimaryBlue.copy(alpha = 0.45f), CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(Color(0xFFFFB347))
                    .border(2.dp, Color.White.copy(alpha = 0.9f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_maneuver_straight),
                    contentDescription = null,
                    tint = Color(0xFF071426),
                    modifier = Modifier
                        .size(26.dp)
                        .rotate(45f),
                )
            }
        }
    }
}
