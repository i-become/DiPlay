package com.shilapi.xcertplay.diagnostics

import com.shilapi.xcertplay.airplay.StreamReceiveStats
import com.shilapi.xcertplay.airplay.AudioPacketCapture
import java.nio.file.Files
import com.shilapi.xcertplay.media.VideoStats
import com.shilapi.xcertplay.network.WirelessReceiveDiagnostics
import com.shilapi.xcertplay.network.WirelessStartupDiagnostics
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiagnosticLoggingTest {
    @Before fun disabledByDefault() { DiagnosticLogging.setEnabled(false) }
    @After fun stopRecording() { DiagnosticLogging.setEnabled(false) }

    @Test fun disabledLoggingDoesNotEvaluateArgumentsOrReadKernelCounters() {
        DiagnosticLogging.record { error("diagnostic arguments evaluated") }
        assertEquals(0, DiagnosticLogging.logcat { error("log arguments evaluated") })
        val receive = WirelessReceiveDiagnostics("wlan0", { _, _ -> error("file opened") }, { error("clock read") })
        assertEquals("", receive.snapshot())
        val stream = StreamReceiveStats("audio", { error("report emitted") }, { error("clock read") })
        stream.reading(); stream.received(128, 10, 100); stream.decrypted(20, 128)
        stream.processed(); stream.flush(true)
        val video = VideoStats(nanoTime = { error("video clock read") })
        video.onReceived(128); video.onRendered(); video.onDecodeLatency(10)
        assertNull(video.logIfDue())
    }

    @Test fun queuedWorkCannotResumeAfterAnotherOptIn() {
        DiagnosticLogging.setEnabled(true)
        val oldToken = DiagnosticLogging.generation
        var stops = 0
        DiagnosticLogging.onDisabled { stops++ }
        DiagnosticLogging.setEnabled(false)
        DiagnosticLogging.setEnabled(false)
        assertEquals(1, stops)
        DiagnosticLogging.setEnabled(true)
        var writes = 0
        DiagnosticLogging.write(oldToken) { writes++ }
        DiagnosticLogging.write { writes++ }
        assertEquals(1, writes)
    }

    @Test fun disabledAudioCaptureDoesNotCreateDirectoriesOrFiles() {
        val root = Files.createTempDirectory("diplay-capture-disabled").toFile()
        try {
            val directory = root.resolve("audio")
            AudioPacketCapture(directory, 100).use { capture ->
                capture.record(byteArrayOf(1, 2), byteArrayOf(3), 1, null)
                assertFalse(capture.file.exists())
            }
            assertFalse(directory.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun disabledObserverDoesNotStartAndTurningOffInterruptsActiveSampling() {
        WirelessStartupDiagnostics({ error("sampled while disabled") }, { error("reported while disabled") },
            nowNs = { error("clock read while disabled") }).use { it.start() }
        DiagnosticLogging.setEnabled(true)
        val sampled = CountDownLatch(1)
        val queries = AtomicInteger()
        val interrupted = CountDownLatch(1)
        WirelessStartupDiagnostics({
            queries.incrementAndGet()
            sampled.countDown()
            try { CountDownLatch(1).await() } catch (_: InterruptedException) { interrupted.countDown() }
            "sample"
        }, {}).use { observer ->
            observer.start()
            assertTrue(sampled.await(2, TimeUnit.SECONDS))
            DiagnosticLogging.setEnabled(false)
            assertTrue(interrupted.await(2, TimeUnit.SECONDS))
            assertEquals(1, queries.get())
        }
    }
}
