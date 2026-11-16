package com.shilapi.xcertplay.newui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.newui.connection.*
import java.io.File
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NewUiCarlinkPairingLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun resetPalette() { NewUiColors.night = true }

    @Test @Config(qualifiers = "zh-rCN-w1920dp-h720dp-land-mdpi")
    fun dayFullPage() { NewUiColors.night = false; verify("day-full") }

    @Test @Config(qualifiers = "zh-rCN-w960dp-h720dp-land-xhdpi")
    fun dayCompactPage() { NewUiColors.night = false; verify("day-compact") }

    @Test @Config(qualifiers = "zh-rCN-w1920dp-h720dp-land-mdpi")
    fun fullPageKeepsSixDigitsInProgressPanel() = verify("full")

    @Test @Config(qualifiers = "zh-rCN-w960dp-h720dp-land-xhdpi")
    fun compactPageKeepsDigitsReadable() = verify("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1920dp-h720dp-land-mdpi")
    fun arabicPageKeepsNumericOrder() = verify("arabic")

    private fun verify(name: String) {
        compose.activity.actionBar?.hide()
        val state = NewUiConnectionSnapshot.placeholder.copy(
            phoneName = "Xiaomi 15", statusTitle = "Carlink",
            carlink = true, linkSummary = "123456", progressStepCount = 6,
            progressLitCount = 2, progressStepTitle = "等待手机连接",
            progressStepSubtitle = "请在手机上确认连接", connectionMethodLabel = "Wi-Fi",
            hasSelectedPhone = true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    Box(Modifier.fillMaxSize().background(NewUiColors.Canvas).padding(NewUiLayout.ScreenPaddingH)) {
                        NewUiConnectionScreen(state, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        val code = compose.onNodeWithTag("carlink-pairing-code").assertIsDisplayed()
        val digitBounds = "123456".map { digit ->
            compose.onNodeWithText(digit.toString()).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        }
        digitBounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
        val bounds = code.fetchSemanticsNode().boundsInRoot
        digitBounds.forEach { assertTrue(bounds.contains(it.center)) }
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val file = File("build/reports/carlink-pairing/$name-font-140.png")
                requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { bitmap.recycle() }
        }
    }
}
