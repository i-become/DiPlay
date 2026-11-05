package com.shilapi.xcertplay.newui.display

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
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
                    AndroidView(factory = { OriginalClusterOverlayView(it, preview = true) },
                        update = { view -> view.layout = draft; view.onLayoutChanged = { draftJson = it.encode() } },
                        modifier = Modifier.width(minOf(maxWidth, maxHeight * ClusterStylePreview.ASPECT))
                            .aspectRatio(ClusterStylePreview.ASPECT).testTag("original-cluster-preview"))
                }
            }
            Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap)) {
                Column {
                    OriginalClusterComponent.entries.forEach { component ->
                        Box(Modifier.testTag("original-toggle-" + component.key)) {
                            NewUiCompactToggleRow(title = stringResource(componentTitle(component)),
                                checked = draft[component].visible,
                                onCheckedChange = { draftJson = draft.show(component, it).encode() })
                        }
                    }
                }
                NewUiSecondaryActionButton(stringResource(R.string.settings_original_cluster_reset),
                    onClick = { draftJson = OriginalClusterLayout().encode() }, modifier = Modifier.testTag("original-cluster-reset"))
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

private fun componentTitle(component: OriginalClusterComponent) = when (component) {
    OriginalClusterComponent.TIRES -> R.string.settings_original_cluster_tires
    OriginalClusterComponent.DRIVING -> R.string.settings_original_cluster_driving
    OriginalClusterComponent.TURN -> R.string.settings_original_cluster_turn
    OriginalClusterComponent.TIME -> R.string.settings_original_cluster_time
}
