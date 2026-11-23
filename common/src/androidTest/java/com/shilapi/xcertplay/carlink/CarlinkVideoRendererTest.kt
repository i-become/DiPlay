package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.os.Handler
import android.os.Looper
import android.view.Surface
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shilapi.xcertplay.ClusterCopyRegion
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Real EGL/SurfaceTexture test; Robolectric cannot exercise decoder surface lifetime. */
@RunWith(AndroidJUnit4::class)
class CarlinkVideoRendererTest {
    @Test fun nativeCropContinuesAfterCentreOutputIsDetached() {
        val surfaces = LinkedBlockingQueue<Surface>()
        val frames = LinkedBlockingQueue<Bitmap>()
        val events = LinkedBlockingQueue<String>()
        val renderer = CarlinkVideoRenderer(1920, 720, { surfaces.offer(it) },
            { frames.offer(it.copy(Bitmap.Config.ARGB_8888, false)) }, { events.offer(it) })
        val main = Handler(Looper.getMainLooper())
        var centreTexture: SurfaceTexture? = null
        try {
            renderer.start()
            val decoder = surfaces.poll(10, TimeUnit.SECONDS)
            assertNotNull("Decoder did not initialize: $events", decoder)
            val attached = java.util.concurrent.CountDownLatch(1)
            main.post {
                centreTexture = SurfaceTexture(false).apply { setDefaultBufferSize(1920, 720) }
                renderer.attach(Surface(centreTexture), 1920, 720)
                attached.countDown()
            }
            assertTrue(attached.await(5, TimeUnit.SECONDS))
            renderer.setCopyRegion(ClusterCopyRegion(0f, 0f, 1f, 1f))
            fun sendFrame(top: Int, bottom: Int, expectedFirstPixel: Int): Bitmap {
                // SurfaceTexture callbacks may still deliver a frame from before the crop change.
                // Wait for this pattern, rather than treating a queued older frame as fresh input.
                frames.clear()
                repeat(20) {
                    val canvas = decoder!!.lockCanvas(null)
                    canvas.drawColor(top)
                    canvas.drawRect(0f, 360f, 1920f, 720f, Paint().apply { color = bottom })
                    canvas.drawRect(1000f, 300f, 1001f, 301f, Paint().apply { color = Color.WHITE })
                    decoder.unlockCanvasAndPost(canvas)
                    frames.poll(100, TimeUnit.MILLISECONDS)?.let {
                        if (it.getPixel(20, 20) == expectedFirstPixel) return it
                        it.recycle()
                    }
                }
                fail("No copied frame: $events")
                error("unreachable")
            }
            val full = sendFrame(Color.RED, Color.BLUE, Color.RED)
            assertEquals(1920, full.width); assertEquals(720, full.height)
            assertEquals(Color.RED, full.getPixel(20, 20))
            assertEquals(Color.BLUE, full.getPixel(20, 600))
            assertEquals(Color.WHITE, full.getPixel(1000, 300))
            assertEquals(Color.RED, full.getPixel(999, 300))
            assertEquals(Color.RED, full.getPixel(1001, 300))
            renderer.attach(null)
            renderer.setCopyRegion(ClusterCopyRegion(.25f, .5f, .75f, 1f))
            val background = sendFrame(Color.YELLOW, Color.GREEN, Color.GREEN)
            assertEquals(960, background.width); assertEquals(360, background.height)
            assertEquals(Color.GREEN, background.getPixel(20, 20))
            assertTrue("EGL errors: $events", events.none { "video error" in it })
        } finally {
            renderer.close()
            main.post { centreTexture?.release() }
        }
    }
}
