package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class StartupTraceStoreTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    @Test fun aNewProcessArchivesTheBootTraceAndUiLogRotationCannotTruncateIt() {
        val directory = Files.createTempDirectory("diplay-startup-trace").toFile()
        val first = StartupTraceStore(directory)
        var second: StartupTraceStore? = null
        try {
            first.append("process=first stage=bluetooth-wait")
            assertTrue(first.awaitIdle(3000))
            first.close()
            second = StartupTraceStore(directory)
            second.append("process=second stage=retry")
            assertTrue(second.awaitIdle(3000))
            SessionLogFile(directory.resolve("diplay.log")).use { it.reset("UI recreated") }
            assertTrue(second.previous().contains("process=first stage=bluetooth-wait"))
            assertEquals("process=second stage=retry\n", second.current())
            assertTrue(directory.resolve(StartupTraceStore.CURRENT).readText().contains("process=second stage=retry"))
            assertTrue(second.status().contains("writeFailures=0 limitReached=false"))
        } finally { first.close(); second?.close(); directory.deleteRecursively() }
    }

    @Test fun clearingQueuedAndArchivedLogsDoesNotResurrectOldEvidence() {
        val directory = Files.createTempDirectory("diplay-startup-clear").toFile()
        val store = StartupTraceStore(directory)
        try {
            repeat(200) { store.append("old startup event=$it") }
            store.clear()
            store.append("new startup event")
            assertTrue(store.awaitIdle(3000))
            assertEquals("new startup event\n", directory.resolve(StartupTraceStore.CURRENT).readText())
            assertFalse(store.previous().contains("old startup"))
            assertEquals("new startup event\n", store.current())
        } finally { store.close(); directory.deleteRecursively() }
    }
}
