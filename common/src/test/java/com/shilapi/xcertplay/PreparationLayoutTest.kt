package com.shilapi.xcertplay

import android.view.View
import android.widget.LinearLayout
import android.widget.FrameLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class PreparationLayoutTest {
    @Test fun buildContentViewDoesNotIncludeLegacyWaitingPanel() {
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        val method = CarPlayHostActivity::class.java.getDeclaredMethod("buildContentView")
        method.isAccessible = true
        val root = method.invoke(activity) as android.widget.FrameLayout
        val legacyWaiting = (0 until root.childCount)
            .map { root.getChildAt(it) }
            .filterIsInstance<FrameLayout>()
            .any { frame ->
                frame.getChildAt(0) is LinearLayout &&
                    (frame.getChildAt(0) as LinearLayout).orientation == LinearLayout.VERTICAL
            }
        assertFalse(legacyWaiting)
    }
}
