package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.shilapi.xcertplay.ClusterMapCropSettings
import com.shilapi.xcertplay.newui.*
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NewUiNavigationCropTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullSizeCalibrationIsSavedLive() = verifyPage("full")

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun compactHighDensityKeepsCalibrationReachable() = verifyPage("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicCalibrationKeepsActionsReachable() = verifyPage("arabic")

    private fun verifyPage(name: String) {
        var value by mutableIntStateOf(0)
        var saves = 0
        var sourceSaves = 0
        var zoom by mutableIntStateOf(100)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    NewUiShell(NewUiDestination.DISPLAY, true, NewUiTopBarStatus.placeholder, {}, {}) {
                        NewUiDisplayNavMarkerScreen(50, 45, {}, { _, _ -> sourceSaves++ }, {},
                            navigationZoom = zoom, onNavigationZoom = {
                                ClusterMapCropSettings.saveZoom(compose.activity, it)
                                zoom = it
                            }, navigationCrop = value, onNavigationCrop = {
                                ClusterMapCropSettings.save(compose.activity, it)
                                value = it
                                saves++
                            })
                    }
                }
            }
        }
        compose.onNodeWithTag("navigation-crop-slider").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(50f) }
        compose.runOnIdle {
            assertEquals(50, value)
            assertEquals(50, ClusterMapCropSettings.load(compose.activity))
            assertEquals(0, sourceSaves)
        }
        screenshot(name + "-slider")
        compose.onNodeWithTag("navigation-crop-reset").performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(0, value)
            assertEquals(0, ClusterMapCropSettings.load(compose.activity))
            assertEquals(2, saves)
            assertEquals(0, sourceSaves)
        }
        screenshot(name + "-reset")
        compose.onNodeWithTag("navigation-zoom-slider").performScrollTo().assertIsDisplayed()
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(150f) }
        compose.runOnIdle {
            assertEquals(150, zoom)
            assertEquals(150, ClusterMapCropSettings.loadZoom(compose.activity))
        }
        screenshot(name + "-zoom")
        compose.onNodeWithTag("navigation-zoom-reset").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(100, zoom) }
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/reports/navigation-crop/" + name + "-font-140.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}

