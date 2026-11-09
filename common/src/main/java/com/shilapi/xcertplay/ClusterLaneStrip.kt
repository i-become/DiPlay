package com.shilapi.xcertplay

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.shilapi.xcertplay.glance.CarPlayLane
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** One left-to-right row; never crop or wrap lanes into a misleading road layout. */
internal class ClusterLaneStrip {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()

    fun fits(lanes: List<CarPlayLane>, width: Float): Boolean =
        lanes.size in 1..12 && width / lanes.size >= 24f && lanes.all { lane ->
            lane.directions.isNotEmpty() && lane.directions.all { it in -180..180 }
        }

    fun draw(canvas: Canvas, bounds: RectF, lanes: List<CarPlayLane>, accent: Int) {
        if (!fits(lanes, bounds.width())) return
        val pitch = min(56f, bounds.width() / lanes.size)
        val left = bounds.centerX() - pitch * lanes.size / 2
        val cy = bounds.centerY() - 2f
        lanes.forEachIndexed { i, lane ->
            val cx = left + pitch * (i + .5f)
            if (i > 0) {
                paint.color = Color.rgb(48, 67, 82); paint.strokeWidth = 1f
                canvas.drawLine(left + pitch * i, cy - 12f, left + pitch * i, cy + 14f, paint)
            }
            // Draw both branches of a split lane; highlight only the branch the phone selected.
            paint.color = if (lane.status == 0) Color.rgb(79, 87, 99) else Color.rgb(176, 184, 196)
            paint.strokeWidth = 2.6f
            lane.directions.distinct().forEach {
                arrow(canvas, cx, cy, it, pitch)
            }
            if (lane.recommended) {
                paint.color = accent; paint.strokeWidth = 4.2f
                val selected = lane.selectedDirection
                selected?.let {
                    arrow(canvas, cx, cy, it, pitch)
                    // Weight + underline preserve the distinction without relying on colour alone.
                    canvas.drawLine(cx - 9f, cy + 24f, cx + 9f, cy + 24f, paint)
                }
            }
        }
    }

    private fun arrow(canvas: Canvas, x: Float, y: Float, degrees: Int, pitch: Float) {
        val reach = min(17f, pitch * .32f)
        val radians = Math.toRadians(degrees.toDouble())
        val dx = sin(radians).toFloat()
        val dy = -cos(radians).toFloat()
        path.reset(); path.moveTo(x, y + 17f); path.lineTo(x, y + 2f)
        val tipX: Float
        val tipY: Float
        if (kotlin.math.abs(degrees) > 135) {
            val side = if (degrees < 0) -1f else 1f
            tipX = x + side * reach; tipY = y + 5f
            path.cubicTo(x, y - 25f, tipX, y - 25f, tipX, tipY)
        } else {
            tipX = x + dx * reach; tipY = y - 4f + dy * reach
            path.quadTo(x, y - 4f, tipX, tipY)
        }
        canvas.drawPath(path, paint)
        val headDx = if (kotlin.math.abs(degrees) > 135) 0f else dx
        val headDy = if (kotlin.math.abs(degrees) > 135) 1f else dy
        path.reset(); path.moveTo(tipX - headDx * 7f - headDy * 5f, tipY - headDy * 7f + headDx * 5f)
        path.lineTo(tipX, tipY)
        path.lineTo(tipX - headDx * 7f + headDy * 5f, tipY - headDy * 7f - headDx * 5f)
        canvas.drawPath(path, paint)
    }
}
