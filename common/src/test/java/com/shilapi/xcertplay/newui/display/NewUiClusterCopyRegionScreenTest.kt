package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.shilapi.xcertplay.ClusterCopyRegion
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.newui.NewUiTheme
import com.shilapi.xcertplay.newui.NewUiColors
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NewUiClusterCopyRegionScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun resetTheme() { NewUiColors.night = true }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullScreenSelectionTracksAnEntireDrag() = verify("full")

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun compactSelectionTracksAnEntireDrag() = verify("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicSelectionUsesTheSameScreenCoordinates() = verify("arabic")

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun dayEditorUsesReadableLightSurfaces() = verify("full-day", night = false)

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun compactDayEditorUsesReadableLightSurfaces() = verify("compact-day", night = false)

    private fun verify(name: String, night: Boolean = true) {
        NewUiColors.night = night
        compose.activity.actionBar?.hide()
        com.shilapi.xcertplay.DiPlayWindowFullscreen.apply(compose.activity)
        ClusterCopyRegion.save(compose.activity, ClusterCopyRegion(), ClusterStyle.SPORT)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 1.4f)) {
                NewUiTheme { NewUiClusterCopyRegionScreen(ClusterStyle.SPORT, {}) }
            }
        }
        val bounds = compose.onNodeWithTag("copy-region-editor").fetchSemanticsNode().boundsInRoot
        val root = compose.onRoot().fetchSemanticsNode().boundsInRoot
        assertEquals(root, bounds)
        val toolbar = compose.onNodeWithTag("copy-region-toolbar").fetchSemanticsNode().boundsInRoot
        assertEquals(bounds.center.x, toolbar.center.x, 1f)
        assertEquals(bounds.center.y, toolbar.center.y, 1f)
        compose.onNodeWithTag("copy-region-canvas").performTouchInput {
            val start = center + Offset(0f, height * .2f)
            swipe(start, start + Offset(width * .1f, 0f), 500)
        }
        compose.runOnIdle {
            val saved = ClusterCopyRegion.load(compose.activity, ClusterStyle.SPORT)
            assertTrue("Drag must survive recomposition", saved.left > .24f)
            assertEquals(.64f, saved.right - saved.left, .001f)
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val file = File("build/reports/cluster-copy-region/$name-font-140.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
