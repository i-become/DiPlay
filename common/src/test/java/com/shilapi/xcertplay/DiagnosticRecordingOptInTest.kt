package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.nio.file.Files
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class DiagnosticRecordingOptInTest {
    @Before fun disable() { DiagnosticLogging.setEnabled(false) }
    @After fun cleanup() { DiagnosticLogging.setEnabled(false) }

    @Test fun disabledReportDoesNotQueryTheContextOrCollectSnapshots() {
        val context = org.mockito.Mockito.mock(android.content.Context::class.java)
        assertEquals("Session log recording is disabled.",
            com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticReport.build(context, "test"))
        StartupDiagnosticSnapshot.received(context, true)
        StartupDiagnosticSnapshot.launchResult(context)
        PhoneWakeDiagnostics.record(context, "off")
        DisplayDiagnosticSnapshot.selection(context, 100, 110, false)
        assertNull(DisplayDiagnosticSnapshot.begin(context, "request", "capability", "effective"))
        com.shilapi.xcertplay.update.UpdateDiagnostics.record(context, "off")
        org.mockito.Mockito.verifyNoInteractions(context)
    }

    @Test fun disabledSessionDoesNotCreateADirectoryOrAFile() {
        val root = Files.createTempDirectory("diplay-no-recording").toFile()
        try {
            val target = root.resolve("logs/diplay.log")
            SessionLogFile(target).use { log ->
                log.reset("header")
                log.append("off")
                AsyncDiagnosticLog.append(log, "off")
            }
            assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
            assertFalse(target.parentFile.exists())
        } finally { root.deleteRecursively() }
    }

    @Test fun capturedCallbacksCannotWriteIntoAReenabledSession() {
        val root = Files.createTempDirectory("diplay-recording-generation").toFile()
        try {
            DiagnosticLogging.setEnabled(true)
            val old = SessionLogFile(root.resolve("old.log"))
            old.reset("first session")
            DiagnosticLogging.setEnabled(false)
            old.append("disabled callback")
            DiagnosticLogging.setEnabled(true)
            old.append("stale callback")
            AsyncDiagnosticLog.append(old, "queued stale callback")
            val current = SessionLogFile(root.resolve("current.log"))
            current.reset("new session")
            current.append("active callback")
            assertTrue(AsyncDiagnosticLog.awaitIdle(2_000))
            assertEquals("first session\n", old.file.readText())
            assertTrue(current.file.readText().contains("active callback"))
            old.close(); current.close()
        } finally { root.deleteRecursively() }
    }
}
