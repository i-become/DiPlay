package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.NewUiFormField

@Composable
fun NewUiSettingsVehicleNameScreen(
    initialName: String,
    onBack: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_settings_vehicle_name)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.car_button_description),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiFormField(
                label = stringResource(R.string.car_button_name),
                value = name,
                onValueChange = { name = it },
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
            primaryLabelRes = R.string.save,
            onPrimary = { onSave(name.trim()) },
        )
    }
}
