package com.shilapi.xcertplay.newui.connection

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.DiPlayPreferences

@Composable
internal fun NewUiConnectPhoneScreen(
    state: NewUiPhoneSelectionState,
    connectAfterSave: Boolean,
    onBack: () -> Unit,
    onConfirm: (NewUiBondedPhone) -> Unit,
    onRequestBluetoothPermission: () -> Unit,
    onOpenBluetoothSettings: () -> Unit,
) {
    var pickedAddress by remember(state) {
        mutableStateOf(state.selectedAddress ?: state.bondedPhones.firstOrNull()?.address)
    }
    val picked = state.bondedPhones.firstOrNull { it.address == pickedAddress }

    Column(Modifier.fillMaxSize()) {
        NewUiConnectionSubpageHeader(titleRes = R.string.new_ui_connect_phone_title)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
        ) {
            when {
                state.needsBluetoothPermission -> {
                    NewUiPhoneHintPanel(
                        title = stringResource(R.string.new_ui_phone_need_bluetooth_permission),
                        actionLabel = stringResource(R.string.new_ui_phone_grant_permission),
                        onAction = onRequestBluetoothPermission,
                    )
                }
                !state.bluetoothEnabled -> {
                    NewUiPhoneHintPanel(
                        title = stringResource(R.string.new_ui_phone_bluetooth_off),
                        actionLabel = stringResource(R.string.open_bluetooth),
                        onAction = onOpenBluetoothSettings,
                    )
                }
                state.bondedPhones.isEmpty() -> {
                    NewUiPhoneHintPanel(
                        title = stringResource(R.string.new_ui_phone_none_paired),
                        actionLabel = stringResource(R.string.new_ui_phone_pair_another),
                        onAction = onOpenBluetoothSettings,
                    )
                }
                else -> {
                    NewUiPhoneDeviceList(
                        phones = state.bondedPhones,
                        selectedAddress = pickedAddress,
                        onSelect = { pickedAddress = it.address },
                    )
                    NewUiPhoneSecondaryLink(
                        label = stringResource(R.string.new_ui_phone_pair_another),
                        onClick = onOpenBluetoothSettings,
                    )
                }
            }
        }
        val confirmLabel = if (connectAfterSave) {
            stringResource(R.string.new_ui_phone_confirm_and_connect)
        } else {
            stringResource(R.string.new_ui_phone_confirm)
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_connection,
            onBack = onBack,
            primaryLabel = confirmLabel,
            primaryEnabled = picked != null && !state.needsBluetoothPermission && state.bluetoothEnabled,
            onPrimary = { picked?.let(onConfirm) },
        )
    }
}
