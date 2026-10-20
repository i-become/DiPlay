package com.shilapi.xcertplay.newui.display

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.airplay.AirPlayDisplaySettings
import com.shilapi.xcertplay.CarPlayHostSettingsBridge
import com.shilapi.xcertplay.CarPlayNightMode
import com.shilapi.xcertplay.hud.BydOutputSettings
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
            clusterFlyScreenWindowEnabled = true,
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
            smoothVideoEnabled = AirPlayPersistence.loadSmoothVideo(context),
            directVideoOutputEnabled = AirPlayPersistence.loadDirectVideoOutput(context),
            lowLatencyDecoderEnabled = AirPlayPersistence.loadLowLatencyDecoder(context),
            mtkDecoderTuningEnabled = AirPlayPersistence.loadMtkDecoderTuning(context),
            mtkDecoderTuningDescription = context.getString(
                R.string.settings_mtk_decoder_tuning_description,
                videoCodecLabel,
            ),
            hudNavigationEnabled = BydOutputSettings.enabled(context),
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
}

internal object NewUiDisplayActions {
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
        BydOutputSettings.setEnabled(context, enabled)
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
        CarPlayHostSettingsBridge.notifyCarPlayNightModeChanged(context)
    }

    fun saveClusterMap(context: Context, enabled: Boolean) {
        AirPlayPersistence.saveClusterMapEnabled(context, enabled)
        if (enabled) {
            AirPlayPersistence.saveClusterMapHorizontalGestureEnabled(context, true)
        }
        CarPlayHostSettingsBridge.notifyClusterMapSettingsChanged()
    }
}

fun CarPlayNightMode.newUiLabelRes(): Int = when (this) {
    CarPlayNightMode.SYSTEM -> R.string.new_ui_theme_follow_system
    CarPlayNightMode.DAY -> R.string.new_ui_theme_day
    CarPlayNightMode.NIGHT -> R.string.new_ui_theme_night
    else -> R.string.new_ui_theme_follow_system
}
