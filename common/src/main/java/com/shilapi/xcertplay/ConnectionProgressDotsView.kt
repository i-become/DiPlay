package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.ceil

/** Twenty-seven dots; each completed connection step lights the next dot. */
internal class ConnectionProgressDotsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    var litCount: Int = 0
        set(value) {
            val clamped = value.coerceIn(0, CarPlayConnectionProgress.STEP_COUNT)
            if (field == clamped) return
            field = clamped
            invalidate()
        }

    var activeColor: Int = 0xFF_F1F5FC.toInt()
        set(value) {
            if (field == value) return
            field = value
            activePaint.color = value
            invalidate()
        }

    var inactiveColor: Int = 0x66_A8B6CA
        set(value) {
            if (field == value) return
            field = value
            inactivePaint.color = value
            invalidate()
        }

    private val density = resources.displayMetrics.density
    private val dotRadiusPx = 3f * density
    private val dotGapPx = 5f * density
    private val rowGapPx = 6f * density
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    init {
        activePaint.color = activeColor
        inactivePaint.color = inactiveColor
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxWidth = MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(0)
        val dotsPerRow = dotsPerRow(maxWidth)
        val rows = ceil(CarPlayConnectionProgress.STEP_COUNT.toFloat() / dotsPerRow).toInt()
        val rowWidth = rowWidth(dotsPerRow)
        val height = (rows * dotRadiusPx * 2 + (rows - 1).coerceAtLeast(0) * rowGapPx).toInt()
        setMeasuredDimension(rowWidth.toInt(), height)
    }

    override fun onDraw(canvas: Canvas) {
        val dotsPerRow = dotsPerRow(width)
        for (index in 0 until CarPlayConnectionProgress.STEP_COUNT) {
            val row = index / dotsPerRow
            val col = index % dotsPerRow
            val cx = dotRadiusPx + col * (dotRadiusPx * 2 + dotGapPx)
            val cy = dotRadiusPx + row * (dotRadiusPx * 2 + rowGapPx)
            val paint = if (index < litCount) activePaint else inactivePaint
            canvas.drawCircle(cx, cy, dotRadiusPx, paint)
        }
    }

    private fun dotsPerRow(availableWidth: Int): Int {
        if (availableWidth <= 0) return CarPlayConnectionProgress.STEP_COUNT
        val dotPitch = dotRadiusPx * 2 + dotGapPx
        return ((availableWidth + dotGapPx) / dotPitch).toInt().coerceIn(9, CarPlayConnectionProgress.STEP_COUNT)
    }

    private fun rowWidth(dotsInRow: Int): Float {
        val count = dotsInRow.coerceAtMost(CarPlayConnectionProgress.STEP_COUNT)
        return dotRadiusPx * 2 * count + dotGapPx * (count - 1).coerceAtLeast(0)
    }
}
