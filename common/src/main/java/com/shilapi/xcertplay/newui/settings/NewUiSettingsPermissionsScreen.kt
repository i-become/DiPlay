package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsPermissionsScreen(
    onBack: () -> Unit,
    onOpenAppPermissions: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_settings_permissions)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.nearby_devices_connects_your_iphone_microphone_enables_sir),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiSecondaryActionButton(
                label = stringResource(R.string.app_permissions),
                onClick = onOpenAppPermissions,
                verticalPadding = 32.dp,
            )
            NewUiSecondaryActionButton(
                label = stringResource(R.string.bluetooth_settings),
                modifier = Modifier.padding(top = 12.dp),
                onClick = onOpenBluetoothSettings,
                verticalPadding = 32.dp,
            )
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
        )
    }
}
