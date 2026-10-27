package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class Hs5SpeedEvidenceTest {
    @After fun close() { DiagnosticLogging.setEnabled(false) }

    @Test fun disabledDoesNotCaptureAndTogglingCannotResurrectOldEvidence() {
        val evidence = Hs5SpeedEvidence()
        DiagnosticLogging.setEnabled(false)
        evidence.observe("getter7", 4200, 10)
        assertEquals("speedEvidence=[]", evidence.report())
        DiagnosticLogging.setEnabled(true)
        assertEquals("speedEvidence=[]", evidence.report())
        evidence.observe("push10", 4300, 20)
        assertTrue(evidence.report().contains("rawInt=4300"))
        DiagnosticLogging.setEnabled(false)
        evidence.observe("push10", 4400, 30)
        DiagnosticLogging.setEnabled(true)
        assertEquals("speedEvidence=[]", evidence.report())
        evidence.observe("getter7", 0, 40)
        assertFalse(evidence.report().contains("4300"))
    }

    @Test fun changesPreserveMovingRejectedAndFailedValuesAfterStationaryExport() {
        DiagnosticLogging.setEnabled(true)
        val evidence = Hs5SpeedEvidence()
        evidence.observe("getter7", 4200, 10)
        evidence.observe("push10", 45, 20)
        evidence.observe("getter7", null, 30)
        evidence.observe("push10", 0, 40)
        repeat(100) { evidence.observe("push10", 0, 50L + it) }
        val report = evidence.report()
        assertTrue(report.contains("rawInt=4200 rawHex=1068 elapsedMs=10 rangeValidKph=42.0"))
        assertTrue(report.contains("rangeValidKph=0.45 units=raw-div-100-kph result=range-valid"))
        assertTrue(report.contains("result=read-failed"))
        assertEquals(4, "source=".toRegex().findAll(report).count())
        evidence.clear()
        assertEquals("speedEvidence=[]", evidence.report())
    }

    @Test fun historyIsBoundedAndUsesTheConfirmedHundredthsUnit() {
        DiagnosticLogging.setEnabled(true)
        val evidence = Hs5SpeedEvidence()
        repeat(1000) { evidence.observe("push10", it + 1000, it.toLong()) }
        val report = evidence.report()
        assertEquals(64, "source=".toRegex().findAll(report).count())
        assertFalse(report.contains("rawInt=1000 "))
        assertTrue(report.contains("rawInt=1999 "))
        assertTrue(report.contains("rangeValidKph=19.99"))
        assertTrue(report.contains("result=range-valid"))
    }
}
