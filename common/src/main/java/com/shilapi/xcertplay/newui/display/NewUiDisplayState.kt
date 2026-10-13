package com.shilapi.xcertplay.newui.display

import android.content.Context
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.CarPlayHostSettingsBridge
import com.shilapi.xcertplay.CarPlayNightMode
import com.shilapi.xcertplay.hud.BydOutputSettings
import com.shilapi.xcertplay.host.R

data class NewUiDisplayState(
    val fps: Int,
    val hudNavigationEnabled: Boolean,
    val clusterMapEnabled: Boolean,
    val clusterHorizontalGestureEnabled: Boolean,
    val carPlayNightMode: CarPlayNightMode,
) {
    val fpsLabel: String get() = if (fps >= 60) "60 FPS" else "30 FPS"

    val nightModeIndex: Int
        get() = when (carPlayNightMode) {
            CarPlayNightMode.SYSTEM -> 0
            CarPlayNightMode.DAY -> 1
            else -> 2
        }

    companion object {
        val placeholder = NewUiDisplayState(
            fps = 60,
            hudNavigationEnabled = true,
            clusterMapEnabled = true,
            clusterHorizontalGestureEnabled = true,
            carPlayNightMode = CarPlayNightMode.SYSTEM,
        )
    }
}

internal object NewUiDisplayStateReader {
    fun read(context: Context): NewUiDisplayState = NewUiDisplayState(
            fps = AirPlayPersistence.loadFps(context),
            hudNavigationEnabled = BydOutputSettings.enabled(context),
            clusterMapEnabled = AirPlayPersistence.loadClusterMapEnabled(context),
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

internal object NewUiDisplayActions {
    fun saveFps(context: Context, fps: Int) {
        AirPlayPersistence.saveFps(context, if (fps >= 60) 60 else 30)
    }

    fun saveHudNavigation(context: Context, enabled: Boolean) {
        BydOutputSettings.setEnabled(context, enabled)
    }

    fun saveClusterGesture(context: Context, enabled: Boolean) {
        if (enabled && !AirPlayPersistence.loadClusterMapEnabled(context)) return
        AirPlayPersistence.saveClusterMapHorizontalGestureEnabled(context, enabled)
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
