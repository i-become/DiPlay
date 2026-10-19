package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View

/**
 * Bench overlays when the iPhone sends only the center navigation map on stream 111.
 * CarPlay Ultra on real cars composites left/right gauges on the phone; here DiPlay draws placeholders.
 */
internal class SimulatedUltraLeftSpeedGauge(context: Context) : View(context) {
    private var speedKmh = 0
    private var stopObserving: (() -> Unit)? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(180, 120, 255, 160)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val unitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(210, 210, 220, 220)
        textAlign = Paint.Align.CENTER
    }
    private val arc = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopObserving = SimulatedClusterSpeedHub.observe { speed ->
            speedKmh = speed ?: 0
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopObserving?.invoke()
        stopObserving = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.17f
        val cy = height * 0.52f
        val radius = minOf(width, height) * 0.19f
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arc, 135f, 270f, false, ringPaint)
        textPaint.textSize = radius * 0.55f
        unitPaint.textSize = radius * 0.18f
        canvas.drawText(speedKmh.toString(), cx, cy + radius * 0.08f, textPaint)
        canvas.drawText("km/h", cx, cy + radius * 0.42f, unitPaint)
    }
}

internal class SimulatedUltraRightGauge(context: Context) : View(context) {
    private var speedKmh = 0
    private var stopObserving: (() -> Unit)? = null

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
        color = Color.argb(180, 120, 255, 160)
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 200, 205, 210)
        textAlign = Paint.Align.CENTER
        textSize = 28f
    }
    private val arc = RectF()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopObserving = SimulatedClusterSpeedHub.observe { speed ->
            speedKmh = speed ?: 0
            postInvalidateOnAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopObserving?.invoke()
        stopObserving = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width * 0.83f
        val cy = height * 0.52f
        val radius = minOf(width, height) * 0.19f
        val rpm = (800 + speedKmh * 45).coerceIn(800, 6500)
        arc.set(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(arc, 135f, 270f, false, ringPaint)
        textPaint.textSize = radius * 0.38f
        canvas.drawText((rpm / 1000f).toString(), cx, cy + radius * 0.05f, textPaint)
        labelPaint.textSize = radius * 0.16f
        canvas.drawText("x1000 rpm", cx, cy + radius * 0.38f, labelPaint)
        labelPaint.textSize = radius * 0.14f
        canvas.drawText("90°C", cx, cy + radius * 0.78f, labelPaint)
    }
}
