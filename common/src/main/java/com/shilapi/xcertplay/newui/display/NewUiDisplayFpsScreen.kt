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
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSingleChoicePanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiDisplayFpsScreen(
    selectedFps: Int,
    onBack: () -> Unit,
    onSelectFps: (Int) -> Unit,
) {
    val selectedIndex = if (selectedFps >= 60) 1 else 0
    val options = listOf(
        stringResource(R.string.s_30_fps_lighter_load),
        stringResource(R.string.s_60_fps_smoother_motion),
    )
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
                onSelect = { index -> onSelectFps(if (index == 1) 60 else 30) },
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_display,
            onBack = onBack,
        )
    }
}
