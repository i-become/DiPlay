package com.shilapi.xcertplay

import android.app.Activity
import android.os.Build
import android.view.Window
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** Full-screen chrome for DiPlay's own activities (matches legacy [DiPlayActivity] behavior). */
internal object DiPlayWindowFullscreen {
    fun configureWindow(window: Window) {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    fun apply(activity: Activity) {
        configureWindow(activity.window)
        val multiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && activity.isInMultiWindowMode
        if (AirPlayPersistence.loadHarmanFullscreen(activity) && !multiWindow) {
            HarmanFullscreenCompat.apply(activity.window)
            return
        }
        HarmanFullscreenCompat.clear(activity.window)
        val hideTop = AirPlayPersistence.loadHideTopBar(activity) && !multiWindow
        val hideBottom = AirPlayPersistence.loadHideBottomBar(activity) && !multiWindow
        WindowCompat.setDecorFitsSystemWindows(activity.window, !(hideTop && hideBottom))
        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (hideTop) controller.hide(WindowInsetsCompat.Type.statusBars())
        else controller.show(WindowInsetsCompat.Type.statusBars())
        if (hideBottom) controller.hide(WindowInsetsCompat.Type.navigationBars())
        else controller.show(WindowInsetsCompat.Type.navigationBars())
        ViewCompat.requestApplyInsets(activity.window.decorView)
    }
}
