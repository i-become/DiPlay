package com.shilapi.xcertplay

import android.graphics.Point
import android.view.Display
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class SimulatedClusterPresentationPickTest {
    @Test fun skipsMainDisplayAndPicksLargestLandscapePresentation() {
        val main = mockDisplay(0, 1080, 1920)
        val small = mockDisplay(2, 640, 480)
        val cluster = mockDisplay(3, 1920, 720)
        val picked = SimulatedClusterPresentationPick.pick(0, arrayOf(main, small, cluster)) { display ->
            when (display.displayId) {
                0 -> Point(1080, 1920)
                2 -> Point(640, 480)
                else -> Point(1920, 720)
            }
        }
        assertEquals(3, picked?.displayId)
    }

    @Test fun returnsNullWhenOnlyMainDisplayExists() {
        val main = mockDisplay(0, 1080, 1920)
        assertNull(
            SimulatedClusterPresentationPick.pick(0, arrayOf(main)) { Point(1080, 1920) },
        )
    }

    private fun mockDisplay(id: Int, width: Int, height: Int): Display {
        val display = Mockito.mock(Display::class.java)
        Mockito.`when`(display.displayId).thenReturn(id)
        Mockito.`when`(display.isValid).thenReturn(true)
        return display
    }
}
