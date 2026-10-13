package com.shilapi.xcertplay.newui.diagnostics

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.host.R

@Composable
fun NewUiDiagnosticReportScreen(
    title: String,
    body: String,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_diag_view)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                title,
                style = NewUiTypography.SettingTitle,
                color = NewUiColors.TextPrimary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            Text(
                body,
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_diagnostics,
            onBack = onBack,
        )
    }
}
