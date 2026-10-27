package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.shilapi.xcertplay.ClusterStyle
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
class NewUiClusterManagerTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullSizeCanApplyBothStyles() = verifyPage("full")

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun narrowHighDensityCanScrollAndApply() = verifyPage("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicKeepsActionsReachable() = verifyPage("arabic")

    private fun verifyPage(name: String) {
        val selections = mutableListOf<ClusterStyle>()
        var selected by mutableStateOf(ClusterStyle.BASIC)
        var backs = 0
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    NewUiShell(NewUiDestination.DISPLAY, true, NewUiTopBarStatus.placeholder, {}, {}) {
                        NewUiClusterManagerScreen(selected, { backs++ }, { selected = it; selections += it })
                    }
                }
            }
        }
        screenshot(name + "-overview")
        compose.onNodeWithTag("cluster-apply-basic").performScrollTo().assertIsDisplayed().assertIsNotEnabled()
        screenshot(name + "-basic")
        compose.onNodeWithTag("cluster-apply-original").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("cluster-apply-original").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(ClusterStyle.ORIGINAL), selections) }
        screenshot(name + "-original")
        compose.onNodeWithTag("cluster-apply-sport").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithTag("cluster-apply-sport").assertIsNotEnabled()
        compose.onNodeWithTag("cluster-card-sport").performScrollTo()
        screenshot(name + "-sport")
        compose.onNodeWithTag("cluster-apply-basic").performScrollTo().performClick()
        compose.onNodeWithTag("cluster-apply-basic").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf(ClusterStyle.ORIGINAL, ClusterStyle.SPORT, ClusterStyle.BASIC), selections) }
        compose.onNode(hasText(compose.activity.getString(R.string.new_ui_back_to_display))
            and hasAnyAncestor(hasTestTag("cluster-manager"))).performClick()
        compose.runOnIdle { assertEquals(1, backs) }
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/reports/cluster-manager/" + name + "-font-140.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}

