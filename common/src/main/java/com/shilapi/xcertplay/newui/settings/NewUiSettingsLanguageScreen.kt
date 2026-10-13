package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.AppLocale
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSingleChoicePanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsLanguageScreen(
    currentLanguage: String,
    onBack: () -> Unit,
    onApply: (String) -> Unit,
) {
    val context = LocalContext.current
    val options = remember {
        AppLocale.ALL.map { AppLocale.displayName(context, it) }
    }
    val initialIndex = AppLocale.ALL.indexOf(currentLanguage).coerceAtLeast(0)
    var selectedIndex by remember(currentLanguage) { mutableIntStateOf(initialIndex) }
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.language_section_title)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.language_hint),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiSingleChoicePanel(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = { selectedIndex = it },
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
            primaryLabelRes = R.string.language_apply,
            onPrimary = { onApply(AppLocale.ALL[selectedIndex]) },
        )
    }
}
