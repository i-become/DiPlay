package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterLyricScrollTest {
    @Test fun overflowingLineRevealsOnceThenStopsAndResetsOnlyForNewContent() {
        var now = 0L
        val scroll = ClusterLyricScroll { now }
        assertEquals(0f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertTrue(scroll.animating)
        now = 200
        assertEquals(0f, scroll.offset("song-line1", 500f, 300f), 0f)
        now = 1083
        assertEquals(100f, scroll.offset("song-line1", 500f, 300f), .2f)
        now = 10000
        assertEquals(200f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertFalse(scroll.animating)
        now = 90000
        assertEquals(200f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertEquals(0f, scroll.offset("song-line2", 500f, 300f), 0f)
        assertTrue(scroll.animating)
        assertEquals(0f, scroll.offset("short", 250f, 300f), 0f)
        assertFalse(scroll.animating)
    }
    @Test fun timedLineFinishesBeforeNextLineAndFasterLinesMoveFaster() {
        var now = 0L
        val scroll = ClusterLyricScroll { now }
        scroll.offset("fast", 500f, 300f, 2000L)
        now = 1000L
        val fast = scroll.offset("fast", 500f, 300f, 1000L)
        assertTrue(fast > 90f)
        now = 1750L
        assertEquals(200f, scroll.offset("fast", 500f, 300f, 250L), .01f)
        now = 2000L
        scroll.offset("slow", 500f, 300f, 6000L)
        now = 3000L
        assertTrue(scroll.offset("slow", 500f, 300f, 5000L) < fast)
    }

    @Test fun rotatedBaselineMovesGraphemesWhileKeepingEachGlyphUpright() {
        val image = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
            fun bounds(): IntArray {
                var minX = image.width; var maxX = 0; var minY = image.height; var maxY = 0
                for (y in 0 until image.height) for (x in 0 until image.width) {
                    if (Color.alpha(image.getPixel(x, y)) > 128) {
                        minX = minOf(minX, x); maxX = maxOf(maxX, x)
                        minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    }
                }
                return intArrayOf(minX, maxX, minY, maxY)
            }
            Canvas(image).apply { rotate(90f, 120f, 120f) }
                .also { drawUprightGlyphs(it, "H", 100f, 120f, paint, 90f) }
            val glyph = bounds()
            assertTrue("A glyph remains upright", glyph[3] - glyph[2] > glyph[1] - glyph[0])
            image.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(image).apply { rotate(90f, 120f, 120f) }
            drawUprightGlyphs(canvas, "H H", 80f, 120f, paint, 90f)
            val line = bounds()
            assertTrue("Glyph anchors follow the rotated baseline", line[3] - line[2] > line[1] - line[0])
        } finally { image.recycle() }
    }

    @Test fun verticalNumbersGainSpacingAndFitTheirComponentWidth() {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 30f }
        val value = "111111"
        val verticalWidth = uprightTextAdvance(value, paint, 90f)
        val digitBounds = android.graphics.Rect().also { paint.getTextBounds("1", 0, 1, it) }
        assertTrue(verticalWidth >= value.length * (digitBounds.height() + 2f))
        val image = Bitmap.createBitmap(140, 100, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(image).apply { rotate(90f, 70f, 50f) }
            val fittedWidth = drawUprightGlyphs(canvas, value, 15f, 50f, paint, 90f, fitWidth = 120f)
            assertTrue(fittedWidth <= 120f)
        } finally { image.recycle() }
    }

    @Test fun verticalDigitsHaveVisibleGapsForEveryAlignment() {
        val image = Bitmap.createBitmap(300, 400, Bitmap.Config.ARGB_8888)
        try {
            for (align in Paint.Align.values()) {
                image.eraseColor(Color.TRANSPARENT)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.WHITE; textSize = 40f; textAlign = align
                }
                val canvas = Canvas(image).apply { rotate(90f, 150f, 200f) }
                val anchor = when (align) {
                    Paint.Align.LEFT -> 40f
                    Paint.Align.CENTER -> 150f
                    else -> 260f
                }
                drawUprightGlyphs(canvas, "111111", anchor, 200f, paint, 90f, align, 220f)
                var groups = 0
                var previousInk = false
                for (y in 0 until image.height) {
                    val ink = (0 until image.width).any { x -> Color.alpha(image.getPixel(x, y)) > 128 }
                    if (ink && !previousInk) groups++
                    previousInk = ink
                }
                assertEquals("Each digit must have a visible gap with $align alignment", 6, groups)
            }
        } finally { image.recycle() }
    }

    @Test fun mirroredCardsKeepTextReadableEvenWhenRotated() {
        val expected = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(400, 400, Bitmap.Config.ARGB_8888)
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 36f }
            val baseline = 200f - (paint.ascent() + paint.descent()) / 2f
            val bounds = android.graphics.RectF(100f, 100f, 300f, 300f)
            for (rotation in listOf(30f, 90f, -45f)) {
                expected.eraseColor(Color.TRANSPARENT)
                val reference = Canvas(expected).apply { rotate(rotation, 200f, 200f) }
                drawUprightGlyphs(reference, "F12", 200f, baseline, paint, rotation, Paint.Align.CENTER)
                for ((horizontal, vertical) in listOf(true to false, false to true, true to true)) {
                    actual.eraseColor(Color.TRANSPARENT)
                    val canvas = Canvas(actual).apply { concat(originalTransform(bounds,
                        OriginalClusterPlacement(x = 0f, y = 0f, rotation = rotation,
                            flipHorizontal = horizontal, flipVertical = vertical))) }
                    drawUprightGlyphs(canvas, "F12", 200f, baseline, paint, rotation, Paint.Align.CENTER,
                        flipHorizontal = horizontal, flipVertical = vertical)
                    val a = IntArray(400 * 400); val b = IntArray(a.size)
                    expected.getPixels(a, 0, 400, 0, 0, 400, 400)
                    actual.getPixels(b, 0, 400, 0, 0, 400, 400)
                    val error = a.indices.sumOf { kotlin.math.abs(Color.alpha(a[it]) - Color.alpha(b[it])).toLong() }
                    assertTrue("Text keeps its glyphs and reading order at $rotation, $horizontal/$vertical", error < 1000)
                }
            }
        } finally { expected.recycle(); actual.recycle() }
    }

    @Test fun horizontalFlipKeepsTheBeginningAndEndOfScrollingLyricsVisible() {
        val expected = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888)
        val actual = Bitmap.createBitmap(240, 100, Bitmap.Config.ARGB_8888)
        try {
            var now = 0L
            val normal = ClusterLyricScroll { now }; val flipped = ClusterLyricScroll { now }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 30f }
            for (time in listOf(0L, 90000L)) {
                now = time
                expected.eraseColor(Color.TRANSPARENT); actual.eraseColor(Color.TRANSPARENT)
                normal.draw(Canvas(expected), "First words then final words", "track", paint, 20f, 60f, 200f)
                val canvas = Canvas(actual).apply { scale(-1f, 1f, 120f, 50f) }
                flipped.draw(canvas, "First words then final words", "track", paint, 20f, 60f, 200f, flipHorizontal = true)
                assertTrue("Flip preserves the lyric viewport at $time", expected.sameAs(actual))
            }
        } finally { expected.recycle(); actual.recycle() }
    }
}
