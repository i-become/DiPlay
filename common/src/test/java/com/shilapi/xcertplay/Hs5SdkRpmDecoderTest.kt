package com.shilapi.xcertplay

import com.hs5.vehicle.sdk.can.CanPayloadDecoder
import com.hs5.vehicle.sdk.Hs5VehicleFieldSemantics
import alfusos.rpc.RpcManager
import com.hs5.vehicle.sdk.can.CanSignalRuleDecoder
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5SdkRpmDecoderTest {
    private fun canFrame(hex: String) = JSONObject()
        .put("rpc_available", true)
        .put("can_getMessage_op1872", JSONObject().put("0", hex))

    @Test fun ems3FallbackUsesBytes12AndNeverUsesDebugByteAsPrimary() {
        val frame = CanPayloadDecoder.parseHexFrame("[00 00 EA 0A 00 00 00 00 20]")
        assertEquals(9, frame!!.size)
        assertEquals(699, CanPayloadDecoder.rpmFromEms3Bytes12Le(frame))
        assertEquals(9, CanSignalRuleDecoder.frameFromCanJson(canFrame(
            "[00 00 EA 0A 00 00 00 00 20]"), 0)!!.size)
        assertEquals(128, CanPayloadDecoder.rpmDebugFromEms3Byte7(frame))
    }

    @Test fun parkAtZeroSpeedHidesStaleSubIdleRpmButKeepsRealIdle() {
        assertTrue(CanPayloadDecoder.shouldHideRpmInParkIdle(
            Hs5VehicleFieldSemantics.enrichSnapshot(JSONObject().put("speed", 0).put("gear", 0)), 300))
        assertFalse(CanPayloadDecoder.shouldHideRpmInParkIdle(
            Hs5VehicleFieldSemantics.enrichSnapshot(JSONObject().put("speed", 0).put("gear", 0)), 699))
        assertFalse(CanPayloadDecoder.shouldHideRpmInParkIdle(
            Hs5VehicleFieldSemantics.enrichSnapshot(JSONObject().put("speed", 150).put("gear", 0)), 300))
    }

    @Test fun sdkReaderUsesFullFrameFallbackAndAppliesParkGate() {
        try {
            RpcManager.response0 = byteArrayOf(0, 0, 0xEA.toByte(), 0x0A, 0, 0, 0, 0, 0)
            val idling = Hs5SdkRpmReader().read(0, 0)
            assertEquals(699f, idling.rpmPrimary!!, 0f)
            assertEquals("EMS_3 payload[1..2] 小端 ×0.25 回退", idling.note)

            RpcManager.response0 = byteArrayOf(0, 0, 0xB0.toByte(), 0x04, 0, 0, 0, 0, 0)
            val stale = Hs5SdkRpmReader().read(0, 0)
            assertNull(stale.rpmPrimary)
            assertTrue(stale.note!!.contains("P挡"))
        } finally {
            RpcManager.response0 = null
        }
    }
}
