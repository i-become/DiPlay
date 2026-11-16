package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF

/** Each style owns its map geometry and artwork; all styles share live subscriptions/motion. */
internal interface ClusterInstrumentRenderer {
    val lyricAnimating: Boolean get() = false
    fun draw(canvas: Canvas, width: Int, height: Int, state: ClusterDashboardState, night: Boolean,
             includeNeedles: Boolean = true)
    fun drawNeedles(canvas: Canvas, width: Int, height: Int, speedKph: Float?, rpm: Float?)
    fun release()
}

internal val ClusterStyle.customDashboard: Boolean get() = this != ClusterStyle.ORIGINAL
internal val ClusterStyle.mapAspect: Float get() = if (this == ClusterStyle.SPORT)
    SportClusterGeometry.MAP_ASPECT else OemClusterGeometry.MAP_ASPECT
internal fun ClusterStyle.renderer(context: Context): ClusterInstrumentRenderer =
    if (this == ClusterStyle.SPORT) SportClusterRenderer(context) else OemClusterRenderer(context)
internal fun ClusterStyle.mapBounds(width: Int, height: Int, aspect: Float = mapAspect): RectF =
    if (this == ClusterStyle.SPORT) SportClusterGeometry.mapBounds(width, height, aspect)
    else OemClusterGeometry.mapBounds(width, height, aspect)
internal fun ClusterStyle.streamConfig(width: Int, height: Int, scale: Int, markerXPercent: Int, markerYPercent: Int) =
    if (this == ClusterStyle.SPORT) SportClusterGeometry.streamConfig(width, height, scale, markerXPercent, markerYPercent)
    else OemClusterGeometry.streamConfig(width, height, scale, markerXPercent, markerYPercent)

