package com.shilapi.xcertplay.newui.display

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.CarPlayHostSettingsBridge
import com.shilapi.xcertplay.CarPlayNightMode
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.ClusterDashboardView
import com.shilapi.xcertplay.hud.Hs5OutputSettings
import com.shilapi.xcertplay.host.R

data class NewUiDisplayState(
    val fps: Int,
    val smoothVideoEnabled: Boolean,
    val directVideoOutputEnabled: Boolean,
    val lowLatencyDecoderEnabled: Boolean,
    val mtkDecoderTuningEnabled: Boolean,
    val mtkDecoderTuningDescription: String,
    val hudNavigationEnabled: Boolean,
    val clusterMapEnabled: Boolean,
    val clusterFlyScreenDefaultOnConnect: Boolean,
    val clusterFlyScreenWindowEnabled: Boolean,
    val clusterHorizontalGestureEnabled: Boolean,
    val carPlayNightMode: CarPlayNightMode,
    val clusterStyle: ClusterStyle = ClusterStyle.ORIGINAL,
    val clusterManagerAvailable: Boolean = false,
    val carPlaySize: CarPlaySize = CarPlaySize.DEFAULT,
    val iconSizeReconnectNeeded: Boolean = false,
) {
    val fpsLabel: String
        get() = "${AirPlayDisplaySettings.sanitizeFps(fps)} FPS"

    val nightModeIndex: Int
        get() = when (carPlayNightMode) {
            CarPlayNightMode.SYSTEM -> 0
            CarPlayNightMode.DAY -> 1
            else -> 2
        }

    companion object {
        val placeholder = NewUiDisplayState(
            fps = 60,
            smoothVideoEnabled = true,
            directVideoOutputEnabled = true,
            lowLatencyDecoderEnabled = true,
            mtkDecoderTuningEnabled = true,
            mtkDecoderTuningDescription = "",
            hudNavigationEnabled = true,
            clusterMapEnabled = true,
            clusterFlyScreenDefaultOnConnect = true,
            clusterFlyScreenWindowEnabled = false,
            clusterHorizontalGestureEnabled = true,
            carPlayNightMode = CarPlayNightMode.SYSTEM,
        )
    }
}

internal object NewUiDisplayStateReader {
    fun read(context: Context): NewUiDisplayState {
        val videoCodecLabel = if (AirPlayPersistence.loadHevcEnabled(context)) "HEVC" else "H.264"
        return NewUiDisplayState(
            fps = AirPlayPersistence.loadFps(context),
            carPlaySize = AirPlayPersistence.loadCarPlaySize(context),
            iconSizeReconnectNeeded = iconSizeNeedsReconnect(context),
            clusterStyle = AirPlayPersistence.loadClusterStyle(context),
            clusterManagerAvailable = ClusterDashboardView.supported(context),
            smoothVideoEnabled = AirPlayPersistence.loadSmoothVideo(context),
            directVideoOutputEnabled = AirPlayPersistence.loadDirectVideoOutput(context),
            lowLatencyDecoderEnabled = AirPlayPersistence.loadLowLatencyDecoder(context),
            mtkDecoderTuningEnabled = AirPlayPersistence.loadMtkDecoderTuning(context),
            mtkDecoderTuningDescription = context.getString(
                R.string.settings_mtk_decoder_tuning_description,
                videoCodecLabel,
            ),
            hudNavigationEnabled = Hs5OutputSettings.enabled(context),
            clusterMapEnabled = AirPlayPersistence.loadClusterMapEnabled(context),
            clusterFlyScreenDefaultOnConnect = AirPlayPersistence.loadClusterFlyScreenDefaultOnConnect(context),
            clusterFlyScreenWindowEnabled = AirPlayPersistence.loadHs5ClusterActivityWindow(context),
            clusterHorizontalGestureEnabled =
                AirPlayPersistence.loadClusterMapHorizontalGestureEnabled(context),
            carPlayNightMode = AirPlayPersistence.loadCarPlayNightMode(context).let { mode ->
                when (mode) {
                    CarPlayNightMode.DAY, CarPlayNightMode.NIGHT, CarPlayNightMode.SYSTEM -> mode
                    else -> CarPlayNightMode.SYSTEM
                }
            },
        )
    }

