package com.shilapi.xcertplay

import android.graphics.Color

/** The screen before CarPlay's video follows CarPlay's day/night mode, so it does not glare at night. */
internal data class WaitingScreenColors(val background: Int, val text: Int, val secondary: Int) {
    companion object {
        fun of(night: Boolean): WaitingScreenColors = DiPlayPalette.of(night).let {
            WaitingScreenColors(it.background, it.primaryText, it.secondaryText)
        }

        /** Text on [com.shilapi.xcertplay.newui.NewUiShellBackgroundViews], same legibility as NewUi. */
        fun forNewUiShell(): WaitingScreenColors = WaitingScreenColors(
            background = Color.TRANSPARENT,
            text = 0xFFF2F7FF.toInt(),
            secondary = 0xFF8FAED5.toInt(),
        )
    }
}
