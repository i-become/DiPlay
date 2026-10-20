package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import kotlin.math.abs
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSingleChoicePanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

private val newUiFpsChoices = listOf(30, 50, 60)

private fun newUiFpsOptionLabelRes(fps: Int): Int = when (fps) {
    30 -> R.string.s_30_fps_lighter_load
    50 -> R.string.s_50_fps_balanced
    else -> R.string.s_60_fps_smoother_motion
}

private fun nearestNewUiFpsChoice(fps: Int): Int {
    val sanitized = AirPlayDisplaySettings.sanitizeFps(fps)
    return newUiFpsChoices.minBy { abs(it - sanitized) }
}

@Composable
fun NewUiDisplayFpsScreen(
    selectedFps: Int,
    onBack: () -> Unit,
    onSelectFps: (Int) -> Unit,
) {
    val selectedChoice = nearestNewUiFpsChoice(selectedFps)
    val selectedIndex = newUiFpsChoices.indexOf(selectedChoice)
    val options = newUiFpsChoices.map { stringResource(newUiFpsOptionLabelRes(it)) }
    Column(modifier = Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_display_fps)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.new_ui_display_fps_sub),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiSingleChoicePanel(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = { index -> onSelectFps(newUiFpsChoices[index]) },
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_display,
            onBack = onBack,
        )
    }
}
