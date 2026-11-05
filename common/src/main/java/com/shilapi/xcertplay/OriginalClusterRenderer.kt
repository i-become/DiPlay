package com.shilapi.xcertplay

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.text.NumberFormat

/** Used unchanged by the live overlay and the editor. The OEM speed/status UI is never painted here. */
internal class OriginalClusterRenderer(private val context: Context) {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val lanes = ClusterLaneStrip()
    private val car = BitmapFactory.decodeResource(context.resources, R.drawable.cluster_original_tire_car)
    // The supplied car artwork includes transparent margins. Use its visible body bounds.
    private val carSource = Rect(50, 63, 494, 1060)
    private val arrows = mutableMapOf<Int, android.graphics.drawable.Drawable>()

    fun draw(canvas: Canvas, width: Int, height: Int, layout: OriginalClusterLayout,
        vehicle: OemClusterVehicleState, nav: CarPlayGlance.Snapshot, editing: OriginalClusterComponent? = null) {
        canvas.save()
        canvas.scale(width / OriginalClusterLayout.WIDTH, height / OriginalClusterLayout.HEIGHT)
        OriginalClusterComponent.entries.forEach { component ->
            if (!layout[component].visible) return@forEach
            val bounds = layout.bounds(component)
            when (component) {
                OriginalClusterComponent.TIRES -> tires(canvas, bounds, vehicle)
                OriginalClusterComponent.DRIVING -> driving(canvas, bounds, vehicle)
                OriginalClusterComponent.TURN -> if (nav.connected && nav.navigating &&
                    (nav.maneuverType != null || lanes.fits(nav.lanes, bounds.width() - 30))) turn(canvas, bounds, nav)
                OriginalClusterComponent.TIME -> if (nav.connected && nav.navigating &&
                    (nav.arrivalEpochSeconds != null || nav.remainingSeconds != null)) time(canvas, bounds, nav)
            }
            if (editing == component) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                paint.color = Color.argb(180, 103, 200, 255)
                canvas.drawRoundRect(bounds, 16f, 16f, paint)
                paint.style = Paint.Style.FILL
            }
        }
        canvas.restore()
    }

    private fun panel(canvas: Canvas, r: RectF, color: Int, radius: Float) {
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawRoundRect(r, radius, radius, paint)
    }

    private fun tires(canvas: Canvas, r: RectF, v: OemClusterVehicleState) {
        panel(canvas, r, INFO_SURFACE, 15f)
        text(canvas, context.getString(R.string.settings_original_cluster_tires), r.left + 25, r.top + 36, 22f, r.width() - 50, bold = true)
        val carY = r.top + 108
        val carX = r.centerX() + 4
        val carArea = RectF(carX - 45, carY - 105, carX + 45, carY + 105)
        canvas.save(); canvas.rotate(-90f, carArea.centerX(), carArea.centerY())
        paint.color = Color.WHITE; paint.isFilterBitmap = true
        canvas.drawBitmap(car, carSource, carArea, paint); canvas.restore()
        // Rotating the car left puts its right wheels above and its front wheels on the left.
        val order = listOf(1, 3, 0, 2)
        val labels = listOf(R.string.settings_original_cluster_fr, R.string.settings_original_cluster_rr,
            R.string.settings_original_cluster_fl, R.string.settings_original_cluster_rl)
        order.forEachIndexed { slot, wheel ->
            val x = if (slot % 2 == 0) r.left + 92 else r.right - 92
            val y = r.top + if (slot < 2) 69 else 143
            val color = if (v.tireWarning.getOrNull(wheel) == true) Color.rgb(255, 77, 84) else Color.WHITE
            text(canvas, context.getString(labels[slot]), x, y, 18f, 100f, color, align = Paint.Align.CENTER)
            val value = v.tirePressureBar.getOrNull(wheel)?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
            text(canvas, value, x, y + 28, 24f, 90f, color, align = Paint.Align.CENTER)
            if (slot < 2) { paint.color = color; paint.strokeWidth = 1.5f
                canvas.drawLine(x - 34, y + 41, x + 34, y + 41, paint) }
        }
    }

    private fun driving(canvas: Canvas, r: RectF, v: OemClusterVehicleState) {
        panel(canvas, r, INFO_SURFACE, 15f)
        text(canvas, context.getString(R.string.settings_original_cluster_driving), r.left + 25, r.top + 37, 22f, r.width() - 50, bold = true)
        val labels = listOf(R.string.settings_original_cluster_consumption, R.string.settings_original_cluster_odometer, R.string.settings_original_cluster_range)
        val values = listOf(v.averageFuelConsumption?.let { String.format(Locale.getDefault(), "%.1f", it) },
            v.totalMileageKm?.let { String.format(Locale.getDefault(), "%.1f", it) }, v.remainingMileageKm?.toString())
        labels.forEachIndexed { i, label ->
            val x = r.left + 77 + i * 170
            val width = 150f
            text(canvas, context.getString(label), x, r.top + 78, 22f, width, align = Paint.Align.CENTER)
            text(canvas, values[i] ?: "—", x, r.top + 124, 29f, width, align = Paint.Align.CENTER)
            text(canvas, if (i == 0) "L/100km" else "km", x, r.top + 154, 18f, width, Color.LTGRAY, align = Paint.Align.CENTER)
        }
    }

    private fun turn(canvas: Canvas, r: RectF, nav: CarPlayGlance.Snapshot) {
        val showLanes = lanes.fits(nav.lanes, r.width() - 30)
        val maneuver = nav.maneuverType
        // Keep the reference card silhouette; lanes share the lower part of the same card.
        val contentHeight = if (maneuver != null) r.height() else 74f
        val card = RectF(r.left, r.top, r.right, r.top + contentHeight)
        panel(canvas, card, TURN_SURFACE, if (maneuver != null) 44f else 30f)
        if (maneuver != null) {
            val icon = NavigationWidgetUpdater.arrow(maneuver, nav.drivingSide)
            val straight = icon == R.drawable.ic_maneuver_straight
            val resource = if (straight) R.drawable.ic_original_cluster_straight else icon
            arrows.getOrPut(resource) { requireNotNull(context.getDrawable(resource)).mutate().apply { setTint(Color.WHITE) } }.let {
                val left = if (straight) 58 else 42
                val right = if (straight) 102 else 120
                val top = if (showLanes) 20 else 36
                it.setBounds((r.left + left).toInt(), (r.top + top).toInt(), (r.left + right).toInt(), (r.top + top + 82).toInt()); it.draw(canvas)
            }
            val distance = nav.distanceMeters?.let { if (it >= 1000) context.getString(R.string.original_cluster_kilometers,
                NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1; isGroupingUsed = false }.format(it / 1000.0))
                else context.getString(R.string.cluster_dashboard_meters, it) } ?: "—"
            text(canvas, distance, r.left + 144, r.top + 90, 40f, r.width() - 174, bold = true)
            text(canvas, nav.road, r.left + 34, r.top + if (showLanes) 133f else 157f, 28f, r.width() - 68, bold = true)
        }
        if (showLanes) lanes.draw(canvas, RectF(r.left + 20, card.bottom - 58, r.right - 20, card.bottom - 4), nav.lanes, Color.rgb(80, 222, 247))
    }

    private fun time(canvas: Canvas, r: RectF, nav: CarPlayGlance.Snapshot) {
        panel(canvas, r, TIME_SURFACE, r.height() / 2)
        val arrival = nav.arrivalEpochSeconds?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
        val duration = nav.remainingSeconds?.let { seconds ->
            val minutes = ((seconds + 59) / 60).toInt()
            if (minutes < 60) context.getString(R.string.widget_minutes, minutes)
            else context.getString(R.string.original_cluster_hours_minutes, minutes / 60, minutes % 60)
        }
        val value = listOfNotNull(arrival?.let { context.getString(R.string.settings_original_cluster_arrival, it) }, duration).joinToString("   ")
        paint.textSize = 28f; paint.typeface = Typeface.DEFAULT_BOLD
        val actual = TextUtils.ellipsize(value, paint, r.width() - 32, TextUtils.TruncateAt.END).toString()
        text(canvas, actual, r.centerX(),
            r.centerY() - (paint.ascent() + paint.descent()) / 2, 28f, r.width() - 32, bold = true, align = Paint.Align.CENTER)
    }

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, width: Float,
        color: Int = Color.WHITE, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        canvas.drawText(TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString(), x, y, paint)
    }

    companion object {
        // Calibrated against the provided 2048 × 768 reference. These are independent
        // surfaces: map details stay visible through the two information panels.
        private val INFO_SURFACE = Color.argb(92, 11, 11, 14)
        private val TURN_SURFACE = Color.argb(252, 1, 5, 16)
        private val TIME_SURFACE = Color.argb(232, 24, 28, 36)
    }
}
