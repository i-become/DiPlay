package com.shilapi.xcertplay

import android.app.Activity
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDisplayManager
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Hs5ClusterForegroundTest {
    @Test fun clusterOverlayUsesTheSecondaryDisplayAndReleasesItsViewOnClose() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val id = ShadowDisplayManager.addDisplay("w1920dp-h720dp")
        val display = activity.getSystemService(DisplayManager::class.java).getDisplay(id)
        ShadowSettings.setCanDrawOverlays(true)
        val view = View(activity)
        val output = Hs5ClusterForeground()
        val keys = mutableListOf<Int>()
        try {
            assertTrue(output.attach(activity, display, view) { keys += it.keyCode; true })
            val root = view.parent as View
            val params = root.layoutParams as WindowManager.LayoutParams
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, params.type)
            assertEquals(PixelFormat.TRANSLUCENT, params.format)
            assertTrue(output.mode.contains("display=$id"))
            root.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK))
            assertEquals(listOf(KeyEvent.KEYCODE_BACK), keys)
            output.setVisible(false)
            assertEquals(0f, (root.layoutParams as WindowManager.LayoutParams).alpha)
            output.setVisible(true)
            assertEquals(1f, (root.layoutParams as WindowManager.LayoutParams).alpha)
            output.close()
            output.close()
            assertNull(view.parent)
        } finally {
            output.close()
            ShadowSettings.setCanDrawOverlays(false)
            ShadowDisplayManager.removeDisplay(id)
        }
    }

    @Test fun noOverlayPermissionPreservesTheActivityPathWithoutAttachingAnything() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val id = ShadowDisplayManager.addDisplay("w1920dp-h720dp")
        try {
            ShadowSettings.setCanDrawOverlays(false)
            val view = View(activity)
            assertFalse(Hs5ClusterForeground().attach(activity,
                activity.getSystemService(DisplayManager::class.java).getDisplay(id), view) { true })
            assertNull(view.parent)
        } finally { ShadowDisplayManager.removeDisplay(id) }
    }
}
