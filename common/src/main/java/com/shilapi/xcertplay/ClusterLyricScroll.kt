package com.shilapi.xcertplay

import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import java.text.BreakIterator
import java.util.Locale

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
             left: Float, baseline: Float, width: Float, centered: Boolean = false, remainingMillis: Long? = null,
             counterRotation: Float = 0f, flipHorizontal: Boolean = false, flipVertical: Boolean = false) {
        val textWidth = uprightTextAdvance(value, paint, counterRotation)
        val offset = offset(listOf(track, value, width, paint.textSize), textWidth, width, remainingMillis)
        paint.textAlign = Paint.Align.LEFT
        val x = left + if (centered && textWidth <= width) (width - textWidth) / 2 else -offset
        val save = canvas.save()
        canvas.clipRect(left, baseline + paint.ascent() - 2f, left + width, baseline + paint.descent() + 2f)
        // The viewport, rather than the overflowing run, is the mirrored layout slot.
        canvas.scale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f,
            left + width / 2f, baseline + (paint.ascent() + paint.descent()) / 2f)
        drawUprightGlyphs(canvas, value, x, baseline, paint, counterRotation)
        canvas.restoreToCount(save)
    }
}

/** Keeps each grapheme upright while its anchor follows the component's rotated baseline. */
internal fun drawUprightGlyphs(canvas: Canvas, value: String, x: Float, baseline: Float,
    paint: Paint, rotation: Float, align: Paint.Align = Paint.Align.LEFT,
    fitWidth: Float? = null, flipHorizontal: Boolean = false, flipVertical: Boolean = false): Float {
    if ((rotation == 0f && !flipHorizontal && !flipVertical) || value.isEmpty()) {
        canvas.drawText(value, x, baseline, paint)
        return paint.measureText(value)
    }
    val glyphs = uprightGlyphs(value, paint, rotation)
    val naturalWidth = glyphs.sumOf { it.cellAdvance.toDouble() }.toFloat()
    val scale = fitWidth?.takeIf { naturalWidth > it && it > 0f }?.div(naturalWidth) ?: 1f
    // Alignment is applied to the complete run below, never separately to each glyph path.
    val drawPaint = Paint(paint).apply { textSize *= scale; textAlign = Paint.Align.LEFT }
    val width = naturalWidth * scale
    val lineLeft = when (align) {
        Paint.Align.CENTER -> x - width / 2f
        Paint.Align.RIGHT -> x - width
        else -> x
    }
    // Reflect the text block's position with its card, but preserve reading order and glyph shape.
    val saved = canvas.save()
    canvas.scale(if (flipHorizontal) -1f else 1f, if (flipVertical) -1f else 1f,
        lineLeft + width / 2f, baseline + (drawPaint.ascent() + drawPaint.descent()) / 2f)
    var position = lineLeft
    glyphs.forEach { glyph ->
        val cellAdvance = glyph.cellAdvance * scale
        val advance = glyph.advance * scale
        val glyphX = position + (cellAdvance - advance) / 2f
        val path = android.graphics.Path()
        drawPaint.getTextPath(value, glyph.start, glyph.end, glyphX, baseline, path)
        val pivotX = glyphX + advance / 2f
        val pivotY = baseline + (drawPaint.ascent() + drawPaint.descent()) / 2f
        path.transform(android.graphics.Matrix().apply { setRotate(-rotation, pivotX, pivotY) })
        canvas.drawPath(path, drawPaint)
        position += cellAdvance
    }
    canvas.restoreToCount(saved)
    return width
}

internal fun uprightTextAdvance(value: String, paint: Paint, rotation: Float): Float =
    if (rotation == 0f) paint.measureText(value) else uprightGlyphs(value, paint, rotation).sumOf { it.cellAdvance.toDouble() }.toFloat()

private data class UprightGlyph(val start: Int, val end: Int, val advance: Float, val cellAdvance: Float)

private fun uprightGlyphs(value: String, paint: Paint, rotation: Float): List<UprightGlyph> {
    val vertical = kotlin.math.abs(kotlin.math.sin(Math.toRadians(rotation.toDouble()))).toFloat()
    val horizontal = kotlin.math.abs(kotlin.math.cos(Math.toRadians(rotation.toDouble()))).toFloat()
    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(value) }
    val result = mutableListOf<UprightGlyph>()
    var start = iterator.first()
    var prefixAdvance = 0f
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val endAdvance = paint.measureText(value, 0, end)
        val advance = (endAdvance - prefixAdvance).coerceAtLeast(0f)
        val bounds = android.graphics.Rect()
        paint.getTextBounds(value, start, end, bounds)
        val requiredStep = when {
            bounds.isEmpty -> advance
            vertical < .001f -> bounds.width().toFloat() + 2f
            horizontal < .001f -> bounds.height().toFloat() + 2f
            else -> minOf(bounds.width() / horizontal, bounds.height() / vertical) + 2f
        }
        result += UprightGlyph(start, end, advance, maxOf(advance, requiredStep))
        start = end
        prefixAdvance = endAdvance
        end = iterator.next()
    }
    return result
}
