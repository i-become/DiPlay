package com.shilapi.xcertplay

import android.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.shilapi.xcertplay.newui.NewUiColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class DiPlayPaletteTest {
    @Test fun darkPaletteUsesHs5NightConnectionColors() {
        val palette = DiPlayPalette.DARK
        assertEquals(Color.rgb(5, 3, 7), palette.background)
        assertEquals(Color.rgb(41, 38, 53), palette.surface)
        assertEquals(Color.rgb(52, 49, 65), palette.button)
        assertEquals(Color.rgb(70, 66, 82), palette.outline)
        assertEquals(Color.rgb(64, 59, 83), palette.railSelected)
        assertEquals(Color.rgb(109, 105, 134), palette.railSelectedOutline)
        assertEquals(Color.rgb(226, 228, 230), palette.primaryText)
        assertEquals(Color.rgb(175, 177, 189), palette.secondaryText)
        assertEquals(Color.rgb(196, 193, 216), palette.accent)
        assertEquals(Color.rgb(255, 196, 128), palette.warning)
        assertEquals(Color.rgb(196, 204, 208), palette.success)
        assertEquals(Color.rgb(190, 45, 45), palette.danger)
        assertEquals(0x336D6986, palette.ripple)
        assertFalse(palette.systemBarIconsAreDark)
    }

    @Test fun waitingScreenDelegationKeepsItsExactLightAndDarkColors() {
        assertEquals(
            WaitingScreenColors(Color.rgb(5, 3, 7), Color.rgb(226, 228, 230), Color.rgb(175, 177, 189)),
            WaitingScreenColors.of(night = true),
        )
        assertEquals(
            WaitingScreenColors(Color.rgb(233, 238, 246), Color.rgb(28, 28, 30), Color.rgb(90, 100, 116)),
            WaitingScreenColors.of(night = false),
        )
    }

    @Test fun semanticRolesMeetTheirWcagContrastTargets() {
        for (palette in listOf(DiPlayPalette.DARK, DiPlayPalette.LIGHT)) {
            assertContrastAtLeast(palette.primaryText, palette.background, 4.5)
            assertContrastAtLeast(palette.secondaryText, palette.background, 4.5)
            assertContrastAtLeast(palette.accent, palette.background, 4.5)
            assertContrastAtLeast(palette.onAccent, palette.accent, 4.5)
            assertContrastAtLeast(palette.warning, palette.background, 4.5)
            assertContrastAtLeast(palette.success, palette.background, 4.5)
            // The legacy dark danger role is used for graphical/status emphasis, not body text.
            assertContrastAtLeast(palette.danger, palette.background, 3.0)
            assertContrastAtLeast(palette.overlayPrimaryText, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlaySecondaryText, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlayAccent, palette.overlayBackground, 4.5)
            assertContrastAtLeast(palette.overlayOnAccent, palette.overlayAccent, 4.5)
            assertContrastAtLeast(palette.overlayDanger, palette.overlayBackground, 3.0)
        }
        assertContrastAtLeast(DiPlayPalette.LIGHT.accentTrack, DiPlayPalette.LIGHT.trackOff, 3.0)
        assertContrastAtLeast(
            DiPlayPalette.LIGHT.overlayAccentTrack,
            DiPlayPalette.LIGHT.overlayTrackOff,
            3.0,
        )
        assertTrue(DiPlayPalette.LIGHT.systemBarIconsAreDark)
    }

    @Test fun newUiButtonAndCardForegroundsRemainReadable() {
        val previous = NewUiColors.night
        try {
            for (night in listOf(true, false)) {
                NewUiColors.night = night
                assertEquals(NewUiColors.DropdownBorder, com.shilapi.xcertplay.newui.NewUiSurfaces.glassBorder)
                assertEquals(NewUiColors.Divider, com.shilapi.xcertplay.newui.NewUiSurfaces.innerDivider)
                assertContrastAtLeast(NewUiColors.OnAccent.toArgb(), NewUiColors.Accent.toArgb(), 4.5)
                for (background in listOf(NewUiColors.Canvas, NewUiColors.CanvasBottom,
                    NewUiColors.PrimaryButtonFill, NewUiColors.MenuSelected, NewUiColors.ButtonNormal,
                    NewUiColors.DropdownBackground, NewUiColors.CardNormal, NewUiColors.CardSecondary)) {
                    assertContrastAtLeast(NewUiColors.TextPrimary.toArgb(), background.toArgb(), 4.5)
                    assertContrastAtLeast(NewUiColors.TextSecondary.toArgb(), background.toArgb(), 4.5)
                    assertContrastAtLeast(NewUiColors.DangerText.toArgb(), background.toArgb(), 4.5)
                }
            }
        } finally { NewUiColors.night = previous }
    }

    private fun assertContrastAtLeast(foreground: Int, background: Int, minimum: Double) {
        val foregroundLuminance = luminance(foreground)
        val backgroundLuminance = luminance(background)
        val ratio = (max(foregroundLuminance, backgroundLuminance) + 0.05) /
            (min(foregroundLuminance, backgroundLuminance) + 0.05)
        assertTrue("Expected contrast >= $minimum, was $ratio", ratio >= minimum)
    }

    private fun luminance(color: Int): Double {
        fun channel(value: Int): Double {
            val normalized = value / 255.0
            return if (normalized <= 0.04045) normalized / 12.92
            else ((normalized + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(Color.red(color)) +
            0.7152 * channel(Color.green(color)) +
            0.0722 * channel(Color.blue(color))
    }
}
