package com.shilapi.xcertplay

import android.content.Context
import android.app.Activity
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.widget.FrameLayout
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import android.hardware.display.DisplayManager
import android.view.SurfaceView
import android.view.ViewGroup
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ClusterStyleTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Test fun newOrUnknownPreferenceUsesOriginalAndSavedStylesArePreserved() {
        assertEquals(ClusterStyle.ORIGINAL, ClusterStyle.fromPreference(null))
        assertEquals(ClusterStyle.ORIGINAL, ClusterStyle.fromPreference("future-style"))
        assertEquals(ClusterStyle.ORIGINAL, AirPlayPersistence.loadClusterStyle(context))
        AirPlayPersistence.saveClusterStyle(context, ClusterStyle.ORIGINAL)
        assertEquals(ClusterStyle.ORIGINAL, AirPlayPersistence.loadClusterStyle(context))
        AirPlayPersistence.saveClusterStyle(context, ClusterStyle.BASIC)
        assertEquals(ClusterStyle.BASIC, AirPlayPersistence.loadClusterStyle(context))
        assertEquals(ClusterStyle.BASIC, ClusterStyle.fromPreference("basic"))
        AirPlayPersistence.saveClusterStyle(context, ClusterStyle.SPORT)
        assertEquals(ClusterStyle.SPORT, AirPlayPersistence.loadClusterStyle(context))
    }

    @Test fun originalActivityUsesFullSurfaceAndKeepsBackToClose() {
        AirPlayPersistence.saveClusterStyle(context, ClusterStyle.ORIGINAL)
        var closed = false
        val owner = object : Hs5ClusterActivityOutput.Host {
            override fun onClusterWindowSurface(surface: Surface?) = Unit
            override fun onClusterWindowClosed(reason: String) { closed = true }
        }
        val launcher = Robolectric.buildActivity(Activity::class.java).setup()
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        try {
            assertTrue(Hs5ClusterActivityOutput.open(launcher.get(), display, owner))
            val launch = shadowOf(launcher.get()).nextStartedActivity
            val controller = Robolectric.buildActivity(Hs5ClusterActivity::class.java, launch).setup().visible()
            try {
                val window = controller.get()
                val content = window.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as FrameLayout
                assertEquals(2, content.childCount)
                assertTrue(content.getChildAt(0) is SurfaceView)
                assertTrue(content.getChildAt(1) is OriginalClusterOverlayView)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, content.getChildAt(1).layoutParams.width)
                assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, content.getChildAt(1).layoutParams.height)
                window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
                assertTrue(window.isFinishing)
                assertTrue(closed)
            } finally { controller.pause().stop().destroy() }
        } finally {
            Hs5ClusterActivityOutput.close("test")
            launcher.pause().stop().destroy()
        }
    }

    @Test fun bothStylesUseMapVideoButOriginalOmitsTheCustomDashboard() {
        ShadowBuild.setBrand("Hongqi")
        val id = ShadowDisplayManager.addDisplay("w1920dp-h720dp-land-mdpi")
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(id)
        try {
            AirPlayPersistence.saveClusterContent(context, CarPlayClusterDisplay.Content.INSTRUMENTS)
            for (style in ClusterStyle.entries) {
                AirPlayPersistence.saveClusterStyle(context, style)
                assertTrue(ClusterDashboardView.supported(context))
                assertEquals(style.customDashboard, ClusterDashboardView.enabled(context))
                assertEquals(CarPlayClusterDisplay.Content.MAP, AirPlayPersistence.effectiveClusterContent(context))
                val presentation = ClusterMapPresentation(context, display, onSurface = {})
                try {
                    presentation.show()
                    val dashboard: ClusterDashboardView? = ReflectionHelpers.getField(presentation, "dashboard")
                    if (style.customDashboard) assertNotNull(dashboard) else {
                        assertNull(dashboard)
                        val video: android.view.View = ReflectionHelpers.getField(presentation, "videoView")
                        assertTrue(video is SurfaceView)
                        val params = video.layoutParams as ViewGroup.LayoutParams
                        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.width)
                        assertEquals(ViewGroup.LayoutParams.MATCH_PARENT, params.height)
                        val copied: android.widget.ImageView = ReflectionHelpers.getField(presentation, "copiedMap")
                        val copyParams = copied.layoutParams as FrameLayout.LayoutParams
                        assertEquals(1920, copyParams.width)
                        assertEquals(720, copyParams.height)
                        assertEquals(0, copyParams.leftMargin)
                        assertEquals(0, copyParams.topMargin)
                        assertEquals(android.widget.ImageView.ScaleType.FIT_XY, copied.scaleType)
                    }
                } finally { presentation.dismiss() }
            }
        } finally { ShadowDisplayManager.removeDisplay(id) }
    }
}

