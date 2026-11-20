package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import kotlin.math.max
import kotlin.math.min

/** Content-sized guidance in the cluster's reference plane; physical lane order stays LTR. */
internal class ClusterGuidanceCard(private val context: Context, private val sport: Boolean) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val lanes = ClusterLaneStrip()
    private val phoneIcon = ClusterNavigationIcon()

    fun release() = phoneIcon.release()
    private val white = Color.rgb(245, 247, 250)
    private val accent = if (sport) Color.rgb(120, 231, 249) else Color.rgb(119, 228, 255)

    fun draw(canvas: Canvas, nav: CarPlayGlance.Snapshot) {
        if (!nav.connected || !nav.navigating) return
        val maxWidth = if (sport) 385f else 461f
        val showLanes = lanes.fits(nav.lanes, maxWidth - 24f)
        val maneuver = nav.maneuverType
        if (maneuver == null && !showLanes) return
        val road = nav.road.trim()
        val distance = if (nav.distanceMeters < 1000) context.getString(R.string.cluster_dashboard_meters, nav.distanceMeters)
            else context.getString(R.string.cluster_dashboard_kilometers, nav.distanceMeters / 1000.0)
        val distanceSize = if (sport) 35f else 36f
        val roadSize = if (sport) 23f else 22f
        val arrowSize = if (sport) 67f else 73f
        val textWidth = max(measure(distance, distanceSize), measure(road, roadSize))
        val turnWidth = if (maneuver != null) 24f + arrowSize + 12f + textWidth else 0f
        val laneWidth = if (showLanes) nav.lanes.size * 56f + 24f else 0f
        val width = min(maxWidth, max(turnWidth, laneWidth))
        val distanceHeight = fontHeight(distanceSize)
        val roadHeight = if (road.isNotEmpty()) fontHeight(roadSize) + 2f else 0f
        val turnHeight = if (maneuver != null) max(arrowSize, distanceHeight + roadHeight) + 12f else 0f
        val laneTop = turnHeight + if (showLanes && maneuver != null) 2f else 0f
        val height = turnHeight + if (showLanes) 56f + if (maneuver != null) 2f else 0f else 0f
        val left = if (sport) 1366f else 997.5f - width / 2
        val top = if (sport) 72f else 112f
        val bounds = RectF(left, top, left + width, top + height)
        paint.style = Paint.Style.FILL
        paint.color = if (sport) Color.argb(249, 3, 15, 22) else Color.argb(249, 0, 21, 45)
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = if (sport) 1f else 1.4f
        paint.color = if (sport) Color.rgb(43, 58, 72) else Color.rgb(83, 128, 183)
        canvas.drawRoundRect(bounds, 12f, 12f, paint)
        if (maneuver != null) {
            val arrowLeft = bounds.left + 12f
            val arrowTop = top + (turnHeight - arrowSize) / 2
            if (!phoneIcon.draw(canvas, nav.directionIcon, RectF(arrowLeft, arrowTop, arrowLeft + arrowSize, arrowTop + arrowSize)))
            context.getDrawable(NavigationWidgetUpdater.arrow(maneuver, nav.drivingSide))?.mutate()?.let {
                it.setTint(white)
                it.setBounds(arrowLeft.toInt(), arrowTop.toInt(), (arrowLeft + arrowSize).toInt(), (arrowTop + arrowSize).toInt())
                it.draw(canvas)
            }
            val textX = arrowLeft + arrowSize + 12f
            val textTop = top + (turnHeight - distanceHeight - roadHeight) / 2
            val baseline = textTop - ascent(distanceSize)
            text(canvas, distance, textX, baseline, distanceSize, bounds.right - 12f - textX, if (sport) white else accent)
            if (road.isNotEmpty()) text(canvas, road, textX,
                textTop + distanceHeight + 2f - ascent(roadSize), roadSize, bounds.right - 12f - textX, white)
        }
        if (showLanes) {
            if (maneuver != null) {
                paint.color = if (sport) Color.rgb(42, 54, 65) else Color.rgb(42, 74, 102)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
                canvas.drawLine(bounds.left + 12f, top + turnHeight + 1f, bounds.right - 12f, top + turnHeight + 1f, paint)
            }
            lanes.draw(canvas, RectF(bounds.left + 12f, top + laneTop, bounds.right - 12f, bounds.bottom), nav.lanes, accent)
        }
    }

    private fun measure(value: String, size: Float): Float { paint.textSize = size; return paint.measureText(value) }
    private fun fontHeight(size: Float): Float { paint.textSize = size; return paint.descent() - paint.ascent() }
    private fun ascent(size: Float): Float { paint.textSize = size; return paint.ascent() }
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, width: Float, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
        canvas.drawText(TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString(), x, y, paint)
    }
}
