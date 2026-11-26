package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.ClusterCopyRegion
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.*

// The editor occupies the entire application window, outside the shell and its padding.
// Controls float over the same coordinate space that is captured from the centre screen.
@Composable
fun NewUiClusterCopyRegionScreen(style: ClusterStyle, onBack: () -> Unit, onStartCapture: () -> Unit = {}) {
    val context = LocalContext.current
    var region by remember(style) { mutableStateOf(ClusterCopyRegion.load(context, style)) }
    val accent = NewUiColors.TextPrimary
    val background = if (NewUiColors.night) Color.Black else NewUiColors.Canvas
    val selectionFill = NewUiColors.CardSecondary
    val hintFill = NewUiColors.CardNormal.copy(alpha = .95f)
    fun save() = ClusterCopyRegion.save(context, region, style)
    Box(Modifier.fillMaxSize().background(background).testTag("copy-region-editor")) {
        Canvas(Modifier.fillMaxSize().testTag("copy-region-canvas").pointerInput(style) {
            awaitEachGesture {
                // Hit-test the finger-down position, before touch slop or a fast first
                // movement can turn a corner resize into an interior move.
                val down = awaitFirstDown()
                val corner = ClusterCopyRegionGesture.hit(region, down.position.x, down.position.y,
                    size.width.toFloat(), size.height.toFloat(), 48.dp.toPx())
                if (corner != -2) {
                    down.consume()
                    try {
                        drag(down.id) { change ->
                            val delta = change.positionChange()
                            region = ClusterCopyRegionGesture.drag(region, corner,
                                delta.x / size.width, delta.y / size.height)
                            change.consume()
                        }
                    } finally { save() }
                }
            }
        }) {
            val l = region.left * size.width; val t = region.top * size.height
            val r = region.right * size.width; val b = region.bottom * size.height
            drawRect(selectionFill, Offset(l, t), Size(r - l, b - t))
            drawRect(accent, Offset(l, t), Size(r - l, b - t), style = Stroke(3.dp.toPx()))
            val length = 24.dp.toPx()
            val stroke = 5.dp.toPx()
            listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b)).forEachIndexed { i, p ->
                drawCircle(background, 15.dp.toPx(), p)
                drawLine(accent, p, p + Offset(if (i % 2 == 0) length else -length, 0f), stroke)
                drawLine(accent, p, p + Offset(0f, if (i < 2) length else -length), stroke)
            }
            // A centre cross makes the movable interior discoverable without obscuring the frame.
            val c = Offset((l + r) / 2, (t + b) / 2)
            drawLine(accent.copy(alpha = .6f), c - Offset(12.dp.toPx(), 0f), c + Offset(12.dp.toPx(), 0f), 2.dp.toPx())
            drawLine(accent.copy(alpha = .6f), c - Offset(0f, 12.dp.toPx()), c + Offset(0f, 12.dp.toPx()), 2.dp.toPx())
        }
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 880.dp).background(hintFill)) {
            Text(stringResource(R.string.cluster_copy_description), color = NewUiColors.TextPrimary,
                style = NewUiTypography.SettingSubtitle, modifier = Modifier.padding(NewUiLayout.MainGap))
            NewUiSecondaryActionButton(stringResource(R.string.cluster_copy_authorize), onStartCapture)
        }
        Text(stringResource(R.string.new_ui_cluster_copy_region_placeholder),
            color = NewUiColors.TextPrimary, style = NewUiTypography.SettingSubtitle,
            modifier = Modifier.align(Alignment.BottomCenter).padding(NewUiLayout.MainGap)
                .background(hintFill, RoundedCornerShape(NewUiLayout.RowGap))
                .padding(NewUiLayout.MainGap))
        Row(Modifier.align(Alignment.Center).padding(NewUiLayout.MainGap)
            .widthIn(max = NewUiLayout.ClusterEditorToolbarMaxWidth).fillMaxWidth()
            .height(IntrinsicSize.Min)
            .testTag("copy-region-toolbar"),
            horizontalArrangement = Arrangement.spacedBy(NewUiLayout.NavTabGap)) {
            NewUiSecondaryActionButton(stringResource(R.string.new_ui_cluster_copy_region_fullscreen), {
                region = ClusterCopyRegion(0f, 0f, 1f, 1f); save()
            }, Modifier.weight(1f).heightIn(min = NewUiLayout.ClusterEditorButtonMinHeight).fillMaxHeight(),
                verticalPadding = NewUiLayout.ClusterEditorButtonPadding)
            NewUiSecondaryActionButton(stringResource(R.string.new_ui_cluster_copy_region_center), {
                region = ClusterCopyRegion(); save()
            }, Modifier.weight(1f).heightIn(min = NewUiLayout.ClusterEditorButtonMinHeight).fillMaxHeight(),
                verticalPadding = NewUiLayout.ClusterEditorButtonPadding)
            NewUiSecondaryActionButton(stringResource(R.string.new_ui_back_to_display), {
                save(); onBack()
            }, Modifier.weight(1f).heightIn(min = NewUiLayout.ClusterEditorButtonMinHeight).fillMaxHeight(),
                verticalPadding = NewUiLayout.ClusterEditorButtonPadding)
        }
    }
}

/** Stable gesture session: hit-test once, clamp the dragged edge without moving its opposite. */
internal object ClusterCopyRegionGesture {
    fun hit(r: ClusterCopyRegion, x: Float, y: Float, width: Float, height: Float, radius: Float): Int {
        val corners = listOf(r.left to r.top, r.right to r.top, r.left to r.bottom, r.right to r.bottom)
        val closest = corners.indices.minByOrNull { i ->
            val dx = x - corners[i].first * width; val dy = y - corners[i].second * height
            dx * dx + dy * dy
        } ?: return -2
        val dx = x - corners[closest].first * width; val dy = y - corners[closest].second * height
        if (dx * dx + dy * dy <= radius * radius) return closest
        return if (x >= r.left * width && x <= r.right * width && y >= r.top * height && y <= r.bottom * height) -1 else -2
    }

    fun drag(r: ClusterCopyRegion, corner: Int, dx: Float, dy: Float): ClusterCopyRegion {
        val minX = minOf(.06f, r.right - r.left)
        val minY = minOf(.06f, r.bottom - r.top)
        return when (corner) {
            0 -> r.copy(left = (r.left + dx).coerceIn(0f, r.right - minX), top = (r.top + dy).coerceIn(0f, r.bottom - minY))
            1 -> r.copy(right = (r.right + dx).coerceIn(r.left + minX, 1f), top = (r.top + dy).coerceIn(0f, r.bottom - minY))
            2 -> r.copy(left = (r.left + dx).coerceIn(0f, r.right - minX), bottom = (r.bottom + dy).coerceIn(r.top + minY, 1f))
            3 -> r.copy(right = (r.right + dx).coerceIn(r.left + minX, 1f), bottom = (r.bottom + dy).coerceIn(r.top + minY, 1f))
            -1 -> {
                val x = dx.coerceIn(-r.left, 1f - r.right)
                val y = dy.coerceIn(-r.top, 1f - r.bottom)
                r.copy(left = r.left + x, right = r.right + x, top = r.top + y, bottom = r.bottom + y)
            }
            else -> r
        }
    }
}
