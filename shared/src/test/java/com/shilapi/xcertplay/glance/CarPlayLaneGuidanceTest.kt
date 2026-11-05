package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test

class CarPlayLaneGuidanceTest {
    private fun body(id: Int = 0x5204, block: Iap2BodyBuilder.() -> Unit) =
        Iap2Messages.buildRaw(id, block).payload

    private fun block(index: Int = 0, direction: Int = 90) = body {
        u16(0, 42); u16(1, index)
        group(2) { u16(0, 0); u8(1, 2); i16(2, direction) }
    }

    @Test fun measuredThreeLanePacketKeepsTheSignedAnglesAndOnlyHighlightsTheRightBranch() {
        // Captured Apple Maps 0x5204. Third lane goes straight/right; only right is recommended.
        val hex = "00060000002a000600010000" +
            "00150002000600000000000500010000060002ffa6" +
            "001500020006000000010005000100000600020000" +
            "001b0002000600000002000500010200060002000000060003005a"
        val parsed = CarPlayLaneGuidance.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())!!
        assertEquals(0, parsed.index)
        assertEquals(listOf(-90, 0, 0), parsed.lanes.map { it.direction })
        assertEquals(listOf(false, false, true), parsed.lanes.map { it.recommended })
        assertEquals(90, parsed.lanes.last().highlightedDirection)
    }

    @Test fun packedRepeatedAndUnknownDirectionsDoNotDiscardValidLanes() {
        val payload = body {
            u16(0, 42); u16(1, 3)
            group(2) { u16(0, 0); u8(1, 2); u16List(2, listOf(0, 90)); i16(3, 90) }
            group(2) { u16(0, 1); u8(1, 0); i16(2, -90); i16(2, 0); i16(3, 1000) }
            group(2) { u16(0, 2); u8(1, 0); i16(2, 1000) }
        }
        val state = CarPlayLaneState()
        assertTrue(state.acceptLanes(payload))
        state.acceptRoute(body(0x5201) { u16(0x10, 3); u16(0x11, 1); u8(0x12, 1) })
        val lanes = state.current()
        assertEquals(3, lanes.size)
        assertEquals(listOf(0, 90), lanes[0].directions)
        assertEquals(90, lanes[0].highlightedDirection)
        assertEquals(listOf(-90, 0), lanes[1].directions)
        assertNull(lanes[1].highlightedDirection)
        assertTrue(lanes[2].directions.isEmpty())
    }

    @Test fun laneBlocksCanArriveBeforeOrAfterTheCurrentRouteReference() {
        val state = CarPlayLaneState()
        state.acceptLanes(block(7, -90))
        state.acceptLanes(block(8, 90)) // Prefetch must not replace the current junction.
        assertTrue(state.current().isEmpty())
        state.acceptRoute(body(0x5201) { u16(0x10, 7); u16(0x11, 2); u8(0x12, 1) })
        assertEquals(-90, state.current().single().direction)
        state.acceptRoute(body(0x5201) { u16(0x10, 9) })
        assertTrue(state.current().isEmpty()) // Unknown reference cannot reuse the previous road.
        state.acceptLanes(block(9, 0))
        assertEquals(0, state.current().single().direction)
        state.acceptRoute(body(0x5201) { u8(0x12, 0) })
        assertTrue(state.current().isEmpty())
        state.acceptRoute(body(0x5201) { u16(0x10, 8); u8(0x12, 1) })
        assertEquals(90, state.current().single().direction)
    }

    @Test fun prefetchEvictionKeepsTheCurrentlyVisibleJunction() {
        val state = CarPlayLaneState()
        state.acceptLanes(block(0, -90))
        state.acceptRoute(body(0x5201) { u16(0x10, 0); u8(0x12, 1) })
        for (index in 1..12) state.acceptLanes(block(index, 90))
        assertEquals(-90, state.current().single().direction)
        state.acceptRoute(body(0x5201) { u16(0x10, 1) })
        assertTrue(state.current().isEmpty())
        state.acceptRoute(body(0x5201) { u16(0x10, 12) })
        assertEquals(90, state.current().single().direction)
    }

    @Test fun routeEndEmptyUpdatesAndMalformedPacketsRetireVisibleLanes() {
        for (clear in listOf<(CarPlayLaneState) -> Unit>(
            { it.acceptRoute(body(0x5201) { u8(1, 0) }) },
            { it.acceptRoute(body(0x5201) { u8(1, 2) }) },
            { it.acceptRoute(body(0x5201) { u16(0x11, 0) }) },
            { it.acceptLanes(body { u16(0, 42); u16(1, 0) }) },
            { it.acceptLanes(byteArrayOf()) },
            { it.acceptLanes(block().dropLast(1).toByteArray()) },
            { it.clear() },
        )) {
            val state = CarPlayLaneState()
            state.acceptLanes(block())
            state.acceptRoute(body(0x5201) { u16(0x10, 0); u8(0x12, 1) })
            assertEquals(1, state.current().size)
            clear(state)
            assertTrue(state.current().isEmpty())
        }
    }

    @Test fun malformedAndAmbiguousLanesCannotBecomeStraightOrRecommendedDefaults() {
        for (payload in listOf(
            body { u16(0, 41); u16(1, 0) },
            body { u16(0, 42); u16(1, 0); group(2) { u16(0, 0); u8(1, 2) } },
            body { u16(0, 42); u16(1, 0); group(2) { u16(0, 0); u16(1, 2); i16(2, 0) } },
            body { u16(0, 42); u16(1, 0); group(2) { u16(0, 0); u8(1, 2); i16(2, 181) } },
            body { u16(0, 42); u16(1, 0); repeat(2) { group(2) { u16(0, 0); u8(1, 2); i16(2, 0) } } },
            body { u16(0, 42); u16(1, 0); repeat(17) { n -> group(2) { u16(0, n); u8(1, 2); i16(2, 0) } } },
        )) assertNull(CarPlayLaneGuidance.decode(payload))
        val unknown = body { u16(0, 42); u16(1, 0); group(2) { u16(0, 0); u8(1, 255); i16(2, 0) } }
        assertFalse(CarPlayLaneGuidance.decode(unknown)!!.lanes.single().recommended)
    }
}
