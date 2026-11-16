package com.shilapi.xcertplay.newui.display

import com.shilapi.xcertplay.ClusterCopyRegion
import org.junit.Assert.*
import org.junit.Test

class ClusterCopyRegionGestureTest {
    @Test fun draggingInsideMovesTheWholeSelectionAndClampsAtScreenEdges() {
        val region = ClusterCopyRegion(.2f, .2f, .8f, .8f)
        assertEquals(-1, ClusterCopyRegionGesture.hit(region, 500f, 300f, 1000f, 600f, 48f))
        val moved = ClusterCopyRegionGesture.drag(region, -1, .9f, -.9f)
        assertEquals(1f, moved.right, .001f)
        assertEquals(0f, moved.top, .001f)
        assertEquals(.6f, moved.right - moved.left, .001f)
        assertEquals(.6f, moved.bottom - moved.top, .001f)
    }

    @Test fun allFourCornersRemainResizableAcrossRepeatedUpdates() {
        for ((corner, point) in listOf(200f to 120f, 800f to 120f, 200f to 480f, 800f to 480f).withIndex()) {
            var region = ClusterCopyRegion(.2f, .2f, .8f, .8f)
            assertEquals(corner, ClusterCopyRegionGesture.hit(region, point.first, point.second, 1000f, 600f, 48f))
            repeat(10) { region = ClusterCopyRegionGesture.drag(region, corner, .01f, .01f) }
            assertEquals(if (corner % 2 == 0) .3f else .2f, region.left, .001f)
            assertEquals(if (corner % 2 == 1) .9f else .8f, region.right, .001f)
        }
    }
}
