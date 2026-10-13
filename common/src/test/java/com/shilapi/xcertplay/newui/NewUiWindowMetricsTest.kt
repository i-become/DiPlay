package com.shilapi.xcertplay.newui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewUiWindowMetricsTest {
    @Test fun referenceSizeKeepsDesignDimensions() {
        val metrics = NewUiWindowMetrics.from(1280.dp, 640.dp)
        assertEquals(NewUiLayout.SidebarWidth, metrics.sidebarWidth)
        assertEquals(NewUiLayout.HeroHeight, metrics.heroHeight)
        assertEquals(false, metrics.compact)
    }

    @Test fun shortViewportScalesDownAndMarksCompact() {
        val metrics = NewUiWindowMetrics.from(800.dp, 480.dp)
        assertTrue(metrics.compactHeight)
        assertTrue(metrics.heroHeight < NewUiLayout.HeroHeight)
        assertTrue(metrics.sidebarWidth <= NewUiLayout.SidebarWidth)
    }
}
