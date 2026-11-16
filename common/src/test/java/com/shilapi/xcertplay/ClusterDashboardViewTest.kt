package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.graphics.RectF
import android.os.Looper
import android.view.Surface
import android.view.View
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClusterDashboardViewTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun mapFitsItsOwnRegionAtDifferentClusterSizesAndSourceAspects() {
        for ((w, h) in listOf(1920 to 720, 1280 to 480, 1920 to 480, 800 to 600)) {
            for (aspect in listOf(5f / 3f, 4f / 3f, 8f / 3f, 16f / 9f, 4f)) {
                val map = ClusterDashboardView.mapBounds(w, h, aspect)
                val scale = minOf(w / 1920f, h / 720f)
                val offsetX = (w - 1920f * scale) / 2
                val offsetY = (h - 720f * scale) / 2
                val mapSlot = RectF(offsetX + OemClusterGeometry.MAP_LEFT * scale,
                    offsetY + OemClusterGeometry.MAP_TOP * scale,
                    offsetX + (OemClusterGeometry.MAP_LEFT + OemClusterGeometry.MAP_WIDTH) * scale,
                    offsetY + (OemClusterGeometry.MAP_TOP + OemClusterGeometry.MAP_HEIGHT) * scale)
                assertTrue(mapSlot.left - 0.01 <= map.left && map.right <= mapSlot.right + 0.01)
                assertTrue(mapSlot.top - 0.01 <= map.top && map.bottom <= mapSlot.bottom + 0.01)
                assertEquals(aspect, map.width() / map.height(), 0.001f)
            }
        }
    }

    @Test fun copiedMapIsVisibleWithoutCarPlayConnectionOrNavigationMetadata() {
        for (style in listOf(ClusterStyle.BASIC, ClusterStyle.SPORT)) {
            val view = ClusterDashboardView(context, style = style, readState = { ClusterDashboardState() })
            val frame = android.graphics.Bitmap.createBitmap(640, 360, android.graphics.Bitmap.Config.RGB_565)
            view.setCopiedMap(frame)
            assertEquals(View.GONE, view.getChildAt(1).visibility)
            view.refreshState()
            assertEquals(View.GONE, view.getChildAt(1).visibility)
            view.setCopiedMap(null)
            assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
            frame.recycle()
        }
    }

    @Test fun instrumentForegroundIsAboveBothVideoAndDisconnectPlaceholder() {
        val view = ClusterDashboardView(context)
        assertEquals(4, view.childCount)
        assertSame(view.mapViewport, view.getChildAt(0))
        assertSame(view.mapViewport, view.mapSurface.parent)
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
        val foreground = view.getChildAt(2)
        assertFalse(foreground.isOpaque)
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1920, 720)
        assertEquals(1920, foreground.width)
        assertEquals(720, foreground.height)
        assertEquals(1920, view.getChildAt(3).width)
        assertEquals(720, view.getChildAt(3).height)
        val expected = ClusterDashboardView.mapBounds(1920, 720, OemClusterGeometry.MAP_ASPECT)
        assertEquals(Math.round(expected.top), view.mapViewport.top)
        assertEquals(Math.round(expected.top) + Math.round(expected.height()), view.mapViewport.bottom)
        assertEquals(0, view.mapSurface.top)
        assertEquals(view.mapViewport.height, view.mapSurface.height)
    }

    @Test fun zoomNeverMovesOrResizesAnyInstrumentMapAperture() {
        for (style in listOf(ClusterStyle.BASIC, ClusterStyle.SPORT)) {
            val view = ClusterDashboardView(context, style = style, readState = { liveState() })
            view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1920, 720)
            val aperture = view.mapViewport
            val bounds = android.graphics.Rect(aperture.left, aperture.top, aperture.right, aperture.bottom)
            val surface = view.decoderSurface
            for (zoom in listOf(50, 100, 200, 150, 200, 100)) {
                ClusterMapCropSettings.saveZoom(context, zoom)
                view.refreshState()
                assertEquals(bounds, android.graphics.Rect(aperture.left, aperture.top, aperture.right, aperture.bottom))
                assertTrue(aperture.clipChildren)
                assertTrue(aperture.clipToPadding)
                assertEquals(1f, aperture.scaleX, 0f)
                assertEquals(1f, view.mapSurface.scaleX, 0f)
                val matrix = view.mapSurface.getTransform(android.graphics.Matrix())
                val points = floatArrayOf(0f, 0f, view.mapSurface.width.toFloat(), view.mapSurface.height.toFloat())
                matrix.mapPoints(points)
                assertTrue(points[0] <= 0f && points[1] <= 0f)
                assertTrue(points[2] >= aperture.width && points[3] >= aperture.height)
                val values = FloatArray(9)
                matrix.getValues(values)
                assertEquals((zoom / 100f).coerceAtLeast(1f), values[0], .001f)
                assertSame(surface, view.decoderSurface)
            }
        }
        ClusterMapCropSettings.saveZoom(context, 100)
    }

    @Test fun liveCardsDoNotDependOnMapVideoOrLegacyOverlayPreference() {
        AirPlayPersistence.saveClusterContent(context, CarPlayClusterDisplay.Content.MAP)
        val view = ClusterDashboardView(context, readState = { liveState() })
        view.refreshState()
        assertTrue(view.contentDescription.toString().contains("测试道路"))
        assertTrue(view.contentDescription.toString().contains("测试歌曲"))
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
        view.setStreamActive(true)
        assertEquals(View.GONE, view.getChildAt(1).visibility)
        view.setStreamActive(false)
        assertTrue(view.contentDescription.toString().contains("测试歌曲"))
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
    }

    @Test fun disconnectClearsOldSongRouteAndCoverEvenBeforeWindowCloses() {
        var snapshot = liveState()
        val view = ClusterDashboardView(context, readState = { snapshot })
        view.setStreamActive(true)
        view.refreshState()
        snapshot = snapshot.copy(guidance = snapshot.guidance.copy(connected = false))
        view.refreshState()
        assertFalse(view.contentDescription.toString().contains("测试道路"))
        assertFalse(view.contentDescription.toString().contains("测试歌曲"))
        assertEquals(ClusterDashboardState(), snapshot.current())
        assertEquals(View.VISIBLE, view.getChildAt(1).visibility)
    }

    @Test fun hiddenAndDetachedWindowsStopReadingMetadata() {
        CarPlayGlance.setConnected(false)
        val activity = Robolectric.buildActivity(Activity::class.java).create()
        var reads = 0
        val view = ClusterDashboardView(activity.get(), readState = { reads++; liveState() })
        activity.get().setContentView(view)
        activity.start().resume().visible()
        // Robolectric's API 28 window starts attached but GONE; mirror ViewRootImpl's show callback.
        val attachInfo: Any = ReflectionHelpers.getField(view, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2100))
        assertEquals("Only the initial state may be read without an event", 1, reads)
        CarPlayGlance.setConnected(true)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals("A connection event must update without waiting for a timer", 2, reads)
        view.setDashboardVisible(false)
        val beforeHide = reads
        CarPlayGlance.setConnected(false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(beforeHide, reads)
        view.setDashboardVisible(true)
        assertTrue(reads > beforeHide)
        activity.get().setContentView(View(activity.get()))
        val beforeDetach = reads
        CarPlayGlance.setConnected(true)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertEquals(beforeDetach, reads)
        activity.pause().stop().destroy()
        CarPlayGlance.setConnected(false)
    }

    @Test fun navigationStartStopAndMissingManeuversKeepTheExistingVideoSurfaceAndViewport() {
        var snapshot = ClusterDashboardState(guidance = CarPlayGlance.Snapshot(connected = true))
        val view = ClusterDashboardView(context, readState = { snapshot })
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1920, 720)
        val surface = view.decoderSurface
        val bounds = android.graphics.Rect(view.mapSurface.left, view.mapSurface.top,
            view.mapSurface.right, view.mapSurface.bottom)
        for (state in listOf(liveState(), liveState().copy(guidance = liveState().guidance.copy(
            maneuverType = null, remainingMeters = 10000)), snapshot)) {
            snapshot = state
            view.refreshState()
            assertSame(surface, view.decoderSurface)
            assertEquals(bounds, android.graphics.Rect(view.mapSurface.left, view.mapSurface.top,
                view.mapSurface.right, view.mapSurface.bottom))
        }
    }

    @Test fun navigationCropNeverMovesTheSurfaceOutsideItsFixedViewport() {
        var state = ClusterDashboardState(guidance = CarPlayGlance.Snapshot(connected = true))
        val activity = Robolectric.buildActivity(Activity::class.java).create()
        ClusterMapCropSettings.save(activity.get(), 50)
        val view = ClusterDashboardView(activity.get(), readState = { state })
        activity.get().setContentView(view)
        activity.start().resume().visible()
        val attachInfo: Any = ReflectionHelpers.getField(view, "mAttachInfo")
        ReflectionHelpers.setField(attachInfo, "mWindowVisibility", View.VISIBLE)
        view.dispatchWindowVisibilityChanged(View.VISIBLE)
        view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 1920, 720)
        val surface = view.decoderSurface
        val size = view.mapSurface.width to view.mapSurface.height
        assertEquals(1f, view.mapSurface.scaleX, 0f)
        state = state.copy(guidance = state.guidance.copy(navigating = true, maneuverType = null))
        view.refreshState()
        assertEquals(1f, view.mapSurface.scaleX, 0f)
        assertEquals(1f, view.mapSurface.scaleY, 0f)
        assertEquals(0f, view.mapSurface.translationX, 0f)
        ClusterMapCropSettings.save(activity.get(), 40)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1f, view.mapSurface.scaleX, 0f)
        assertSame(surface, view.decoderSurface)
        assertEquals(size, view.mapSurface.width to view.mapSurface.height)
        state = state.copy(guidance = state.guidance.copy(navigating = false))
        view.refreshState()
        assertEquals(1f, view.mapSurface.scaleX, 0f)
        assertEquals(0f, view.mapSurface.translationX, 0f)
        assertEquals(0f, view.mapSurface.translationY, 0f)
        assertSame(surface, view.decoderSurface)
        activity.pause().stop().destroy()
        ClusterMapCropSettings.save(context, 0)
    }

    @Test fun progressUsesPhonePositionAndNeverInventsMissingDuration() {
        assertNull(ClusterDashboardState().progress())
        val music = CarPlayNowPlaying(elapsedMillis = 50, durationMillis = 100)
        fun progress(track: CarPlayNowPlaying) = ClusterDashboardState(music = ClusterDashboardMusic(track)).progress()
        assertEquals(0.5f, progress(music)!!, 0.001f)
        assertEquals(1f, progress(music.copy(elapsedMillis = 200))!!, 0.001f)
        assertEquals(0f, progress(music.copy(elapsedMillis = -10))!!, 0.001f)
        assertNull(progress(music.copy(elapsedMillis = null)))
        assertNull(progress(music.copy(durationMillis = 0)))
    }

    @Test fun hongqiPresentationUsesDashboardAndStillHandsOffTheMapSurface() {
        AirPlayPersistence.saveClusterStyle(context, ClusterStyle.BASIC)
        val old = android.os.Build.BRAND
        ShadowBuild.setBrand("Hongqi")
        val displayId = ShadowDisplayManager.addDisplay("w1920dp-h720dp-land-mdpi")
        val display = context.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId)
        val surfaces = mutableListOf<Surface?>()
        val presentation = ClusterMapPresentation(context, display, onSurface = { surfaces += it })
        try {
            assertTrue(ClusterDashboardView.enabled(context))
            AirPlayPersistence.saveClusterContent(context, CarPlayClusterDisplay.Content.INSTRUMENTS)
            assertEquals(CarPlayClusterDisplay.Content.MAP, AirPlayPersistence.effectiveClusterContent(context))
            presentation.show()
            val dashboard: ClusterDashboardView = ReflectionHelpers.getField(presentation, "dashboard")
            dashboard.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            dashboard.layout(0, 0, 1920, 720)
            val texture = android.graphics.SurfaceTexture(0)
            val listener = requireNotNull(dashboard.mapSurface.surfaceTextureListener)
            listener.onSurfaceTextureAvailable(texture, dashboard.mapSurface.width, dashboard.mapSurface.height)
            assertSame(dashboard.decoderSurface, presentation.outputSurface)
            assertSame(presentation.outputSurface, surfaces.last())
            assertTrue(dashboard.mapSurface.width < 1920)
            presentation.setStreamActive(true)
            presentation.setMapVisible(false)
            assertFalse(presentation.mapVisible)
            presentation.dismiss()
            listener.onSurfaceTextureDestroyed(texture)
            texture.release()
            assertNull(presentation.outputSurface)
            assertNull(surfaces.last())
        } finally {
            presentation.dismiss()
            ShadowDisplayManager.removeDisplay(displayId)
            ShadowBuild.setBrand(old)
        }
    }

    private fun liveState() = ClusterDashboardState(
        guidance = CarPlayGlance.Snapshot(connected = true, maneuverType = 2, road = "测试道路", distanceMeters = 300),
        music = ClusterDashboardMusic(CarPlayNowPlaying(title = "测试歌曲", artist = "测试歌手", playing = true)),
    )
}
