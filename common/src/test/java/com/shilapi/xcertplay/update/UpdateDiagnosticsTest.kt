package com.shilapi.xcertplay.update

import android.content.Context
import com.shilapi.xcertplay.CarPlayStartupDiagnostics
import com.shilapi.xcertplay.newui.diagnostics.NewUiDiagnosticReport
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class UpdateDiagnosticsTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val app get() = RuntimeEnvironment.getApplication()

    @Before @After fun clearHistory() {
        app.getSharedPreferences("diplay_update_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun persistentHistoryIsBoundedAndRetainsTheLatestFailure() {
        repeat(45) { UpdateDiagnostics.record(app, "event=$it") }
        val saved = app.getSharedPreferences("diplay_update_diagnostics", Context.MODE_PRIVATE).getString("events", "")!!
        assertEquals(40, saved.lines().size)
        assertTrue(saved.lines().first().endsWith("event=5"))
        assertTrue(saved.lines().last().endsWith("event=44"))
        assertEquals(saved, UpdateDiagnostics.report(app.applicationContext))
    }

    @Test fun reportIncludesInstallerHistoryAndPreUiBluetoothEventsWithoutASessionLog() {
        UpdateDiagnostics.record(app, "install-failed step=uri-create")
        CarPlayStartupDiagnostics.event("boot-bt stage=radio-wait result=timeout")
        val report = NewUiDiagnosticReport.build(app, "test")
        assertTrue(report.contains("--- Online update ---"))
        assertTrue(report.contains("Update check:"))
        assertTrue(report.contains("install-failed step=uri-create"))
        assertTrue(report.contains("--- Boot Bluetooth events ---"))
        assertTrue(report.contains("boot-bt stage=radio-wait result=timeout"))
        assertTrue(report.contains("processElapsedMs="))
    }
}
