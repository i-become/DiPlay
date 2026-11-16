package com.shilapi.xcertplay

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock

/** A single reveal of an overflowing line, never a looping marquee. Coordinates are design units. */
internal class ClusterLyricScroll(private val clock: () -> Long = SystemClock::uptimeMillis) {
    private var identity: Any? = null
    private var began = 0L
    private var revealDuration = 0L
    private var initialDelay = 0L
    var animating = false
        private set

    internal fun offset(key: Any, textWidth: Float, windowWidth: Float, remainingMillis: Long? = null): Float {
        val now = clock()
        val overflow = (textWidth - windowWidth).coerceAtLeast(0f)
        if (identity != key) {
            identity = key; began = now
            revealDuration = remainingMillis?.let { (it * 3 / 4).coerceAtLeast(1L) }
                ?: (overflow / 0.12f).toLong().coerceAtLeast(1L)
            initialDelay = if (remainingMillis != null) (revealDuration / 6).coerceAtMost(300L) else 250L
        }
        val offset = (overflow * (now - began - initialDelay).coerceAtLeast(0L).toFloat() / revealDuration).coerceAtMost(overflow)
        animating = offset < overflow
        return offset
    }

    fun draw(canvas: Canvas, value: String, track: Any, paint: Paint,
             left: Float, baseline: Float, width: Float, centered: Boolean = false, remainingMillis: Long? = null) {
        val textWidth = paint.measureText(value)
        val offset = offset(listOf(track, value, width, paint.textSize), textWidth, width, remainingMillis)
        val save = canvas.save()
        canvas.clipRect(left, baseline + paint.ascent() - 2f, left + width, baseline + paint.descent() + 2f)
        paint.textAlign = Paint.Align.LEFT
        val x = left + if (centered && textWidth <= width) (width - textWidth) / 2 else -offset
        canvas.drawText(value, x, baseline, paint)
        canvas.restoreToCount(save)
    }
}
