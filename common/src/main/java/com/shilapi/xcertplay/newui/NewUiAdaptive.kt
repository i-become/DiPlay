package com.shilapi.xcertplay.newui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/** Layout tuning for the content area below the top bar (reference: 1280×640 dp content). */
data class NewUiWindowMetrics(
    val compactWidth: Boolean,
    val compactHeight: Boolean,
    val sidebarWidth: Dp,
    val heroHeight: Dp,
    val actionRowHeight: Dp,
    val locationRowHeight: Dp,
    val sidebarItemHeight: Dp,
) {
    val compact: Boolean get() = compactWidth || compactHeight

    companion object {
        val Default = NewUiWindowMetrics(
            compactWidth = false,
            compactHeight = false,
            sidebarWidth = NewUiLayout.SidebarWidth,
            heroHeight = NewUiLayout.HeroHeight,
            actionRowHeight = NewUiLayout.ActionRowHeight,
            locationRowHeight = NewUiLayout.LocationRowHeight,
            sidebarItemHeight = NewUiLayout.SidebarItemHeight,
        )

        fun from(contentWidth: Dp, contentHeight: Dp): NewUiWindowMetrics {
            val compactWidth = contentWidth < 960.dp
            val compactHeight = contentHeight < 560.dp
            val scale = min(contentWidth / 1280.dp, contentHeight / 640.dp).coerceIn(0.88f, 1.08f)
            fun scaled(base: Dp, minDp: Dp) = (base.value * scale).dp.coerceAtLeast(minDp)
            val sidebarFraction = (contentWidth.value * 0.19f).dp.coerceIn(220.dp, 280.dp)
            return NewUiWindowMetrics(
                compactWidth = compactWidth,
                compactHeight = compactHeight,
                sidebarWidth = if (compactWidth) {
                    196.dp
                } else {
                    scaled(NewUiLayout.SidebarWidth, 220.dp).coerceAtLeast(sidebarFraction)
                },
                heroHeight = scaled(NewUiLayout.HeroHeight, 168.dp),
                actionRowHeight = scaled(NewUiLayout.ActionRowHeight, 112.dp),
                locationRowHeight = scaled(NewUiLayout.LocationRowHeight, 92.dp),
                sidebarItemHeight = if (compactHeight) 74.dp else scaled(NewUiLayout.SidebarItemHeight, 74.dp),
            )
        }
    }
}

val LocalNewUiWindowMetrics = staticCompositionLocalOf { NewUiWindowMetrics.Default }

@Composable
fun newUiWindowMetrics(): NewUiWindowMetrics = LocalNewUiWindowMetrics.current
