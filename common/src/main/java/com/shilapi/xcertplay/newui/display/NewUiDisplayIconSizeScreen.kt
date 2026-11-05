package com.shilapi.xcertplay.newui.display

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.localizedLabel
import com.shilapi.xcertplay.newui.*

@Composable
fun NewUiDisplayIconSizeScreen(
    selectedSize: CarPlaySize,
    reconnectNeeded: Boolean,
    onBack: () -> Unit,
    onSelectSize: (CarPlaySize) -> Unit,
    onReconnect: () -> Unit,
) {
    val context = LocalContext.current
    val choices = CarPlaySize.entries
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.settings_carplay_icon_size)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.settings_carplay_icon_size_description),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiSingleChoicePanel(
                options = choices.map { it.localizedLabel(context) },
                selectedIndex = choices.indexOf(selectedSize),
                onSelect = { onSelectSize(choices[it]) },
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_display,
            onBack = onBack,
            primaryLabelRes = if (reconnectNeeded) R.string.apply_and_reconnect else null,
            onPrimary = if (reconnectNeeded) onReconnect else null,
        )
    }
}
