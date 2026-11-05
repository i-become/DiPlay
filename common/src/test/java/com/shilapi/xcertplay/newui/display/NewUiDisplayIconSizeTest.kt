package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.shilapi.xcertplay.*
import com.shilapi.xcertplay.airplay.AirPlayPhysicalSizeBasis
import com.shilapi.xcertplay.airplay.CarPlaySize
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.newui.*
import com.shilapi.xcertplay.orchestration.CarPlayController
import java.io.File
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class NewUiDisplayIconSizeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @After fun clearSession() { CarPlayBackgroundSession.clear() }

    @Test @Config(qualifiers = "zh-rCN-w1280dp-h720dp-land-mdpi")
    fun fullSizePageSavesWithoutInterruptingCarPlay() = verifyPage("full")

    @Test @Config(qualifiers = "zh-rCN-w640dp-h480dp-land-xhdpi")
    fun compactHighDensityPageKeepsChoicesAndReconnectReachable() = verifyPage("compact")

    @Test @Config(qualifiers = "ar-ldrtl-w1280dp-h720dp-land-mdpi")
    fun arabicPageKeepsChoicesAndReconnectReachable() = verifyPage("arabic")

    @Test fun savedPresetReplacesLegacyAxisAndPendingStateFollowsTheActualSession() {
        val context = compose.activity
        AirPlayPersistence.savePhysicalSizeBasis(context, AirPlayPhysicalSizeBasis.HEIGHT)
        NewUiDisplayActions.saveCarPlaySize(context, CarPlaySize.SMALL)
        assertEquals(350, AirPlayPersistence.loadWidthPhysicalMm(context))
        assertEquals(AirPlayPhysicalSizeBasis.WIDTH, AirPlayPersistence.loadPhysicalSizeBasis(context))
        assertEquals(CarPlaySize.SMALL, NewUiDisplayStateReader.read(context).carPlaySize)
        assertFalse(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)

        storeSession(CarPlaySize.MEDIUM) {}
        assertTrue(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)
        NewUiDisplayActions.saveCarPlaySize(context, CarPlaySize.MEDIUM)
        assertFalse(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)
        NewUiDisplayActions.saveCarPlaySize(context, CarPlaySize.LARGE)
        assertTrue(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)
        storeSession(CarPlaySize.LARGE) {}
        assertFalse(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)
        CarPlayBackgroundSession.clear()
        assertFalse(NewUiDisplayStateReader.read(context).iconSizeReconnectNeeded)
    }

    private fun verifyPage(name: String) {
        val context = compose.activity
        NewUiDisplayActions.saveCarPlaySize(context, CarPlaySize.MEDIUM)
        var stops = 0
        storeSession(CarPlaySize.MEDIUM) { stops++ }
        var state by mutableStateOf(NewUiDisplayStateReader.read(context))
        var reconnectRequests = 0
        var choosingSize by mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.4f),
                LocalLayoutDirection provides if (name == "arabic") LayoutDirection.Rtl else LayoutDirection.Ltr,
            ) {
                NewUiTheme {
                    NewUiShell(NewUiDestination.DISPLAY, true, NewUiTopBarStatus.placeholder, {}, {}) {
                        if (!choosingSize) {
                            NewUiDisplayScreen(
                                state = state, navMarkerSummary = "50%, 45%",
                                onPickFps = {}, onPickNavMarker = {}, onOpenClusterManager = {},
                                onSmoothVideoChanged = {}, onDirectVideoOutputChanged = {},
                                onLowLatencyDecoderChanged = {}, onMtkDecoderTuningChanged = {},
                                onHudNavigationChanged = {}, onClusterMapChanged = {},
                                onClusterFlyScreenDefaultOnConnectChanged = {}, onClusterGestureChanged = {},
                                onNightModeSelected = {}, onPickIconSize = { choosingSize = true },
                            )
                        } else NewUiDisplayIconSizeScreen(
                            selectedSize = state.carPlaySize,
                            reconnectNeeded = state.iconSizeReconnectNeeded,
                            onBack = {},
                            onSelectSize = {
                                NewUiDisplayActions.saveCarPlaySize(context, it)
                                state = NewUiDisplayStateReader.read(context)
                            },
                            onReconnect = { reconnectRequests++ },
                        )
                    }
                }
            }
        }
        compose.onNodeWithText(context.getString(R.string.settings_carplay_icon_size))
            .performScrollTo().assertIsDisplayed()
        screenshot(name + "-entry")
        compose.onNodeWithText(context.getString(R.string.settings_carplay_icon_size)).performClick()
        val reconnect = context.getString(R.string.apply_and_reconnect)
        compose.onNodeWithText(reconnect).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.option_size_large))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(CarPlaySize.LARGE, AirPlayPersistence.loadCarPlaySize(context))
            assertEquals(250, AirPlayPersistence.loadWidthPhysicalMm(context))
            assertEquals(0, stops)
            assertEquals(0, reconnectRequests)
            assertTrue(state.iconSizeReconnectNeeded)
        }
        compose.onNodeWithText(reconnect).assertIsDisplayed()
        screenshot(name)
        compose.onNodeWithText(reconnect).performClick()
        compose.runOnIdle { assertEquals(1, reconnectRequests) }
        compose.onNodeWithText(context.getString(R.string.option_size_medium))
            .performScrollTo().performClick()
        compose.onNodeWithText(reconnect).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.option_size_small))
            .performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(350, AirPlayPersistence.loadWidthPhysicalMm(context))
            assertEquals(0, stops)
        }
    }

    private fun storeSession(size: CarPlaySize, onStop: () -> Unit) {
        CarPlayBackgroundSession.store(
            mock(CarPlayController::class.java), mock(AndroidMediaSink::class.java), 1920, 720, this,
            CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720,
                physicalSizeReferenceMm = size.widthMillimeters),
        ) { completion -> onStop(); completion() }
    }

    private fun screenshot(name: String) {
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            try {
                view.draw(Canvas(bitmap))
                val output = File("build/reports/carplay-icon-size/$name-font-140.png")
                requireNotNull(output.parentFile).mkdirs()
                output.outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            } finally { bitmap.recycle() }
        }
    }
}
