package com.shilapi.xcertplay

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CrashDiagnosticsTest {
    @Test fun stackIsBoundedAndExcludesExceptionMessages() {
        val error = IllegalStateException("private phone address", IllegalArgumentException("private file path"))
        error.stackTrace = Array(500) { StackTraceElement("Renderer", "frame", "Renderer.kt", 42) }
        val text = CrashDiagnostics.format("main", error)
        assertTrue(text.contains("java.lang.IllegalStateException"))
        assertTrue(text.contains("java.lang.IllegalArgumentException"))
        assertTrue(text.contains("Renderer.frame(Renderer.kt:42)"))
        assertFalse(text.contains("private phone"))
        assertFalse(text.contains("private file"))
        assertTrue(text.length <= 16 * 1024)
        assertEquals(48, text.lineSequence().count { "Renderer.frame" in it })
    }

    @Test fun disablingDiagnosticsRemovesRetainedCrashAndHidesReport() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(DiPlaySessionLogFiles.directory(context), "last-java-crash.txt")
        try {
            DiagnosticLogging.setEnabled(true)
            file.parentFile!!.mkdirs(); file.writeText("retained stack")
            assertEquals("retained stack", CrashDiagnostics.report(context))
            AirPlayPersistence.saveSessionLogRecordingEnabled(context, false)
            assertFalse(file.exists())
            assertEquals("Java crash recording disabled.", CrashDiagnostics.report(context))
        } finally { DiagnosticLogging.setEnabled(false); CrashDiagnostics.clear(context) }
    }
}
