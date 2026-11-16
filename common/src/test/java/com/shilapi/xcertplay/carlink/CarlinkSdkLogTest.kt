package com.shilapi.xcertplay.carlink

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CarlinkSdkLogTest {
    @After fun stop() { DiagnosticLogging.setEnabled(false) }

    @Test fun sdkLogsAndExceptionsFollowLiveRecordingSwitch() {
        DiagnosticLogging.setEnabled(false)
        ShadowLog.clear()
        outputAllLevels()
        assertTrue(ShadowLog.getLogs().isEmpty())

        DiagnosticLogging.setEnabled(true)
        outputAllLevels()
        assertEquals(11, ShadowLog.getLogs().size)

        DiagnosticLogging.setEnabled(false)
        ShadowLog.clear()
        outputAllLevels()
        assertTrue(ShadowLog.getLogs().isEmpty())

        DiagnosticLogging.setEnabled(true)
        CarlinkSdkLog.i("sdk", "resumed")
        assertEquals("resumed", ShadowLog.getLogsForTag("sdk").single().msg)
    }

    private fun outputAllLevels() {
        val error = IllegalStateException("sdk failure")
        CarlinkSdkLog.v("sdk", "v"); CarlinkSdkLog.v("sdk", "v", error)
        CarlinkSdkLog.d("sdk", "d"); CarlinkSdkLog.d("sdk", "d", error)
        CarlinkSdkLog.i("sdk", "i"); CarlinkSdkLog.i("sdk", "i", error)
        CarlinkSdkLog.w("sdk", "w"); CarlinkSdkLog.w("sdk", "w", error)
        CarlinkSdkLog.e("sdk", "e"); CarlinkSdkLog.e("sdk", "e", error)
        CarlinkSdkLog.printStackTrace(error)
    }

    @Test fun sdkStandardOutputAlsoRequiresRecording() {
        val bytes = java.io.ByteArrayOutputStream()
        java.io.PrintStream(bytes).use { stream ->
            DiagnosticLogging.setEnabled(false)
            CarlinkSdkLog.println(stream, "off")
            assertEquals(0, bytes.size())
            DiagnosticLogging.setEnabled(true)
            CarlinkSdkLog.println(stream, "on")
            val enabled = bytes.toString()
            assertTrue(enabled.contains("on"))
            DiagnosticLogging.setEnabled(false)
            CarlinkSdkLog.println(stream, "off again")
            assertEquals(enabled, bytes.toString())
        }
    }
}
