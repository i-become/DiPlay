package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class ClusterLyricScrollTest {
    @Test fun overflowingLineRevealsOnceThenStopsAndResetsOnlyForNewContent() {
        var now = 0L
        val scroll = ClusterLyricScroll { now }
        assertEquals(0f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertTrue(scroll.animating)
        now = 200
        assertEquals(0f, scroll.offset("song-line1", 500f, 300f), 0f)
        now = 1083
        assertEquals(100f, scroll.offset("song-line1", 500f, 300f), .2f)
        now = 10000
        assertEquals(200f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertFalse(scroll.animating)
        now = 90000
        assertEquals(200f, scroll.offset("song-line1", 500f, 300f), 0f)
        assertEquals(0f, scroll.offset("song-line2", 500f, 300f), 0f)
        assertTrue(scroll.animating)
        assertEquals(0f, scroll.offset("short", 250f, 300f), 0f)
        assertFalse(scroll.animating)
    }
    @Test fun timedLineFinishesBeforeNextLineAndFasterLinesMoveFaster() {
        var now = 0L
        val scroll = ClusterLyricScroll { now }
        scroll.offset("fast", 500f, 300f, 2000L)
        now = 1000L
        val fast = scroll.offset("fast", 500f, 300f, 1000L)
        assertTrue(fast > 90f)
        now = 1750L
        assertEquals(200f, scroll.offset("fast", 500f, 300f, 250L), .01f)
        now = 2000L
        scroll.offset("slow", 500f, 300f, 6000L)
        now = 3000L
        assertTrue(scroll.offset("slow", 500f, 300f, 5000L) < fast)
    }
}
