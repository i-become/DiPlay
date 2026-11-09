package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.hud.Hs5OutputSettings
import com.shilapi.xcertplay.location.AndroidCarPlayLocationProvider
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayTransport
import com.shilapi.xcertplay.orchestration.ManualHotspotBand
import com.shilapi.xcertplay.orchestration.ManualHotspotSecurity
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import com.shilapi.xcertplay.transport.Iap2LocationProvider
import com.shilapi.xcertplay.transport.UsbDeviceId

/** UI and boot use the same identity, transport and authentication settings. */
internal object CarPlayRuntimeConfigFactory {
    fun locationProvider(context: Context, config: CarPlayRuntimeConfig): Iap2LocationProvider? =
        if (config.locationReportingEnabled) AndroidCarPlayLocationProvider(context) else null

    fun vehicleStatusProvider(context: Context): com.shilapi.xcertplay.transport.VehicleStatusProvider? = null

    fun create(
        context: Context,
        identity: AirPlayIdentity,
        mfiTarget: MfiTarget = AirPlayPersistence.loadMfiTarget(context),
        mfiI2cPath: String = AirPlayPersistence.loadMfiI2cPath(context),
        remoteMfiServer: String = AirPlayPersistence.loadRemoteMfiServer(context),
        remoteMfiToken: String = AirPlayPersistence.loadRemoteMfiToken(context),
        wirelessEnabled: Boolean = DiPlayPreferences.autoConnectWireless(context),
        hotspotMode: WirelessHotspotMode = AirPlayPersistence.loadWirelessHotspotMode(context),
        manualSsid: String = AirPlayPersistence.loadManualHotspotSsid(context),
        manualPassphrase: String = AirPlayPersistence.loadManualHotspotPassphrase(context),
        manualBand: ManualHotspotBand = AirPlayPersistence.loadManualHotspotBand(context),
        manualChannel: Int = AirPlayPersistence.loadManualHotspotChannel(context),
        manualSecurity: ManualHotspotSecurity = AirPlayPersistence.loadManualHotspotSecurity(context),
        existingSsid: String = AirPlayPersistence.loadExistingWifiSsid(context),
        existingPassphrase: String = AirPlayPersistence.loadExistingWifiPassphrase(context),
        locationEnabled: Boolean = AirPlayPersistence.loadLocationReportingEnabled(context),
    ): CarPlayRuntimeConfig {
        val deviceId = DiPlayBootstrap.deviceId(identity)
        return CarPlayRuntimeConfig(
            mfiTarget = mfiTarget,
            // The CP latches its I2C address at power-up. Do not pulse CH341 RST before discovery.
            ch341Devices = if (mfiTarget == MfiTarget.USB_CH341) listOf(UsbDeviceId(0x1a86, 0x5512)) else emptyList(),
            ch341MfiResetGpio = null,
            linuxI2cPath = if (mfiTarget == MfiTarget.I2C) mfiI2cPath.trim() else null,
            remoteMfiServer = remoteMfiServer.trim().takeIf { it.isNotEmpty() },
            remoteMfiToken = remoteMfiToken.takeIf { it.isNotEmpty() },
            identification = Iap2IdentificationConfig(
                name = "DiPlay",
                modelIdentifier = AirPlayPersistence.loadModel(context).trim().ifBlank { AirPlayPersistence.DEFAULT_MODEL },
                manufacturer = AirPlayPersistence.loadManufacturer(context).trim().ifBlank { AirPlayPersistence.DEFAULT_MANUFACTURER },
                serialNumber = "DIPLAY-" + deviceId.replace(":", ""),
                firmwareVersion = "0.1.0", hardwareVersion = "1.0", carPlayUsbInterfaceNumber = 3,
                locationInformationEnabled = locationEnabled,
                vehicleStatusEnabled = false,
                vehicleSpeedEnabled = false,
            ),
            label = "DiPlay",
            hostName = "diplay-" + deviceId.replace(":", "").lowercase(),
            hostMac = deviceId.split(":").map { it.toInt(16).toByte() }.toByteArray(),
            wirelessBluetoothDeviceAddress = DiPlayPreferences.phoneAddress(context),
            transport = if (wirelessEnabled) CarPlayTransport.WIRELESS else CarPlayTransport.WIRED,
            wirelessHotspotMode = hotspotMode,
            wifiP2pPreferredChannel = AirPlayPersistence.loadWifiP2pPreferredChannel(context),
            manualHotspotSsid = manualSsid,
            manualHotspotPassphrase = manualPassphrase,
            manualHotspotBand = manualBand,
            manualHotspotChannel = manualChannel,
            manualHotspotSecurity = manualSecurity,
            existingWifiSsid = existingSsid,
            existingWifiPassphrase = existingPassphrase,
            locationReportingEnabled = locationEnabled,
            availableCurrentMilliAmps = AirPlayPersistence.loadUsbChargingCurrent(context).milliAmps,
        )
    }
}
