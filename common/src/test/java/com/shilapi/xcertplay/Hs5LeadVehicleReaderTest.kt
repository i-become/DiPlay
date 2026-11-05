package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class Hs5LeadVehicleReaderTest {
    private fun frame(id: Int, payload: ByteArray = ByteArray(8)) = byteArrayOf(id.toByte()) + payload

    @Test fun readerRequestsOnlyIfC2AndIfC4AndDecodesLsb0Fields() {
        val requested = mutableListOf<Int>()
        val payload = ByteArray(8)
        payload[1] = 8 // ObjectDetection, payload byte 1 bit 3
        payload[3] = 0x30 // LDWLaneDetectSt = 3, payload byte 3 bits 4..5
        val reader = Hs5OemLeadVehicleReader(
            clock = { 100L },
            adasFrameReader = Hs5AdasFrameReader { id ->
                requested += id
                frame(id, if (id == 55) payload else ByteArray(8))
            },
        )

        val result = reader.read()
        assertEquals(listOf(55, 6), requested)
        assertEquals(true, result.detected)
        assertEquals(3, result.laneDetectState)
        assertEquals(true, result.leftLaneDetected)
        assertEquals(true, result.rightLaneDetected)
    }

    @Test fun allFourLaneCombinationsMatchDocumentedMapping() {
        for (state in 0..3) {
            val payload = ByteArray(8).also { it[3] = (state shl 4).toByte() }
            val reading = Hs5OemLeadVehicleReader(clock = { 1L }, adasFrameReader = Hs5AdasFrameReader { id ->
                if (id == 55) frame(id, payload) else frame(id)
            }).read()
            assertEquals(state and 1 != 0, reading.leftLaneDetected)
            assertEquals(state and 2 != 0, reading.rightLaneDetected)
        }
    }

    @Test fun missingOrInvalidFramesStayUnknownAndNeverBecomeClear() {
        val badFrames = listOf<ByteArray?>(null, ByteArray(8), frame(6), ByteArray(27))
        for (bad in badFrames) {
            val reading = Hs5OemLeadVehicleReader(clock = { 1L }, adasFrameReader = Hs5AdasFrameReader { id ->
                if (id == 55) bad else frame(id)
            }).read()
            assertNull(reading.detected)
            assertNull(reading.laneDetectState)
            assertNull(reading.leftLaneDetected)
            assertNull(reading.rightLaneDetected)
        }
    }

    @Test fun validZeroIsClearAndInvalidDetectionBitsStayUnknown() {
        val payload = ByteArray(8)
        payload[1] = 0
        payload[3] = 0
        val validZero = Hs5OemLeadVehicleReader(clock = { 1L }, adasFrameReader = Hs5AdasFrameReader { id ->
            frame(id, payload)
        }).read()
        assertEquals(false, validZero.detected)
        assertEquals(false, validZero.leftLaneDetected)
        assertEquals(false, validZero.rightLaneDetected)

        for (raw in listOf(null, -1, 2, 254, 255)) {
            val reading = Hs5LeadVehicleReading(raw)
            assertNull(reading.detected)
        }
    }

    @Test fun frameDecoderValidatesResponseIdAndLengthAndStripsRpcIdByte() {
        val raw = frame(55, byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8))
        assertArrayEquals(byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8), Hs5AdasFrameDecoder.decode(55, raw))
        assertNull(Hs5AdasFrameDecoder.decode(55, frame(6)))
        assertNull(Hs5AdasFrameDecoder.decode(55, ByteArray(8)))
        assertNull(Hs5AdasFrameDecoder.decode(55, ByteArray(27)))
        assertNull(Hs5AdasFrameDecoder.decode(55, null))
    }
}
