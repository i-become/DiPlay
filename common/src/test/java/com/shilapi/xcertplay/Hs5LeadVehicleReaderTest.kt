package com.shilapi.xcertplay

import com.hs5.vehicle.sdk.can.CanSignalRuleDecoder.RuleSample
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5LeadVehicleReaderTest {
    private fun rule(id: Int, raw: Int? = null) =
        RuleSample("Rule_ObjectDetection", id, 0, 0, "ObjectDetection", raw, null, null)

    @Test fun validOemDetectionDoesNotReadRpcOrRequireRuleMetadata() {
        var raw = 1
        val reader = Hs5OemLeadVehicleReader(clock = { 100 }, oemSignal = { raw },
            decodeRule = { error("OEM value should win") }, readFrame = { error("No RPC needed") })
        assertEquals(true, reader.read().detected)
        raw = 0
        assertEquals(false, reader.read().detected)
    }

    @Test fun fallbackReadsOnlyTheRuntimeRuleFrameAndKeepsUnknownValuesUnknown() {
        val frames = mutableListOf<Int>()
        val frame = JSONObject()
        var raw: Int? = 1
        val reader = Hs5OemLeadVehicleReader(clock = { 100 }, oemSignal = { null },
            decodeRule = { json ->
                if (json != null) assertSame(frame, json)
                rule(73, if (json == null) null else raw)
            }, readFrame = { id -> frames += id; frame })
        assertEquals(true, reader.read().detected)
        raw = 0
        assertEquals(false, reader.read().detected)
        raw = 7
        assertNull(reader.read().detected)
        raw = null
        assertNull(reader.read().detected)
        assertEquals(listOf(73, 73, 73, 73), frames)
    }

    @Test fun missingFirmwareRuleBacksOffAndNeverGuessesAFrameOrAnObject() {
        var now = 100L
        var probes = 0
        val reader = Hs5OemLeadVehicleReader(clock = { now }, oemSignal = { probes++; null },
            decodeRule = { rule(-1) }, readFrame = { error("No known frame") })
        assertNull(reader.read().detected)
        now += 9999
        assertNull(reader.read().detected)
        assertEquals(1, probes)
        now++
        assertNull(reader.read().detected)
        assertEquals(2, probes)
    }
}
