package com.shilapi.xcertplay.newui.connection

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.shilapi.xcertplay.DiPlayPreferences
import com.shilapi.xcertplay.host.R

internal data class NewUiBondedPhone(
    val address: String,
    val displayName: String,
    val suffix: String?,
) {
    val linkMode: DiPlayPreferences.PhoneLinkMode = DiPlayPreferences.PhoneLinkMode.CARPLAY
    val subtitle: String?
        get() = suffix?.let { "···$it" }
}

internal data class NewUiPhoneSelectionState(
    val needsBluetoothPermission: Boolean,
    val bluetoothEnabled: Boolean,
    val bondedPhones: List<NewUiBondedPhone>,
    val selectedAddress: String?,
    val selectedName: String,
    val linkModes: Map<String, DiPlayPreferences.PhoneLinkMode> = emptyMap(),
) {
    val hasSelectedPhone: Boolean get() = selectedAddress != null

    companion object {
        val placeholder = NewUiPhoneSelectionState(
            needsBluetoothPermission = false,
            bluetoothEnabled = true,
            bondedPhones = emptyList(),
            selectedAddress = null,
            selectedName = "",
            linkModes = emptyMap(),
        )
    }
}

internal object NewUiPhoneSelectionReader {
    fun read(context: Context): NewUiPhoneSelectionState {
        val needsPermission = Build.VERSION.SDK_INT >= 31 &&
            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            return NewUiPhoneSelectionState(
                needsBluetoothPermission = true,
                bluetoothEnabled = false,
                bondedPhones = emptyList(),
                selectedAddress = DiPlayPreferences.phoneAddress(context),
                selectedName = DiPlayPreferences.phoneName(context),
                linkModes = emptyMap(),
            )
        }
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        val enabled = adapter?.isEnabled == true
        val bonded = if (enabled && adapter != null) {
            runCatching { adapter.bondedDevices.sortedBy { it.name ?: "" } }.getOrDefault(emptyList())
        } else {
            emptyList()
        }
        val phones = bonded.map { device ->
            val name = device.name ?: context.getString(R.string.paired_device)
            val suffix = if (bonded.count { it.name == device.name } > 1) {
                device.address.takeLast(5)
            } else {
                null
            }
            NewUiBondedPhone(device.address, name, suffix)
        }
        return NewUiPhoneSelectionState(
            needsBluetoothPermission = false,
            bluetoothEnabled = enabled,
            bondedPhones = phones,
            selectedAddress = DiPlayPreferences.phoneAddress(context),
            selectedName = DiPlayPreferences.phoneName(context),
            linkModes = phones.associate { it.address to DiPlayPreferences.phoneLinkMode(context, it.address, it.displayName) },
        )
    }
}
