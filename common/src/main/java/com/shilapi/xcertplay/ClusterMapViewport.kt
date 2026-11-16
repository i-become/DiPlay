package com.shilapi.xcertplay

import android.content.Context
import kotlin.math.roundToInt

/** Local crop of the independent cluster video, never a phone layout command.
 * Positive values discard the left edge; negative values discard the right edge.
 * Both axes scale equally. The selected vertical anchor remains at the same height.
 * No position is guessed from maneuver direction, speed, or a missing turn card.
 */
internal data class ClusterMapViewport(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    // A video cannot reveal pixels outside its original frame. Stop at full-frame
    // coverage rather than shrinking the image and introducing black borders.
    fun cover(): ClusterMapViewport {
        val boundedScale = scale.coerceAtLeast(1f)
        return ClusterMapViewport(boundedScale, x.coerceIn(1f - boundedScale, 0f),
            y.coerceIn(1f - boundedScale, 0f))
    }

    fun zoom(percent: Int, anchorPercent: Int): ClusterMapViewport {
        val factor = percent.coerceIn(50, 200) / 100f
        return ClusterMapViewport(scale * factor, x * factor + (1f - factor) / 2f,
            y * factor + (1f - factor) * anchorPercent.coerceIn(5, 95) / 100f)
    }
    companion object {
        fun navigation(navigating: Boolean, cropPercent: Int, verticalAnchorPercent: Int): ClusterMapViewport {
            if (!navigating || cropPercent == 0) return ClusterMapViewport()
            val crop = cropPercent.coerceIn(-60, 60) / 100f
            val removed = kotlin.math.abs(crop)
            val scale = 1f / (1f - removed)
            return ClusterMapViewport(scale,
                x = -crop.coerceAtLeast(0f) * scale,
                y = -removed * verticalAnchorPercent.coerceIn(5, 95) / 100f * scale)
        }
    }
}

/** More map pixels in the same viewport show more geography, in both browsing and
 * guidance. Keep the existing density ceiling and avoid navigation-time Surface rebuilds. */
internal object ClusterMapRenderPolicy {
    fun scalePercent(savedPercent: Int): Int =
        (savedPercent.coerceIn(25, 150) * 1.5f).roundToInt().coerceAtMost(150)
}

/** Separate from the phone's safe-area declaration. Neutral until calibrated on the real map. */
internal object ClusterMapCropSettings {
    private const val PREFS = "cluster_map_viewport"
    private const val NAVIGATION_CROP = "navigation_crop_percent"
    private const val NAVIGATION_ZOOM = "navigation_zoom_percent"
    fun loadZoom(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(NAVIGATION_ZOOM, 100).coerceIn(50, 200)
    fun saveZoom(context: Context, value: Int) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putInt(NAVIGATION_ZOOM, value.coerceIn(50, 200)).apply()
    fun load(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .getInt(NAVIGATION_CROP, 0).coerceIn(-60, 60)
    fun save(context: Context, value: Int) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        .edit().putInt(NAVIGATION_CROP, value.coerceIn(-60, 60)).apply()
    fun subscribe(context: Context, changed: () -> Unit): () -> Unit {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == NAVIGATION_CROP || key == NAVIGATION_ZOOM) changed()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        return { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
}

internal data class ClusterCopyRegion(val left: Float = 0.18f, val top: Float = 0.12f,
    val right: Float = 0.82f, val bottom: Float = 0.88f) {
    fun clamped() = ClusterCopyRegion(left.coerceIn(0f, 0.98f), top.coerceIn(0f, 0.98f),
        right.coerceIn(left.coerceIn(0f, 0.98f) + 0.02f, 1f),
        bottom.coerceIn(top.coerceIn(0f, 0.98f) + 0.02f, 1f))
    companion object {
        fun load(context: Context, style: ClusterStyle = AirPlayPersistence.loadClusterStyle(context)): ClusterCopyRegion {
            val p = context.getSharedPreferences("cluster_copy_region_${style.preferenceValue}", Context.MODE_PRIVATE)
            return ClusterCopyRegion(p.getFloat("left", .18f), p.getFloat("top", .12f),
                p.getFloat("right", .82f), p.getFloat("bottom", .88f)).clamped()
        }
        fun save(context: Context, region: ClusterCopyRegion, style: ClusterStyle) {
            val v = region.clamped()
            context.getSharedPreferences("cluster_copy_region_${style.preferenceValue}", Context.MODE_PRIVATE).edit()
                .putFloat("left", v.left).putFloat("top", v.top)
                .putFloat("right", v.right).putFloat("bottom", v.bottom).apply()
        }
    }
}

