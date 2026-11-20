package com.shilapi.xcertplay.newui.display

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.geometry.Offset
import android.view.View
import android.view.ViewGroup
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
        compose.onAllNodesWithText(context.getString(R.string.settings_original_edit))[0].performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_components)).assertIsDisplayed()
        screenshot("$name-map-properties")
        compose.onNodeWithText(context.getString(R.string.settings_original_flip_horizontal)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_flip_vertical)).performScrollTo().performClick()
        compose.runOnIdle {
            val scene = findScene(context.window.decorView)!!
            assertTrue(scene.sceneLayout[OriginalClusterComponent.MAP].flipHorizontal)
            assertTrue(scene.sceneLayout[OriginalClusterComponent.MAP].flipVertical)
        }
        screenshot("$name-flip-properties")
        compose.runOnIdle { assertEquals(original, OriginalClusterSettings.load(context)) }
        compose.onNodeWithText(context.getString(R.string.settings_original_components)).performScrollTo()
        compose.onNodeWithText(context.getString(R.string.settings_original_components)).performClick()
        compose.onNodeWithTag("original-edit-music").performScrollTo().performClick()
        fun adjust(parameter: Int, value: Float) {
            compose.onNodeWithTag("original-parameter-$parameter").performScrollTo().assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.SetProgress) { it(value) }
        }
        adjust(R.string.settings_original_music_title_size, 30f)
        compose.onNodeWithText(context.getString(R.string.settings_original_music_show_time)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_music_show_playback)).performScrollTo().performClick()
        adjust(R.string.settings_original_music_artist_size, 24f)
        adjust(R.string.settings_original_music_lyric_size, 38f)
        adjust(R.string.settings_original_music_progress_length, 1f)
        adjust(R.string.settings_original_music_cover_size, 288f)
        val hue = compose.onNodeWithTag("original-color-hue-${R.string.settings_original_music_lyric_color}")
        hue.performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(210f) }
        val field = compose.onNodeWithTag("original-color-field-${R.string.settings_original_music_lyric_color}")
        field.performScrollTo().performTouchInput {
            down(Offset(width * .2f, height * .7f))
            moveTo(Offset(width * .75f, height * .25f))
            up()
        }
        compose.runOnIdle {
            val preview = findScene(context.window.decorView)!!
            val expected = android.graphics.Color.HSVToColor(floatArrayOf(210f, .75f, .75f))
            assertEquals(expected, preview.sceneLayout.musicStyle.lyricColor)
            assertEquals(38f, preview.sceneLayout.musicStyle.lyricSize, .01f)
            assertEquals(30f, preview.sceneLayout.musicStyle.titleSize, .01f)
            assertEquals(288f, preview.sceneLayout.musicStyle.coverSize, .01f)
            assertFalse(preview.sceneLayout.musicStyle.showTime)
            assertFalse(preview.sceneLayout.musicStyle.showPlaybackControls)
        }
        screenshot("$name-music-style")
        compose.runOnIdle { assertEquals(original, OriginalClusterSettings.load(context)) }
        compose.onNodeWithText(context.getString(R.string.settings_original_components)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_tires)).performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(findScene(context.window.decorView)!!.sceneLayout[OriginalClusterComponent.TIRES].visible)
        }
        compose.runOnIdle { assertEquals(original, OriginalClusterSettings.load(context)); guard!!.invoke { backs++ } }
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_unsaved)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_keep_editing)).performClick()
        compose.runOnIdle { assertEquals(0, backs) }
        compose.onNodeWithTag("original-cluster-reset").performScrollTo().performClick()
        compose.onNodeWithTag("original-edit-music").performScrollTo().performClick()
        compose.onNodeWithTag("original-color-hue-${R.string.settings_original_music_lyric_color}")
            .performScrollTo().performSemanticsAction(SemanticsActions.SetProgress) { it(210f) }
        compose.onNodeWithTag("original-color-field-${R.string.settings_original_music_lyric_color}")
            .performScrollTo().performTouchInput { click(Offset(width * .75f, height * .25f)) }
        compose.onNodeWithText(context.getString(R.string.settings_original_components)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_tires)).performScrollTo().performClick()
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_save)).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertFalse(OriginalClusterSettings.load(context)[OriginalClusterComponent.TIRES].visible)
            assertTrue(OriginalClusterSettings.load(context)[OriginalClusterComponent.DRIVING].visible)
            assertEquals(OriginalMusicStyle(lyricColor = android.graphics.Color.HSVToColor(floatArrayOf(210f, .75f, .75f))),
                OriginalClusterSettings.load(context).musicStyle)
        }
    }

    private fun findScene(view: View): OriginalClusterScene? =
        if (view is OriginalClusterScene) view else if (view is ViewGroup)
            (0 until view.childCount).firstNotNullOfOrNull { findScene(view.getChildAt(it)) } else null

    @Test fun hardwareBackAsksBeforeDiscardingAndCancelKeepsTheDraft() {
        val context = compose.activity
        OriginalClusterSettings.save(context, OriginalClusterLayout())
        var backs = 0
        compose.setContent { NewUiTheme { NewUiOriginalClusterConfigScreen(onBack = { backs++ }) } }
        compose.onNodeWithText(context.getString(R.string.settings_original_cluster_driving)).performScrollTo().performClick()
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
