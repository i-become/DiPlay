package com.shilapi.xcertplay

import android.app.Activity
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Hs5ClusterActivityOutputTest {
    private val closed = mutableListOf<String>()
    private val host = object : Hs5ClusterActivityOutput.Host {
        override fun onClusterWindowSurface(surface: Surface?) = Unit
        override fun onClusterWindowClosed(reason: String) { closed += reason }
    }

    @After fun tearDown() = Hs5ClusterActivityOutput.close("test")

    private fun openWindow(): Pair<Activity, Intent> {
        val launcher = Robolectric.buildActivity(Activity::class.java).setup().get()
        val display = launcher.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        assertTrue(Hs5ClusterActivityOutput.open(launcher, display, host))
        return launcher to shadowOf(launcher).nextStartedActivity
    }

    @Test fun backKeyClosesTheWindowAndTurnsFlyScreenOff() {
        AirPlayPersistence.saveClusterStyle(org.robolectric.RuntimeEnvironment.getApplication(), ClusterStyle.BASIC)
        val (_, launch) = openWindow()
        assertTrue(Hs5ClusterActivityOutput.isOpen())
        assertTrue(Hs5ClusterActivityOutput.ownsFocus())
        val window = Robolectric.buildActivity(Hs5ClusterActivity::class.java, launch).setup().get()

        val content = window.findViewById<android.view.ViewGroup>(android.R.id.content)
        assertTrue(content.getChildAt(0) is ClusterDashboardView)
        assertEquals(PixelFormat.TRANSLUCENT, window.window.attributes.format)

        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK))
        assertTrue(closed.isEmpty())
        window.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))

        assertEquals(listOf("key ${KeyEvent.KEYCODE_BACK}"), closed)
        assertTrue(window.isFinishing)
        assertFalse(Hs5ClusterActivityOutput.isOpen())
        assertFalse(Hs5ClusterActivityOutput.ownsFocus())
    }

    @Test fun closingFromDiPlayDoesNotReportADriverClose() {
        val (_, launch) = openWindow()
        val window = Robolectric.buildActivity(Hs5ClusterActivity::class.java, launch).setup().get()

        Hs5ClusterActivityOutput.close("gesture")

        assertTrue(window.isFinishing)
        assertTrue(closed.isEmpty())
    }

    @Test fun staleWindowFinishesWithoutTouchingTheCurrentOne() {
        val (_, launch) = openWindow()
        Hs5ClusterActivityOutput.close("reopen")
        val stale = Robolectric.buildActivity(Hs5ClusterActivity::class.java, launch).create().get()

        assertTrue(stale.isFinishing)
        assertTrue(closed.isEmpty())
    }
}
