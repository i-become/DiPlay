package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class ClusterLyricsTest {
    @Test fun currentLineAndLrcUsePlaybackPosition() {
        assertEquals("当前歌词", ClusterLyrics.current("当前歌词", 0))
        val lrc = "[ar:artist]\n[offset:100]\n[00:01.20][00:03.000]第一句\n[00:02.50]第二句"
        assertNull(ClusterLyrics.current(lrc, 1000))
        assertEquals("第一句", ClusterLyrics.current(lrc, 1100))
        assertEquals("第二句", ClusterLyrics.current(lrc, 2400))
        assertEquals(500L, ClusterLyrics.line(lrc, 2400).remainingMillis)
        assertNull(ClusterLyrics.line("单句", 0).remainingMillis)
        assertEquals("第一句", ClusterLyrics.current(lrc, 3000))
        assertNull(ClusterLyrics.current("[ar:artist]", 0))
    }
}
