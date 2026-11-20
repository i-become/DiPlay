package com.shilapi.xcertplay.newui.display

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import java.util.UUID
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.shilapi.xcertplay.*
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.*

/** Edits a private draft. Only Save changes the live instrument layout. */
@Composable
fun NewUiOriginalClusterConfigScreen(
    onBack: () -> Unit,
    registerExitGuard: (((() -> Unit) -> Unit)?) -> Unit = {},
) {
    val context = LocalContext.current
    val baseline = rememberSaveable { OriginalClusterSettings.load(context).encode() }
    var draftJson by rememberSaveable { mutableStateOf(baseline) }
    val draft = remember(draftJson) { OriginalClusterLayout.decode(draftJson) }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingType by rememberSaveable { mutableStateOf<String?>(null) }
    var replaceId by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(LocalMediaPicker()) { uri ->
        if (uri != null) {
            runCatching {
                val type = OriginalMediaType.valueOf(requireNotNull(pendingType))
                val item = OriginalClusterMedia(replaceId ?: UUID.randomUUID().toString(), type, uri.toString())
                val existing = draft.media.find { it.id == replaceId }
                val next = if (existing != null) draft.copy(media = draft.media.map {
                    if (it.id == existing.id) it.copy(uri = uri.toString()) else it
                }) else draft.copy(media = draft.media + item)
                draftJson = next.encode(); selectedId = item.id
            }.onFailure { Toast.makeText(context, R.string.settings_original_media_error, Toast.LENGTH_LONG).show() }
        }
        pendingType = null; replaceId = null
    }
    fun choose(type: OriginalMediaType, id: String? = null) {
        pendingType = type.name; replaceId = id
        runCatching { picker.launch(if (type == OriginalMediaType.IMAGE) "image/*" else "video/*") }
            .onFailure { Toast.makeText(context, R.string.settings_original_media_error, Toast.LENGTH_LONG).show() }
    }
    val controlsScroll = remember(selectedId) { ScrollState(0) }
    val dirty = draftJson != baseline
    var pendingExit by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    val requestExit: (() -> Unit) -> Unit = { exit ->
        if (dirty) { pendingExit = exit; showDiscard = true } else exit()
    }
    SideEffect { registerExitGuard(requestExit) }
    DisposableEffect(Unit) { onDispose { registerExitGuard(null) } }
    BackHandler { requestExit(onBack) }

    Column(Modifier.fillMaxSize().testTag("original-cluster-config")) {
        NewUiSubpageTitle(R.string.settings_original_cluster_config)
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NewUiLayout.ClusterEditorGap)) {
            Column(Modifier.weight(NewUiLayout.ClusterEditorPreviewWeight).fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                Text(stringResource(R.string.settings_original_cluster_preview_hint), style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    AndroidView(factory = { OriginalClusterScene(it, preview = true) },
                        update = { view ->
                            view.sceneLayout = draft; view.onLayoutChanged = { draftJson = it.encode() }
                            view.overlay.selectedId = selectedId
                            view.overlay.onSelectionChanged = { selectedId = it }
                        },
                        modifier = Modifier.width(minOf(maxWidth, maxHeight * ClusterStylePreview.ASPECT))
                            .aspectRatio(ClusterStylePreview.ASPECT).testTag("original-cluster-preview"))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(controlsScroll),
                verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                val component = OriginalClusterComponent.entries.find { it.key == selectedId }
                val media = draft.media.find { it.id == selectedId }
                val placement = component?.let { draft[it] } ?: media?.placement
                if (placement != null) {
                    NewUiSecondaryActionButton(stringResource(R.string.settings_original_components), { selectedId = null })
                    Text(if (component != null) stringResource(componentTitle(component)) else
                        stringResource(if (media?.type == OriginalMediaType.IMAGE) R.string.settings_original_image else R.string.settings_original_video),
                        color = NewUiColors.TextPrimary, style = NewUiTypography.SettingTitle)
                    fun update(p: OriginalClusterPlacement) {
                        draftJson = (if (component != null) draft.update(component, p)
                            else draft.updateMedia(requireNotNull(media).id, p)).encode()
                    }
                    if (component == OriginalClusterComponent.MUSIC) {
                        val music = draft.musicStyle
                        fun updateMusic(style: OriginalMusicStyle) {
                            draftJson = draft.updateMusicStyle(style).encode()
                        }
                        OriginalParameter(R.string.settings_original_music_title_size, music.titleSize, 12f..38f,
                            "${music.titleSize.toInt()} px") { updateMusic(music.copy(titleSize = it)) }
                        OriginalColorOptions(R.string.settings_original_music_title_color, music.titleColor)
                            { updateMusic(music.copy(titleColor = it)) }
                        NewUiCompactToggleRow(stringResource(R.string.settings_original_music_show_time), checked = music.showTime,
                            onCheckedChange = { updateMusic(music.copy(showTime = it)) })
                        NewUiCompactToggleRow(stringResource(R.string.settings_original_music_show_playback), checked = music.showPlaybackControls,
                            onCheckedChange = { updateMusic(music.copy(showPlaybackControls = it)) })
                        OriginalParameter(R.string.settings_original_music_artist_size, music.artistSize, 12f..24f,
                            "${music.artistSize.toInt()} px") { updateMusic(music.copy(artistSize = it)) }
                        OriginalColorOptions(R.string.settings_original_music_artist_color, music.artistColor)
                            { updateMusic(music.copy(artistColor = it)) }
                        OriginalParameter(R.string.settings_original_music_lyric_size, music.lyricSize, 18f..38f,
                            "${music.lyricSize.toInt()} px") { updateMusic(music.copy(lyricSize = it)) }
                        OriginalColorOptions(R.string.settings_original_music_lyric_color, music.lyricColor)
                            { updateMusic(music.copy(lyricColor = it)) }
                        OriginalParameter(R.string.settings_original_music_progress_length, music.progressLength, .35f..1f,
                            "${(music.progressLength * 100).toInt()}%") { updateMusic(music.copy(progressLength = it)) }
                        OriginalColorOptions(R.string.settings_original_music_progress_color, music.progressColor)
                            { updateMusic(music.copy(progressColor = it)) }
                        OriginalParameter(R.string.settings_original_music_cover_size, music.coverSize, 64f..288f,
                            "${music.coverSize.toInt()} px") { updateMusic(music.copy(coverSize = it)) }
                    }
                    val layerIndex = draft.layerIndex(selectedId!!)
                    Text(stringResource(R.string.settings_original_layer_position, layerIndex + 1, draft.orderedLayers.size),
                        color = NewUiColors.TextSecondary, style = NewUiTypography.SettingSubtitle)
                    Row(horizontalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_layer_bottom),
                            { draftJson = draft.moveLayerToEdge(selectedId!!, front = false).encode() },
                            modifier = Modifier.weight(1f), enabled = layerIndex > 0, verticalPadding = 10.dp)
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_layer_down),
                            { draftJson = draft.moveLayer(selectedId!!, -1).encode() },
                            modifier = Modifier.weight(1f), enabled = layerIndex > 0, verticalPadding = 10.dp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_layer_up),
                            { draftJson = draft.moveLayer(selectedId!!, 1).encode() },
                            modifier = Modifier.weight(1f), enabled = layerIndex < draft.orderedLayers.lastIndex, verticalPadding = 10.dp)
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_layer_top),
                            { draftJson = draft.moveLayerToEdge(selectedId!!, front = true).encode() },
                            modifier = Modifier.weight(1f), enabled = layerIndex < draft.orderedLayers.lastIndex, verticalPadding = 10.dp)
                    }
                    NewUiCompactToggleRow(stringResource(R.string.settings_original_visible), checked = placement.visible,
                        onCheckedChange = { update(placement.copy(visible = it)) })
                    if (media != null) {
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_media_stretch_fullscreen),
                            onClick = { draftJson = draft.stretchMediaFullScreen(media.id).encode() },
                            modifier = Modifier.testTag("original-media-fullscreen"))
                    }
                    OriginalParameter(R.string.settings_original_scale, placement.scale,
                        if (media != null) OriginalClusterMedia.MIN_SCALE..OriginalClusterMedia.MAX_SCALE else .2f..3f,
                        "${(placement.scale * 100).toInt()}%") { update(placement.copy(scale = it)) }
                    OriginalParameter(R.string.settings_original_rotation, placement.rotation, -180f..180f,
                        "${placement.rotation.toInt()}°") { update(placement.copy(rotation = it)) }
                    NewUiCompactToggleRow(stringResource(R.string.settings_original_flip_horizontal), checked = placement.flipHorizontal,
                        onCheckedChange = { update(placement.copy(flipHorizontal = it)) })
                    NewUiCompactToggleRow(stringResource(R.string.settings_original_flip_vertical), checked = placement.flipVertical,
                        onCheckedChange = { update(placement.copy(flipVertical = it)) })
                    OriginalParameter(R.string.settings_original_opacity, placement.opacity, 0f..1f,
                        "${(placement.opacity * 100).toInt()}%") { update(placement.copy(opacity = it)) }
                    if (media == null && component != OriginalClusterComponent.MAP)
                        OriginalParameter(R.string.settings_original_background, placement.backgroundOpacity, 0f..1f,
                            "${(placement.backgroundOpacity * 100).toInt()}%") { update(placement.copy(backgroundOpacity = it)) }
                    OriginalParameter(R.string.settings_original_x, placement.x, -1f..1f,
                        "${(placement.x * 100).toInt()}%") { update(placement.copy(x = it)) }
                    OriginalParameter(R.string.settings_original_y, placement.y, -1f..1f,
                        "${(placement.y * 100).toInt()}%") { update(placement.copy(y = it)) }
                    if (media != null) {
                        Text(media.uri.substringAfterLast('/'), color = NewUiColors.TextSecondary,
                            style = NewUiTypography.SettingSubtitle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_replace), { choose(media.type, media.id) })
                        NewUiSecondaryActionButton(stringResource(R.string.settings_original_remove), {
                            draftJson = draft.copy(media = draft.media.filterNot { it.id == media.id }).encode(); selectedId = null
                        })
                    }
                } else {
                    NewUiSecondaryActionButton(stringResource(R.string.settings_original_add_image), { choose(OriginalMediaType.IMAGE) })
                    NewUiSecondaryActionButton(stringResource(R.string.settings_original_add_video), { choose(OriginalMediaType.VIDEO) })
                    Text(stringResource(R.string.settings_original_select_hint), color = NewUiColors.TextSecondary,
                        style = NewUiTypography.SettingSubtitle)
                    OriginalClusterComponent.entries.forEach { item ->
                        Column(Modifier.testTag("original-toggle-" + item.key)) {
                            NewUiCompactToggleRow(title = stringResource(componentTitle(item)),
                                checked = draft[item].visible,
                                onCheckedChange = { draftJson = draft.show(item, it).encode() })
                            NewUiSecondaryActionButton(stringResource(R.string.settings_original_edit),
                                onClick = { selectedId = item.key },
                                modifier = Modifier.testTag("original-edit-${item.key}"))
                        }
                    }
                    draft.media.forEachIndexed { index, item ->
                        NewUiSecondaryActionButton("${stringResource(if (item.type == OriginalMediaType.IMAGE)
                            R.string.settings_original_image else R.string.settings_original_video)} ${index + 1}",
                            onClick = { selectedId = item.id })
                    }
                    NewUiSecondaryActionButton(stringResource(R.string.settings_original_cluster_reset),
                        onClick = { draftJson = OriginalClusterLayout().encode(); selectedId = null },
                        modifier = Modifier.testTag("original-cluster-reset"))
                }
            }
        }
        NewUiSubpageBottomBar(backLabel = stringResource(R.string.settings_original_cluster_back),
            onBack = { requestExit(onBack) }, primaryLabel = stringResource(R.string.settings_original_cluster_save),
            onPrimary = { OriginalClusterSettings.save(context, draft); onBack() },
            modifier = Modifier.testTag("original-cluster-actions"))
    }
    if (showDiscard) AlertDialog(
        onDismissRequest = { showDiscard = false; pendingExit = null },
        title = { Text(stringResource(R.string.settings_original_cluster_unsaved)) },
        text = { Text(stringResource(R.string.settings_original_cluster_discard_message)) },
        confirmButton = { TextButton(onClick = { showDiscard = false; (pendingExit ?: onBack).invoke(); pendingExit = null }) {
            Text(stringResource(R.string.settings_original_cluster_discard)) } },
        dismissButton = { TextButton(onClick = { showDiscard = false; pendingExit = null }) {
            Text(stringResource(R.string.settings_original_cluster_keep_editing)) } },
    )
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun OriginalColorOptions(title: Int, selected: Int, onSelect: (Int) -> Unit) {
    val label = stringResource(title)
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(selected, it) }
    var hue by rememberSaveable(title) { mutableFloatStateOf(hsv[0]) }
    LaunchedEffect(selected) {
        // White, grey and black have no hue; retain the last chosen hue for the next drag.
        if (hsv[1] > .001f && hsv[2] > .001f) hue = hsv[0]
    }
    val currentHue by rememberUpdatedState(hue)
    val updateColor by rememberUpdatedState(onSelect)
    Column(verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f), color = NewUiColors.TextPrimary, style = NewUiTypography.SettingSubtitle)
            Text("#%06X".format(selected and 0xFFFFFF), color = NewUiColors.TextSecondary,
                style = NewUiTypography.SettingSubtitle)
        }
        Canvas(Modifier.fillMaxWidth().height(NewUiLayout.ClusterEditorColorFieldHeight)
            .clip(RoundedCornerShape(NewUiLayout.ClusterEditorColorCorner))
            .semantics { contentDescription = label }
            .testTag("original-color-field-$title")
            .pointerInput(title) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun choose(position: Offset) {
                        val saturation = (position.x / size.width).coerceIn(0f, 1f)
                        val value = (1f - position.y / size.height).coerceIn(0f, 1f)
                        updateColor(android.graphics.Color.HSVToColor(floatArrayOf(currentHue, saturation, value)))
                    }
                    choose(down.position)
                    down.consume()
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (change.pressed) { choose(change.position); change.consume() }
                    } while (change.pressed)
                }
            }) {
            drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
            val radius = 7.dp.toPx()
            val inset = radius + 2.dp.toPx()
            val point = Offset((hsv[1] * size.width).coerceIn(inset, size.width - inset),
                ((1f - hsv[2]) * size.height).coerceIn(inset, size.height - inset))
            drawCircle(Color.Black.copy(alpha = .7f), radius + 2.dp.toPx(), point)
            drawCircle(Color.White, radius, point, style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        }
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Slider(value = hue, onValueChange = {
            hue = it
            updateColor(android.graphics.Color.HSVToColor(floatArrayOf(it, hsv[1], hsv[2])))
        }, valueRange = 0f..360f, modifier = Modifier.testTag("original-color-hue-$title")
            .semantics { contentDescription = label },
            colors = SliderDefaults.colors(thumbColor = Color.hsv(hue, 1f, 1f),
                activeTrackColor = NewUiColors.Accent, inactiveTrackColor = NewUiColors.ButtonNormal),
            track = {
                Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(Brush.horizontalGradient(listOf(Color.Red, Color.Yellow, Color.Green,
                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red))))
            }
        )
        }
    }
}

private fun componentTitle(component: OriginalClusterComponent) = when (component) {
    OriginalClusterComponent.MAP -> R.string.settings_original_map
    OriginalClusterComponent.TIRES -> R.string.settings_original_cluster_tires
    OriginalClusterComponent.DRIVING -> R.string.settings_original_cluster_driving
    OriginalClusterComponent.TURN -> R.string.settings_original_cluster_turn
    OriginalClusterComponent.TIME -> R.string.settings_original_cluster_time
    OriginalClusterComponent.MUSIC -> R.string.settings_original_cluster_music
}

@Composable
private fun OriginalParameter(title: Int, value: Float, range: ClosedFloatingPointRange<Float>,
    formatted: String, onChange: (Float) -> Unit) {
    Column {
        Text("${stringResource(title)} · $formatted", color = NewUiColors.TextPrimary,
            style = NewUiTypography.SettingSubtitle)
        Slider(value = value, onValueChange = onChange, valueRange = range,
            modifier = Modifier.testTag("original-parameter-$title"),
            colors = SliderDefaults.colors(thumbColor = NewUiColors.Accent,
                activeTrackColor = NewUiColors.Accent, inactiveTrackColor = NewUiColors.ButtonNormal))
    }
}
