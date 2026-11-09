package com.shilapi.xcertplay.hud

import com.shilapi.xcertplay.iap2.message.Iap2Messages
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Hs5RouteStateTest {
    @Test
    fun `combines maneuver and route updates`() {
        val state = Hs5RouteState()
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, 7), tlv(0x03, 1), tlv(0x08, 0)),
        )

        val change = state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x0a, 0, 0, 0, 150), tlv(0x0d, 0, 7)),
        )

        assertEquals(Hs5RouteChange.GUIDANCE, change)
        assertEquals(Hs5Guidance(distanceMeters = 150, maneuver = 1, gaode = 1), state.current())
    }

    @Test
    fun `maps right-hand u-turn`() {
        val state = Hs5RouteState()
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, 3), tlv(0x03, 4), tlv(0x08, 1)),
        )
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x0a, 0, 0, 0, 50), tlv(0x0d, 0, 3)),
        )

        assertEquals(Hs5Guidance(distanceMeters = 50, maneuver = 8, gaode = 10), state.current())
    }

    @Test
    fun `route end clears guidance`() {
        val state = populatedState()

        val change = state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x01, 2)))

        assertEquals(Hs5RouteChange.CLEAR, change)
        assertNull(state.current())
    }

    @Test
    fun `overlay keeps the last instruction across a NoRouteSet handoff`() {
        val state = populatedState(keepAcrossNoRoute = true)
        val before = state.current()

        val change = state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x01, 0)))

        assertEquals(Hs5RouteChange.NONE, change)
        assertEquals(before, state.current())
    }

    @Test
    fun `overlay still clears on arrival even when NoRouteSet is ignored`() {
        val state = populatedState(keepAcrossNoRoute = true)

        val change = state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x01, 2)))

        assertEquals(Hs5RouteChange.CLEAR, change)
        assertNull(state.current())
    }

    @Test
    fun `malformed frame does not alter active guidance`() {
        val state = populatedState()
        val before = state.current()

        val change = state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            byteArrayOf(0, 8, 0, 1, 1),
        )

        assertEquals(Hs5RouteChange.NONE, change)
        assertEquals(before, state.current())
    }


    @Test
    fun `carries after-maneuver road and arrival time`() {
        val state = Hs5RouteState()
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, 2), tlv(0x03, 2), tlv(0x04, *utf8z("Am Wehr")), tlv(0x08, 0)),
        )
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(
                tlv(0x01, 1), tlv(0x03, *utf8z("Hauptstraße")), tlv(0x05, 0, 0, 0, 0, 0x68, 0xd5, 0x2a, 0x40),
                tlv(0x0a, 0, 0, 1, 44), tlv(0x0d, 0, 2),
            ),
        )

        val guidance = state.current()!!
        assertEquals("Am Wehr", guidance.road)
        assertEquals(0x68d52a40L, guidance.arrivalEpochSeconds)
        assertEquals(300, guidance.distanceMeters)
    }

    @Test
    fun `brief empty maneuver list keeps guidance and a lasting one hides it without losing maneuvers`() {
        var now = 0L
        val state = populatedState(nanoTime = { now })

        assertEquals(Hs5RouteChange.NONE, state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x01, 5), tlv(0x0d))))
        now = 2_000_000_000L
        assertEquals(2, state.current()!!.maneuver) // a blip does not blank the outputs
        now = 3_100_000_000L
        assertNull(state.current())

        // The iPhone lists the same maneuver again without resending its 0x5202 details.
        val resumed = state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x0a, 0, 0, 0, 20), tlv(0x0d, 0, 1)),
        )
        assertEquals(Hs5RouteChange.GUIDANCE, resumed)
        assertEquals(2, state.current()!!.maneuver)
    }

    @Test
    fun `falls back to current road`() {
        val state = Hs5RouteState()
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE, tlvs(tlv(0x01, 0, 0), tlv(0x03, 1)))
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x03, *utf8z("Hauptstraße")), tlv(0x0d, 0, 0)),
        )
        assertEquals("Hauptstraße", state.current()!!.road)
    }

    @Test
    fun `uses maneuver description when next-road and current-road fields are absent`() {
        val state = Hs5RouteState()
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, 4), tlv(0x02, *utf8z("Turn right onto Central Avenue")), tlv(0x03, 2)))
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x0d, 0, 4)))

        assertEquals("Turn right onto Central Avenue", state.current()!!.road)
    }

    @Test
    fun `keeps junction exit and active lane guidance metadata`() {
        val state = Hs5RouteState()
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(
                tlv(0x01, 0, 4), tlv(0x02, *utf8z("Keep left at exit")), tlv(0x03, 13),
                tlv(0x04, *utf8z("A8")), tlv(0x08, 0), tlv(0x09, 1),
                tlv(0x0a, 0xff, 0xa6, 0, 0x5a), tlv(0x0b, 0, 0x5a),
                tlv(0x0d, *utf8z("Exit 12")),
            ),
        )
        val lane = tlvs(
            tlv(0x0000, 0, 42),
            tlv(0x0001, 0, 3),
            tlv(
                0x0002,
                tlvs(
                    tlv(0x0000, 0, 0), tlv(0x0001, 2), tlv(0x0002, 0, 0, 0, 0x5a),
                    tlv(0x0003, 0, 0x5a),
                    tlv(0x0000, 0, 1), tlv(0x0001, 1), tlv(0x0002, 0, 0xff, 0xff, 0xa6),
                ),
            ),
            tlv(0x0003, *utf8z("Use the 2 left lanes")),
        )
        state.accept(Hs5RouteState.LANE_GUIDANCE_UPDATE, lane)
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(
                tlv(0x01, 1), tlv(0x04, *utf8z("Destination")), tlv(0x07, 0, 0, 0x27, 0x10),
                tlv(0x0a, 0, 0, 0, 25), tlv(0x0d, 0, 4),
                tlv(0x10, 0, 3), tlv(0x11, 0, 8), tlv(0x12, 1),
            ),
        )

        val maneuver = state.currentApple()!!
        assertEquals("Destination", maneuver.destination)
        assertEquals(1, maneuver.junctionType)
        assertEquals(listOf(-90, 90), maneuver.junctionAngles)
        assertEquals(90, maneuver.exitAngle)
        assertEquals("Exit 12", maneuver.exitInfo)
        assertEquals(3, maneuver.laneGuidanceIndex)
        assertEquals("Use the 2 left lanes", maneuver.laneGuidanceDescription)
        assertEquals(2, maneuver.lanes.size)
        assertEquals(2, maneuver.lanes[0].status)
        assertEquals(90, maneuver.lanes[0].highlightedAngle)
        assertEquals(listOf(255, -90), maneuver.lanes[1].angles)
    }

    @Test
    fun `shares the canonical parameter list lane decoder across route outputs`() {
        val state = Hs5RouteState()
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            Iap2Messages.buildRaw(Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE) {
                u16(0x01, 4)
                u8(0x03, 13)
                u8(0x08, 0)
            }.payload,
        )

        // This is the actual 0x5204 parameter-list shape: repeated group 2 fields,
        // with packed signed angles and one highlighted direction.
        val lanePayload = Iap2Messages.buildRaw(Hs5RouteState.LANE_GUIDANCE_UPDATE) {
            u16(0, 42)
            u16(1, 3)
            group(2) {
                u16(0, 0)
                u8(1, 2)
                u16List(2, listOf(0, 90))
                i16(3, 90)
            }
            group(2) {
                u16(0, 1)
                u8(1, 1)
                i16(2, -90)
            }
        }.payload
        assertEquals(Hs5RouteChange.NONE, state.accept(Hs5RouteState.LANE_GUIDANCE_UPDATE, lanePayload))

        // The route reference arrives after the lane event; the cached block must be selected.
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            Iap2Messages.buildRaw(Hs5RouteState.ROUTE_GUIDANCE_UPDATE) {
                u8(0x01, 1)
                u32(0x0a, 25)
                u16(0x0d, 4)
                u16(0x10, 3)
                u16(0x11, 2)
                u8(0x12, 1)
            }.payload,
        )

        val maneuver = state.currentApple()!!
        assertEquals(3, maneuver.laneGuidanceIndex)
        assertEquals(2, maneuver.laneGuidanceTotal)
        assertEquals(2, maneuver.lanes.size)
        assertEquals(listOf(0, 90), maneuver.lanes[0].angles)
        assertEquals(90, maneuver.lanes[0].highlightedAngle)
        assertEquals(listOf(-90), maneuver.lanes[1].angles)
        assertEquals(2, maneuver.lanes[0].status)
    }

    @Test
    fun `roundabout exit has blank arrow and exit icon`() {
        val state = Hs5RouteState()
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE, tlvs(tlv(0x01, 0, 0), tlv(0x03, 29)))
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x01, 1), tlv(0x0d, 0, 0)))
        val guidance = state.current()!!
        assertEquals(99, guidance.maneuver)
        assertEquals(26, guidance.gaode)
    }

    @Test
    fun `silent route expires and fresh update restores cached maneuver`() {
        var now = 0L
        val state = populatedState(nanoTime = { now })
        now = 30_000_000_000L
        assertNull(state.current())
        state.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlvs(tlv(0x0a, 0, 0, 0, 20)))
        assertEquals(20, state.current()!!.distanceMeters)
    }

    @Test
    fun `clear prevents a keepalive from restoring guidance`() {
        val state = populatedState()
        state.clear()
        assertNull(state.current())
        assertNull(state.currentApple())
    }

    private fun utf8z(value: String): IntArray =
        (value.toByteArray(Charsets.UTF_8).map { it.toInt() and 0xff } + 0).toIntArray()

    private fun populatedState(
        nanoTime: () -> Long = System::nanoTime,
        keepAcrossNoRoute: Boolean = false,
    ): Hs5RouteState =
        Hs5RouteState(nanoTime, keepAcrossNoRoute = keepAcrossNoRoute).also { state ->
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlvs(tlv(0x01, 0, 1), tlv(0x03, 2), tlv(0x08, 0)),
        )
        state.accept(
            Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlvs(tlv(0x01, 1), tlv(0x0a, 0, 0, 0, 25), tlv(0x0d, 0, 1)),
        )
    }

    private fun tlv(type: Int, vararg value: Int): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        value.forEach(::write)
    }.toByteArray()

    private fun tlv(type: Int, value: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        val length = value.size + 4
        write(length ushr 8)
        write(length)
        write(type ushr 8)
        write(type)
        write(value)
    }.toByteArray()

    private fun tlvs(vararg values: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        values.forEach(::write)
    }.toByteArray()
}