    private fun iconSizeNeedsReconnect(context: Context): Boolean {
        val display = CarPlayBackgroundSession.snapshot()?.display ?: return false
        val reference = display.physicalSizeReferenceMm ?: return false
        return reference != AirPlayPersistence.loadWidthPhysicalMm(context) ||
            display.physicalSizeBasis != AirPlayPersistence.loadPhysicalSizeBasis(context)
    }
}

internal object NewUiDisplayActions {
    fun saveCarPlaySize(context: Context, size: CarPlaySize) {
        AirPlayPersistence.saveCarPlaySize(context, size)
    }

    fun saveClusterStyle(context: Context, style: ClusterStyle) {
        if (AirPlayPersistence.loadClusterStyle(context) == style) return
        AirPlayPersistence.saveClusterStyle(context, style)
    }

    fun saveFps(context: Context, fps: Int) {
        AirPlayPersistence.saveFps(context, fps)
    }

    fun saveSmoothVideo(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveSmoothVideo(context, enabled)
    }

    fun saveDirectVideoOutput(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveDirectVideoOutput(context, enabled)
    }

    fun saveLowLatencyDecoder(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveLowLatencyDecoder(context, enabled)
    }

    fun saveMtkDecoderTuning(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveMtkDecoderTuning(context, enabled)
    }

    fun saveHudNavigation(context: Context, enabled: Boolean) {
        Hs5OutputSettings.setEnabled(context, enabled)
    }

    fun saveClusterGesture(context: Context, enabled: Boolean) {
        if (enabled && !AirPlayPersistence.loadClusterMapEnabled(context)) return
        AirPlayPersistence.saveClusterMapHorizontalGestureEnabled(context, enabled)
    }

    fun saveClusterFlyScreenDefaultOnConnect(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveClusterFlyScreenDefaultOnConnect(context, enabled)
    }

    fun saveClusterFlyScreenWindow(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveHs5ClusterActivityWindow(context, enabled)
        CarPlayHostSettingsBridge.notifyClusterMapSettingsChanged()
    }

    fun saveCarPlayNightMode(context: Context, index: Int) {
        val mode = when (index) {
            1 -> CarPlayNightMode.DAY
            2 -> CarPlayNightMode.NIGHT
            else -> CarPlayNightMode.SYSTEM
        }
        AirPlayPersistence.saveCarPlayNightMode(context, mode)
        // This selector controls both projection and app chrome, including older installs.
        AirPlayPersistence.saveAppAppearance(context, com.shilapi.xcertplay.AppAppearance.AUTO)
        CarPlayHostSettingsBridge.notifyCarPlayNightModeChanged(context)
        com.shilapi.xcertplay.AppAppearanceRuntime.invalidateVehicleLight()
    }

    fun saveClusterMap(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveClusterMapEnabled(context, enabled)
        if (enabled) {
            AirPlayPersistence.saveClusterMapHorizontalGestureEnabled(context, true)
        }
        CarPlayHostSettingsBridge.notifyClusterMapSettingsChanged()
        com.shilapi.xcertplay.Hs5SteeringKeys.applySettings(context)
    }

    fun saveClusterNavMarker(context: Context, xPercent: Int, yPercent: Int) {
        AirPlayPersistence.saveClusterMarkerXPercent(context, xPercent)
        AirPlayPersistence.saveClusterMarkerYPercent(context, yPercent)
        CarPlayHostSettingsBridge.notifyClusterMapSettingsChanged()
    }

    fun resetClusterNavMarker(context: Context) {
        saveClusterNavMarker(context, DEFAULT_NAV_MARKER_X, DEFAULT_NAV_MARKER_Y)
    }

    private const val DEFAULT_NAV_MARKER_X = 50
    private const val DEFAULT_NAV_MARKER_Y = 45
}

fun CarPlayNightMode.newUiLabelRes(): Int = when (this) {
    CarPlayNightMode.SYSTEM -> R.string.new_ui_theme_follow_system
    CarPlayNightMode.DAY -> R.string.new_ui_theme_day
    CarPlayNightMode.NIGHT -> R.string.new_ui_theme_night
    else -> R.string.new_ui_theme_follow_system
}
