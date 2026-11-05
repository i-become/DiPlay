package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.shilapi.xcertplay.*
import com.shilapi.xcertplay.host.R
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
class NewUiOriginalClusterConfigTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullEditorPreviewsADraftThenSaves() = verifyPage("full")

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun compactEditorKeepsAllControlsReachableAtLargeFont() = verifyPage("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicEditorKeepsPreviewAndControlsReachable() = verifyPage("arabic")

    private fun verifyPage(name: String) {
        val context = compose.activity
        val original = OriginalClusterLayout()
        OriginalClusterSettings.save(context, original)
        var backs = 0
        var guard: ((() -> Unit) -> Unit)? = null
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    NewUiShell(NewUiDestination.DISPLAY, true, NewUiTopBarStatus.placeholder, {}, {}) {
                        NewUiOriginalClusterConfigScreen(onBack = { backs++ }, registerExitGuard = { guard = it })
                    }
                }
            }
        }
        compose.onNodeWithTag("original-cluster-preview").assertIsDisplayed()
        screenshot(name)
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_tires)).performClick()
        compose.runOnIdle { assertEquals(original, OriginalClusterSettings.load(context)); guard!!.invoke { backs++ } }
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_unsaved)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_keep_editing)).performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.onNodeWithTag("original-cluster-reset").performScrollTo().performClick()
        // Reset restores all four widgets and the default positions without committing anything.
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_tires)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_save)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertFalse(OriginalClusterSettings.load(context)[OriginalClusterComponent.TIRES].visible)
            assertTrue(OriginalClusterSettings.load(context)[OriginalClusterComponent.DRIVING].visible)
        }
    }

    @Test fun hardwareBackAsksBeforeDiscardingAndCancelKeepsTheDraft() {
        val context = compose.activity
        OriginalClusterSettings.save(context, OriginalClusterLayout())
        var backs = 0
        compose.setContent { NewUiTheme { NewUiOriginalClusterConfigScreen(onBack = { backs++ }) } }
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_driving)).performClick()
        compose.runOnIdle { context.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_unsaved)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_discard)).performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertEquals(OriginalClusterLayout(), OriginalClusterSettings.load(context))
        }
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/reports/original-cluster-config/$name-font-140.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}
