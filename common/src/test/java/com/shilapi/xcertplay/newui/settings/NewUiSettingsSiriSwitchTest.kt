package com.shilapi.xcertplay.newui.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiDestination
import com.shilapi.xcertplay.newui.NewUiShell
import com.shilapi.xcertplay.newui.NewUiTheme
import com.shilapi.xcertplay.newui.NewUiTopBarStatus
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NewUiSettingsSiriSwitchTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullSizeUsesOneSiriSwitch() = checkSwitch("full")

    @Test
    @Config(qualifiers = "zh-rCN-w800dp-h480dp-land-xhdpi")
    fun compactHighDensityUsesOneSiriSwitch() = checkSwitch("compact")

    @Test
    @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicUsesOneSiriSwitch() = checkSwitch("arabic")

    private fun checkSwitch(name: String) {
        val context = RuntimeEnvironment.getApplication()
        val changes = mutableListOf<Boolean>()
        var enabled by mutableStateOf(true)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    NewUiShell(
                        destination = NewUiDestination.SETTINGS,
                        showHs5Branding = true,
                        topBarStatus = NewUiTopBarStatus.placeholder,
                        onDestinationSelected = {},
                        onOpenHeadUnitHome = {},
                    ) {
                        NewUiSettingsScreen(
                            state = NewUiSettingsState.placeholder.copy(
                                showHs5SteeringKeys = true,
                                carPlayWheelSiri = enabled,
                                // The Siri switch is also available with vehicle Bluetooth audio.
                                carBluetoothAudio = true,
                            ),
                            onAutoStartChanged = {},
                            onCarBluetoothAudioChanged = {},
                            onAudioFocusChanged = {},
                            onAdvancedAudioMappingChanged = {},
                            onPickMediaChannel = {},
                            onPickNavigationChannel = {},
                            onVehicleIcon = {},
                            onVehicleName = {},
                            onPermissions = {},
                            onLanguage = {},
                            onCheckUpdate = {},
                            onCarPlayWheelMediaChanged = {},
                            onCarPlayWheelSiriChanged = { enabled = it; changes += it },
                        )
                    }
                }
            }
        }
        val title = context.getString(R.string.settings_carplay_wheel_siri)
        compose.onNodeWithText(context.getString(R.string.settings_voice_assistant_key_title))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_voice_assistant_key_single_label))
            .assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_voice_assistant_key_double_label))
            .assertDoesNotExist()
        compose.onNodeWithText(title).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(false), changes) }
        compose.onNodeWithText(title).performClick()
        compose.runOnIdle { assertEquals(listOf(false, true), changes) }
        val output = File("build/reports/settings-wheel-siri/$name-font-140.png")
        output.parentFile.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally {
                bitmap.recycle()
            }
        }
    }
}
