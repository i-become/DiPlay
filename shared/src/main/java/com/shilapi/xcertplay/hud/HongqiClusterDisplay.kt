package com.shilapi.xcertplay.hud

import android.util.DisplayMetrics
import android.view.Display

/**
 * Heuristic selection of Hongqi / FAW instrument-cluster [Display] instances.
 *
 * The JiuZhou CarPlay app (com.juyou.app) decodes CarPlay's cluster stream onto a presentation
 * display and can overlay turn-by-turn cards. DiPlay uses the same CarPlay alt-screen path once
 * a compatible display is visible.
 */
object HongqiClusterDisplay {
    private val knownSizes = setOf(
        1920 to 720,
        1920 to 480,
        1280 to 480,
        1280 to 720,
    )

    fun accepts(name: String, width: Int, height: Int): Boolean {
        if (width <= 0 || height <= 0) return false
        if (width <= height) return false
        if (name.contains("cluster", ignoreCase = true)) return true
        if ((width to height) in knownSizes) return true
        return name.contains("Virtual", ignoreCase = true) &&
            width in 960..2560 && height in 360..1080
    }

    fun isInstrumentCluster(name: String): Boolean =
        name.contains("cluster", ignoreCase = true) && !name.contains("ScreenCapture", ignoreCase = true)

    fun pick(displays: Array<Display>, mainDisplayId: Int): Display? {
        val candidates = displays.filter { it.displayId != mainDisplayId }.mapNotNull { display ->
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            if (runCatching { display.getRealMetrics(metrics) }.isFailure) return@mapNotNull null
            if (!accepts(display.name, metrics.widthPixels, metrics.heightPixels)) return@mapNotNull null
            if (display.name.contains("ScreenCapture", ignoreCase = true) ||
                display.name.contains("factorymode", ignoreCase = true)
            ) {
                return@mapNotNull null
            }
            val score = metrics.widthPixels * metrics.heightPixels +
                if (isInstrumentCluster(display.name)) 10_000_000 else 0
            display to score
        }
        return candidates.maxByOrNull { it.second }?.first
    }
}
