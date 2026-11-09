package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.body.Iap2BodyBuilder
import com.shilapi.xcertplay.iap2.message.Iap2Messages
import com.shilapi.xcertplay.hud.Hs5RouteState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CarPlayGlanceTest {
    @Test fun screensSubscribeIndependentlyOfWidgetAndUnsubscribeCleanly() {
        val widget = mutableListOf<CarPlayGlance.Snapshot>()
        val dashboard = mutableListOf<CarPlayGlance.Snapshot>()
        CarPlayGlance.listener = { widget += it }
        val release = CarPlayGlance.subscribe { dashboard += it }
        try {
            CarPlayGlance.setConnected(true)
            assertEquals(1, widget.size)
            assertEquals(widget, dashboard)
            release()
            CarPlayGlance.setConnected(false)
            assertEquals(2, widget.size)
            assertEquals(1, dashboard.size)
        } finally { release() }
    }

    private fun frame(id: Int, block: Iap2BodyBuilder.() -> Unit) = Iap2Messages.buildRaw(id, block)

    @Before
    fun reset() {
        com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false)
        CarPlayGlance.listener = null
        CarPlayGlance.setConnected(true)
        CarPlayGlance.setConnected(false)
    }

    @After
    fun clear() = reset()

    @Test fun laneDiagnosticsAreBoundedOptInAndClearedWhenDisabled() {
        val logging = com.shilapi.xcertplay.diagnostics.DiagnosticLogging
        CarPlayGlance.setConnected(true)
        val lane = frame(0x5204) {
            u16(0, 42); u16(1, 0)
            group(2) { u16(0, 0); u8(1, 2); i16(2, 0); i16(2, 90); i16(3, 90) }
        }
        CarPlayGlance.onFrame(lane)
        assertEquals("CarPlay lane diagnostics disabled", CarPlayGlance.laneDiagnosticReport())
        logging.setEnabled(true)
        repeat(40) { CarPlayGlance.onFrame(lane) }
        CarPlayGlance.onFrame(frame(0x5204) { u16(0, 41); u16(1, 0) })
        val report = CarPlayGlance.laneDiagnosticReport()
        assertTrue(report.contains("lanePackets=41 rejectedLanePackets=1"))
        assertEquals(12, report.lineSequence().count { it.startsWith("id=") })
        logging.setEnabled(false)
        logging.setEnabled(true)
        val cleared = CarPlayGlance.laneDiagnosticReport()
        assertTrue(cleared.contains("lanePackets=0 rejectedLanePackets=0"))
        assertFalse(cleared.contains("hex128="))
    }

    @Test
    fun followsTheNextTurnAndTheSong() {
        val seen = mutableListOf<CarPlayGlance.Snapshot>()
        CarPlayGlance.listener = { seen += it }
        CarPlayGlance.setConnected(true)

        // Maneuver 7 is a right turn onto Main Street; the route update makes it the next one.
        CarPlayGlance.onFrame(frame(0x5202) { u16(1, 7); u8(3, 2); string(4, "Main Street") })
        CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u32(0x0a, 350); u16List(0x0d, listOf(7)) })
        CarPlayGlance.onFrame(frame(0x5001) {
            group(0) { string(1, "Numb"); string(12, "Linkin Park") }
            group(1) { u8(0, 1) }
        })

        val now = CarPlayGlance.snapshot()
        assertTrue(now.connected)
        assertEquals(2, now.maneuverType)
        assertEquals(350, now.distanceMeters)
        assertEquals("Main Street", now.road)
        assertEquals("Numb — Linkin Park", now.song)
        assertTrue(now.playing)
        assertEquals(now, seen.last())
    }

    @Test
    fun publishesManeuverDescriptionAsRoadFallbackForClusterStyles() {
        CarPlayGlance.setConnected(true)
        CarPlayGlance.onFrame(frame(0x5202) {
            u16(1, 3); string(2, "Continue onto Central Avenue"); u8(3, 2)
        })
        CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u16List(0x0d, listOf(3)) })

        assertEquals("Continue onto Central Avenue", CarPlayGlance.snapshot().road)
    }

    @Test
    fun routeEndAndSessionEnd() {
        CarPlayGlance.setConnected(true)
        CarPlayGlance.onFrame(frame(0x5202) { u16(1, 1); u8(3, 1) })
        CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u32(0x0a, 90); u16List(0x0d, listOf(1)) })
        CarPlayGlance.onFrame(frame(0x5001) { group(0) { string(1, "Podcast") } })

        // Arrived: no turn any more, the song stays.
        CarPlayGlance.onFrame(frame(0x5201) { u8(1, 2) })
        assertNull(CarPlayGlance.snapshot().maneuverType)
        assertEquals("Podcast", CarPlayGlance.snapshot().song)

        CarPlayGlance.setConnected(false)
        assertEquals(CarPlayGlance.Snapshot(), CarPlayGlance.snapshot())
    }

    @Test
    fun expiredGuidanceClearsWithoutAnotherFrameAndNotifiesTheWidget() {
        withRouteClock { advance ->
            CarPlayGlance.setConnected(true)
            CarPlayGlance.onFrame(frame(0x5202) { u16(1, 1); u8(3, 2) })
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u16List(0x0d, listOf(1)) })
            assertEquals(2, CarPlayGlance.snapshot().maneuverType)
            val seen = mutableListOf<CarPlayGlance.Snapshot>()
            CarPlayGlance.listener = { seen += it }
            advance(30_000_000_000L)
            assertNull(CarPlayGlance.snapshot().maneuverType)
            assertNull(seen.single().maneuverType)
            assertTrue(CarPlayGlance.snapshot().connected)
        }
    }

    @Test
    fun aPersistentlyEmptyManeuverListExpiresAfterTheGracePeriod() {
        withRouteClock { advance ->
            CarPlayGlance.setConnected(true)
            CarPlayGlance.onFrame(frame(0x5202) { u16(1, 1); u8(3, 2) })
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u16List(0x0d, listOf(1)) })
            CarPlayGlance.onFrame(frame(0x5201) { u16List(0x0d, emptyList()) })
            advance(2_000_000_000L)
            assertEquals(2, CarPlayGlance.snapshot().maneuverType)
            advance(1_000_000_000L)
            assertNull(CarPlayGlance.snapshot().maneuverType)
        }
    }

    @Test
    fun anEmptySongTitleClearsTheWidgetSong() {
        CarPlayGlance.setConnected(true)
        CarPlayGlance.onFrame(frame(0x5001) { group(0) { string(1, "Previous song") } })
        CarPlayGlance.onFrame(frame(0x5001) { group(0) { string(1, "") } })
        assertNull(CarPlayGlance.snapshot().song)
    }

    // Inject the route parser's monotonic clock rather than waiting 30 seconds in each test.
    private fun withRouteClock(test: ((Long) -> Unit) -> Unit) {
        val route = CarPlayGlance.javaClass.getDeclaredField("route").apply { isAccessible = true }
            .get(CarPlayGlance) as Hs5RouteState
        val clock = route.javaClass.getDeclaredField("nanoTime").apply { isAccessible = true }
        val original = clock.get(route)
        var now = 1_000_000_000L
        try {
            clock.set(route, { now })
            test { now += it }
        } finally {
            clock.set(route, original)
        }
    }

    @Test
    fun navigationPresenceDoesNotDependOnNextTurnDetails() {
        withRouteClock { advance ->
            CarPlayGlance.setConnected(true)
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u16List(0x0d, emptyList()) })
            assertTrue(CarPlayGlance.snapshot().navigating)
            assertNull(CarPlayGlance.snapshot().maneuverType)
            CarPlayGlance.onFrame(frame(0x5201) { u16List(0x0d, emptyList()) })
            advance(4_000_000_000L)
            assertTrue(CarPlayGlance.snapshot().navigating)
            assertNull(CarPlayGlance.snapshot().maneuverType)
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 0) })
            assertEquals(false, CarPlayGlance.snapshot().navigating)
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1) })
            assertTrue(CarPlayGlance.snapshot().navigating)
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 2) })
            assertEquals(false, CarPlayGlance.snapshot().navigating)
            CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1) })
            advance(30_000_000_000L)
            assertEquals(false, CarPlayGlance.snapshot().navigating)
            CarPlayGlance.setConnected(false)
            assertEquals(CarPlayGlance.Snapshot(), CarPlayGlance.snapshot())
        }
    }

    @Test
    fun otherMessagesChangeNothing() {
        CarPlayGlance.setConnected(true)
        val before = CarPlayGlance.snapshot()
        var calls = 0
        CarPlayGlance.listener = { calls++ }

        CarPlayGlance.onFrame(frame(0x4E09) { u8(1, 1) })

        assertEquals(before, CarPlayGlance.snapshot())
        assertEquals(0, calls)
    }

    @Test fun lanesAndRouteTotalsPublishWithoutNextTurnAndClearOnRouteEndOrTimeout() {
        withRouteClock { advance ->
            CarPlayGlance.setConnected(true)
            val update = frame(0x5201) {
                u8(1, 1); u32(7, 12800); u64(6, 1080); u64(5, 1792904280)
                u16(0x10, 0); u8(0x12, 1)
            }
            val lane = frame(0x5204) {
                u16(0, 42); u16(1, 0)
                group(2) { u16(0, 0); u8(1, 2); i16(2, 90) }
            }
            CarPlayGlance.onFrame(update)
            CarPlayGlance.onFrame(lane)
            val now = CarPlayGlance.snapshot()
            assertNull(now.maneuverType)
            assertEquals(1, now.lanes.size)
            assertEquals(12800L, now.remainingMeters)
            assertEquals(1080L, now.remainingSeconds)
            assertEquals(1792904280L, now.arrivalEpochSeconds)
            for (end in listOf(0, 2)) {
                CarPlayGlance.onFrame(frame(0x5201) { u8(1, end) })
                val ended = CarPlayGlance.snapshot()
                assertTrue(ended.lanes.isEmpty())
                assertNull(ended.remainingMeters); assertNull(ended.remainingSeconds); assertNull(ended.arrivalEpochSeconds)
                CarPlayGlance.onFrame(update); CarPlayGlance.onFrame(lane)
            }
            advance(30_000_000_000L)
            val stale = CarPlayGlance.snapshot()
            assertTrue(stale.lanes.isEmpty())
            assertNull(stale.remainingMeters); assertNull(stale.remainingSeconds); assertNull(stale.arrivalEpochSeconds)
        }
    }

    @Test fun lanesPublishWithTheCurrentRouteAndDisappearOnArrivalExpiryOrDisconnect() {
        withRouteClock { advance ->
            CarPlayGlance.setConnected(true)
            CarPlayGlance.onFrame(frame(0x5202) { u16(1, 1); u8(3, 2) })
            CarPlayGlance.onFrame(frame(0x5201) {
                u8(1, 1); u16List(0x0d, listOf(1)); u16(0x10, 0); u8(0x12, 1)
            })
            val seen = mutableListOf<CarPlayGlance.Snapshot>()
            val release = CarPlayGlance.subscribe { seen += it }
            try {
                val lane = frame(0x5204) {
                    u16(0, 42); u16(1, 0)
                    group(2) { u16(0, 0); u8(1, 2); i16(2, 90) }
                }
                CarPlayGlance.onFrame(lane)
                assertEquals(90, seen.last().lanes.single().direction)
                advance(30_000_000_000L)
                assertTrue(CarPlayGlance.snapshot().lanes.isEmpty())
                CarPlayGlance.onFrame(frame(0x5201) { u8(1, 1); u16(0x10, 0); u8(0x12, 1) })
                assertTrue(CarPlayGlance.snapshot().lanes.isEmpty())
                CarPlayGlance.onFrame(lane)
                assertEquals(1, CarPlayGlance.snapshot().lanes.size)
                CarPlayGlance.onFrame(frame(0x5201) { u8(1, 2) })
                assertTrue(CarPlayGlance.snapshot().lanes.isEmpty())
                CarPlayGlance.setConnected(false)
                CarPlayGlance.onFrame(lane)
                assertEquals(CarPlayGlance.Snapshot(), CarPlayGlance.snapshot())
            } finally { release() }
        }
    }
}
