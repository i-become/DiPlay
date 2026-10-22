package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class StartupTimelineTest {
    @Test fun exportMergesDiskMiddleAndPendingMemoryTailWithoutDuplicatingEvents() {
        val timeline = StartupTimeline(headLimit = 2, tailLimit = 2)
        val disk = (1..7).map { timeline.add("elapsedMs=$it", "attempt=$it") }.take(6).joinToString("\n")
        val report = timeline.report("$disk\nseq=7 partial")
        assertTrue(report.contains("events=7 retained=7 omitted=0 source=disk+memory"))
        assertEquals((1..7).toList(), Regex("seq=(\\d+)").findAll(report).map { it.groupValues[1].toInt() }.toList())
        assertTrue(report.contains("attempt=7"))
        assertFalse(report.contains("partial"))
    }

    @Test fun overflowKeepsTheOriginalBootAndTheLatestRetryWithAnExplicitGap() {
        val timeline = StartupTimeline(headLimit = 2, tailLimit = 2)
        for (index in 1..7) timeline.add("elapsedMs=$index", "attempt=$index")
        val report = timeline.report()
        assertTrue(report.contains("events=7 retained=4 omitted=3"))
        for (index in listOf(1, 2, 6, 7)) assertTrue(report.contains("attempt=$index\n"))
        for (index in 3..5) assertFalse(report.contains("attempt=$index\n"))
        assertTrue(report.contains("omitted 3 middle events"))
    }

    @Test fun independentCallbacksHaveUniqueOrderedSequenceNumbersAndClearRemovesEvidence() {
        val timeline = StartupTimeline(headLimit = 50, tailLimit = 100)
        val executor = Executors.newFixedThreadPool(4)
        val done = CountDownLatch(100)
        try {
            repeat(100) { executor.execute { timeline.add("elapsedMs=1", "callback"); done.countDown() } }
            assertTrue(done.await(3, TimeUnit.SECONDS))
            val sequences = Regex("seq=(\\d+)").findAll(timeline.report()).map { it.groupValues[1].toInt() }.toList()
            assertEquals((1..100).toList(), sequences)
            timeline.clear()
            assertFalse(timeline.report().contains("callback"))
            assertTrue(timeline.add("elapsedMs=2", "new capture").startsWith("seq=1 "))
        } finally { executor.shutdownNow() }
    }
}
