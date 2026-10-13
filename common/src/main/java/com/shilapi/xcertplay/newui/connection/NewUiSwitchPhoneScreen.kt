package com.shilapi.xcertplay.newui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSwitchPhoneScreen(
    state: NewUiPhoneSelectionState,
    onBack: () -> Unit,
    onSelectPhone: (NewUiBondedPhone) -> Unit,
    onAddPhone: () -> Unit,
    onRequestBluetoothPermission: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NewUiConnectionSubpageHeader(titleRes = R.string.new_ui_switch_phone_title)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
        ) {
            if (state.hasSelectedPhone && state.selectedName.isNotBlank()) {
                NewUiCurrentPhoneCard(state.selectedName)
            }
            when {
                state.needsBluetoothPermission -> {
                    NewUiPhoneHintPanel(
                        title = stringResource(R.string.new_ui_phone_need_bluetooth_permission),
                        actionLabel = stringResource(R.string.new_ui_phone_grant_permission),
                        onAction = onRequestBluetoothPermission,
                    )
                }
                !state.bluetoothEnabled -> {
                    Text(
                        stringResource(R.string.new_ui_phone_bluetooth_off),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                    )
                }
                state.bondedPhones.isEmpty() -> {
                    Text(
                        stringResource(R.string.new_ui_phone_none_paired),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                    )
                }
                else -> {
                    NewUiPhoneDeviceList(
                        phones = state.bondedPhones,
                        selectedAddress = state.selectedAddress,
                        onSelect = onSelectPhone,
                    )
                }
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_connection,
            onBack = onBack,
            primaryLabelRes = R.string.new_ui_add_phone,
            onPrimary = onAddPhone,
        )
    }
}
