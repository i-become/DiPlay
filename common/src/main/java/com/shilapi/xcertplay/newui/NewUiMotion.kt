package com.shilapi.xcertplay.newui

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Motion tokens from `.superdesign/design-system.md` and v8 HTML prototypes. */
object NewUiMotion {
    const val ToggleDurationMs = 200
    const val ButtonPressDurationMs = 120
    const val ButtonPressScale = 0.988f
    const val StatusPulseDurationMs = 1600
    const val NavTabDurationMs = 240
    const val PageCrossfadeMs = 220
    const val ProgressDotDurationMs = 200
    const val LoaderSpinDurationMs = 1000

    val EaseOut = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val EaseInOut = FastOutSlowInEasing
}

@Composable
fun rememberNewUiReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { isReduceMotionEnabled(context) }
}

fun isReduceMotionEnabled(context: Context): Boolean {
    return try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    } catch (_: Throwable) {
        false
    }
}
