package com.shilapi.xcertplay

import android.content.Context

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

