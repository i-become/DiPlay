package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import org.junit.Assert.*
import org.junit.Test

class CarPlayLaneGuidanceTest {
    @Test fun multiLaneContainerComponentListsAndOneBasedPositions() {
        val payload = body {
            u16List(0, listOf(43, 42)); u16(1, 17)
            group(2) {
                u16(0, 2); u8(1, 2); i16(2, 0); i16(2, 90); i16(3, 90)
                u16(0, 1); u8(1, 1); i16(2, -180)
            }
        }
        val parsed = CarPlayLaneGuidance.decode(payload)!!
        assertEquals(listOf(1, 2), parsed.lanes.map { it.index })
        assertEquals(listOf(0, 90), parsed.lanes.last().directions)
        assertEquals(90, parsed.lanes.last().selectedDirection)
        assertNotNull(CarPlayLaneGuidance.decode(body {
            u16(1, 1); group(2) { u16(0, 0); u8(1, 1); i16(2, 0) }
        }))
    }

    @Test fun stateTwoInvalidPacketsAndOtherComponentsDoNotReplaceCommittedState() {
        val state = CarPlayLaneState()
        state.acceptLanes(block(7))
        state.acceptRoute(body { u16(0x10, 7); u8(0x12, 1) })
        state.acceptRoute(body { u8(1, 2) })
        assertEquals(90, state.current().single().direction)
        assertFalse(state.acceptLanes(block(7).dropLast(1).toByteArray()))
        state.acceptRoute(body { u16(0x10, 8); u8(0x12, 0); u8(0x12, 1) })
        state.acceptRoute(body { u16(0, 43); u8(1, 0) })
        assertTrue(state.acceptLanes(body { u16(0, 43); u16(1, 7) }))
        assertEquals(90, state.current().single().direction)
    }

    @Test fun monotonicExpiryClearsCacheAndCannotReviveOldEvents() {
        var now = 0L
        val state = CarPlayLaneState { now }
        state.acceptRoute(body { u16(0x10, 7); u8(0x12, 1) })
        state.acceptLanes(block(7))
        now = 29_999_999_999L
        assertEquals(1, state.current().size)
        now++
        assertTrue(state.current().isEmpty())
        state.acceptRoute(body { u16(0x10, 7); u8(0x12, 1) })
        assertTrue(state.current().isEmpty())
        state.acceptLanes(block(7))
        assertEquals(1, state.current().size)
        state.clear()
        assertTrue(state.current().isEmpty())
    }

    @Test fun invalidLengthsSingletonsStatusesAndPositionsRejectAtomically() {
        val invalid = listOf(
            byteArrayOf(0, 3, 0, 0), byteArrayOf(0, 4, 0),
            body { void(0); u16(1, 7) },
            body { u16(1, 65535); group(2) { u16(0, 0); u8(1, 1); i16(2, 0) } },
            body { u16(1, 7); group(2) { u16(0, 0); u8(1, 1); u8(1, 2); i16(2, 0) } },
            body { u16(1, 7); group(2) { u16(0, 0); u8(1, 1); u8(2, 0) } },
            body { u16(1, 7); group(2) { u16(0, 0); u8(1, 1); void(2) } },
            body { u16(1, 7); group(2) { u16(0, 0); u8(1, 1); repeat(17) { i16(2, 0) } } },
            body { u16(1, 7); for (n in listOf(0, 2)) group(2) { u16(0, n); u8(1, 1); i16(2, 0) } },
        )
        invalid.forEach { assertNull(CarPlayLaneGuidance.decode(it)) }
        val fullRange = CarPlayLaneGuidance.decode(body {
            u16(1, 7); group(2) { u16(0, 0); u8(1, 1); i16(2, -32768); i16(2, 32767) }
        })!!
        assertEquals(listOf(-32768, 32767), fullRange.lanes.single().directions)
    }

    @Test fun handoffSyntheticFixtureReplaysThroughTheProductionStateMachine() {
        // Artificial handoff data, NOT a real iPhone capture.
        val laneHex = "00060000002a00060001001100150002000600000000000500010000060002ffa6001500020006000000010005000102000600020000001d00020006000000020005000102000800020000005a00060003000000150002000600000003000500010000060002005a"
        val routeHex = "00060000002a00050001010006001000110006001100010005001201"
        fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        for (laneFirst in listOf(true, false)) {
            val state = CarPlayLaneState()
            if (laneFirst) state.acceptLanes(bytes(laneHex))
            state.acceptRoute(bytes(routeHex))
            if (!laneFirst) state.acceptLanes(bytes(laneHex))
            assertEquals(17, state.currentBlock()!!.index)
            assertEquals(4, state.current().size)
            assertEquals(listOf(0, 90), state.current()[2].directions)
            assertEquals(0, state.current()[2].selectedDirection)
        }
    }
    private fun body(id: Int = 0x5204, block: Iap2BodyBuilder.() -> Unit) =
        Iap2Messages.buildRaw(id, block).payload

    private fun block(index: Int = 0, direction: Int = 90) = body {
        u16(0, 42); u16(1, index)
        group(2) { u16(0, 0); u8(1, 2); i16(2, direction) }
    }

    @Test fun measuredThreeLanePacketKeepsTheSignedAnglesAndOnlyHighlightsTheRightBranch() {
        // Historical packet: highlight outside the direction list is preserved raw, not drawn.
        val hex = "00060000002a000600010000" +
            "00150002000600000000000500010000060002ffa6" +
            "001500020006000000010005000100000600020000" +
            "001b0002000600000002000500010200060002000000060003005a"
        val parsed = CarPlayLaneGuidance.decode(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())!!
        assertEquals(0, parsed.index)
        assertEquals(listOf(-90, 0, 0), parsed.lanes.map { it.direction })
        assertEquals(listOf(false, false, false), parsed.lanes.map { it.recommended })
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
        assertTrue(state.current().isEmpty()) // Unsupported geometry hides the whole event.
        val lanes = CarPlayLaneGuidance.decode(payload)!!.lanes
        assertEquals(3, lanes.size)
        assertEquals(listOf(0, 90), lanes[0].directions)
        assertEquals(90, lanes[0].highlightedDirection)
        assertEquals(listOf(-90, 0), lanes[1].directions)
        assertEquals(1000, lanes[1].highlightedDirection)
        assertEquals(listOf(1000), lanes[2].directions)
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
        for (index in 1..40) state.acceptLanes(block(index, 90))
        assertEquals(-90, state.current().single().direction)
        state.acceptRoute(body(0x5201) { u16(0x10, 1) })
        assertTrue(state.current().isEmpty())
        state.acceptRoute(body(0x5201) { u16(0x10, 40) })
        assertEquals(90, state.current().single().direction)
    }

    @Test fun routeEndEmptyUpdatesAndMalformedPacketsRetireVisibleLanes() {
        for (clear in listOf<(CarPlayLaneState) -> Unit>(
            { it.acceptRoute(body(0x5201) { u8(1, 0) }) },
            { it.acceptRoute(body(0x5201) { u16(0x11, 0) }) },
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
            body { u16(0, 42); u16(1, 0); repeat(2) { group(2) { u16(0, 0); u8(1, 2); i16(2, 0) } } },
            body { u16(0, 42); u16(1, 0); repeat(17) { n -> group(2) { u16(0, n); u8(1, 2); i16(2, 0) } } },
        )) assertNull(CarPlayLaneGuidance.decode(payload))
        val unknown = body { u16(0, 42); u16(1, 0); group(2) { u16(0, 0); u8(1, 255); i16(2, 0) } }
        assertNull(CarPlayLaneGuidance.decode(unknown))
    }
}
