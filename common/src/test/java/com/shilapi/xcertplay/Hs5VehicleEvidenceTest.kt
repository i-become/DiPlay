package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class Hs5VehicleEvidenceTest {
    @After fun close() { DiagnosticLogging.setEnabled(false) }

    @Test fun disabledDoesNoFrameFormattingAndTogglingDiscardsPreviousOptIn() {
        val evidence = Hs5VehicleEvidence()
        DiagnosticLogging.setEnabled(false)
        evidence.observe(0, Hs5VehicleSample()) { error("disabled diagnostics did work") }
        assertEquals("vehicleEvidence=[]", evidence.report())
        DiagnosticLogging.setEnabled(true)
        evidence.observe(1000, Hs5VehicleSample(speed = Hs5TimedInt(42, 1000))) { "can0=frame-a" }
        assertTrue(evidence.report().contains("can0=frame-a"))
        DiagnosticLogging.setEnabled(false)
        DiagnosticLogging.setEnabled(true)
        assertEquals("vehicleEvidence=[]", evidence.report())
        evidence.observe(2000, Hs5VehicleSample()) { "can0=frame-b" }
        assertFalse(evidence.report().contains("frame-a"))
    }

    @Test fun boundedOneSecondHistoryRetainsRawBrakeAndCanAtParking() {
        DiagnosticLogging.setEnabled(true)
        val evidence = Hs5VehicleEvidence()
        var formats = 0
        repeat(1400) { index ->
            val at = index * 500L
            evidence.observe(at, Hs5VehicleSample(speed = Hs5TimedInt(index, at),
                brake = Hs5TimedInt(index % 2, at), gear = Hs5TimedInt(3, at))) {
                formats++
                "can0=sample-$index"
            }
        }
        val report = evidence.report()
        assertEquals(700, formats)
        assertEquals(600, "elapsedMs=".toRegex().findAll(report).count())
        assertFalse(report.contains("can0=sample-0\n"))
        assertTrue(report.contains("can0=sample-1398"))
        assertTrue(report.contains("speedRaw=1398 gearRaw=3 brakeRaw=0"))
    }
}
