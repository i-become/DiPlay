package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import java.nio.ByteBuffer
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ClusterScreenFramesTest {
    @After fun cleanup() {
        ClusterScreenFrames.clear()
        AirPlayPersistence.saveClusterOnlyMode(RuntimeEnvironment.getApplication(), false)
    }

    private fun frame() {
        // Two useful RGBA pixels + a padded pixel per row; final padding may be absent.
        // Use the runtime bitmap byte layout: desktop Skia may use BGRA while Android
        // uses RGBA. This test verifies stride/cropping, independent of that host detail.
        val source = Bitmap.createBitmap(intArrayOf(Color.RED, Color.GREEN, Color.MAGENTA,
            Color.BLUE, Color.YELLOW, Color.MAGENTA), 3, 2, Bitmap.Config.ARGB_8888)
        val bytes = ByteBuffer.allocateDirect(24)
        source.copyPixelsToBuffer(bytes)
        bytes.rewind()
        bytes.limit(20) // final row padding is not guaranteed by ImageReader
        source.recycle()
        ClusterScreenFrames.update(2, 2, 4, 12, bytes)
    }

    @Test fun standaloneMapShowsAWaitingPlaceholderUntilTheFirstCapturedFrame() {
        val context = RuntimeEnvironment.getApplication()
        AirPlayPersistence.saveClusterOnlyMode(context, true)
        val view = ClusterScreenCopyView(context, ClusterStyle.ORIGINAL).apply { layout(0, 0, 300, 200) }
        val output = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(output))
        assertEquals(Color.rgb(7, 9, 18), output.getPixel(0, 0))
        assertTrue(ClusterScreenFrames.status().contains("frame=none"))
        output.recycle()
    }

    @Test fun paddedRowsAreExcludedAndCropsStretchToEveryPixelOfTheFixedWindow() {
        frame()
        val output = Bitmap.createBitmap(10, 20, Bitmap.Config.ARGB_8888)
        ClusterScreenFrames.draw(Canvas(output), ClusterCopyRegion(0f, 0f, .5f, 1f),
            1f, output.width, output.height, Paint())
        assertEquals(Color.RED, output.getPixel(9, 0))
        assertEquals(Color.BLUE, output.getPixel(9, 19))
        assertEquals(10, output.width)
        assertEquals(20, output.height)
    }

    @Test fun truncatedVisiblePixelsAreRejectedInsteadOfPublishingAStaleFrame() {
        val bytes = ByteBuffer.allocateDirect(19) // 20 bytes are needed for two visible 2-pixel rows.
        try {
            ClusterScreenFrames.update(2, 2, 4, 12, bytes)
            fail("truncated ImageReader frame should be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("truncated"))
        }
        assertFalse(ClusterScreenFrames.hasFrame())
    }

    @Test fun stopClearsOldPixelsAndAnewSessionCanPublishAgain() {
        frame()
        ClusterScreenFrames.clear()
        val output = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        ClusterScreenFrames.draw(canvas, ClusterCopyRegion(0f, 0f, 1f, 1f), 1f, 2, 2, Paint())
        assertEquals(Color.TRANSPARENT, output.getPixel(0, 0))
        frame()
        ClusterScreenFrames.draw(canvas, ClusterCopyRegion(0f, 0f, 1f, 1f), 1f, 2, 2, Paint())
        assertEquals(Color.RED, output.getPixel(0, 0))
        assertEquals(Color.YELLOW, output.getPixel(1, 1))
    }

    @Test fun standaloneModeAutomaticallyUsesEachStylesRegionIgnoringTheRetiredSwitch() {
        val context = RuntimeEnvironment.getApplication() as Context
        frame()
        AirPlayPersistence.saveClusterOnlyMode(context, true)
        assertFalse(DiPlayLauncher.startAutomaticConnection(context))
        val regions = listOf(ClusterCopyRegion(0f, 0f, .5f, .5f),
            ClusterCopyRegion(.5f, 0f, 1f, .5f), ClusterCopyRegion(0f, .5f, .5f, 1f))
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        ClusterStyle.entries.forEachIndexed { index, style ->
            context.getSharedPreferences("cluster_copy_region_${style.preferenceValue}", Context.MODE_PRIVATE)
                .edit().clear().putBoolean("enabled", false).commit()
            ClusterCopyRegion.save(context, regions[index], style)
        }
        ClusterStyle.entries.forEachIndexed { index, style ->
            val view = ClusterScreenCopyView(context, style).apply { layout(0, 0, 30, 20) }
            val output = Bitmap.createBitmap(30, 20, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(output))
            assertEquals(colors[index], output.getPixel(15, 10))
            // A retained capture frame must never leak into the CarPlay map path.
            AirPlayPersistence.saveClusterOnlyMode(context, false)
            output.eraseColor(Color.TRANSPARENT)
            view.draw(Canvas(output))
            assertEquals(Color.TRANSPARENT, output.getPixel(15, 10))
            AirPlayPersistence.saveClusterOnlyMode(context, true)
            output.recycle()
        }
    }

    @Test fun zoomAlwaysSamplesTheOriginalFrameAndKeepsTheWindowFilled() {
        val colors = intArrayOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW)
        val source = Bitmap.createBitmap(IntArray(16) { colors[it % 4] }, 4, 4, Bitmap.Config.ARGB_8888)
        val bytes = ByteBuffer.allocateDirect(64)
        source.copyPixelsToBuffer(bytes)
        bytes.rewind()
        ClusterScreenFrames.update(4, 4, 4, 16, bytes)
        source.recycle()
        val output = Bitmap.createBitmap(8, 6, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val region = ClusterCopyRegion(0f, 0f, 1f, 1f)
        ClusterScreenFrames.draw(canvas, region, 2f, 8, 6, Paint())
        assertEquals(Color.GREEN, output.getPixel(0, 0))
        assertEquals(Color.BLUE, output.getPixel(7, 5))
        ClusterScreenFrames.draw(canvas, region, 1f, 8, 6, Paint())
        assertEquals(Color.RED, output.getPixel(0, 0))
        assertEquals(Color.YELLOW, output.getPixel(7, 5))
    }
}
