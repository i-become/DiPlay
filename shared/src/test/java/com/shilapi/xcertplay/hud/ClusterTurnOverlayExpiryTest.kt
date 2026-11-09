package com.shilapi.xcertplay.hud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class ClusterTurnOverlayExpiryTest {
    @Test fun lateLaneOnlyEventPublishesWithoutAManeuverAndExpires() = withRoute { route, advance, events ->
        route.clear()
        Hs5NavigationOutputs.onFrame(com.shilapi.xcertplay.iap2.message.Iap2Messages.buildRaw(0x5201) {
            u16(0x10, 17); u8(0x12, 1)
        })
        assertNull(events.last())
        Hs5NavigationOutputs.onFrame(com.shilapi.xcertplay.iap2.message.Iap2Messages.buildRaw(0x5204) {
            u16(1, 17); group(2) { u16(0, 0); u8(1, 2); i16(2, 0); i16(2, 90); i16(3, 90) }
        })
        assertEquals(0, events.last()!!.icon)
        assertEquals(listOf(0, 90), events.last()!!.lanes.single().directions)
        assertEquals(90, events.last()!!.lanes.single().selectedDirection)
        advance(30_000_000_000L)
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertNull(events.last())
    }
    @Test fun silentRouteClearsThePublishedCardWithoutAnotherPhonePacket() = withRoute { route, advance, events ->
        advance(120_000_000_000L)
        assertNull(route.currentApple())
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertNull(events.last())
        assertEquals(2, events.size)
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertEquals(2, events.size)
    }

    @Test fun emptyManeuverListClearsTheCardAfterItsGracePeriod() = withRoute { route, advance, events ->
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0d))
        advance(8_000_000_000L)
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertNull(events.last())
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0d, 0, 7))
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertEquals(3, events.size)
        assertEquals(150, events.last()!!.distanceMeters)
    }

    @Test fun endingTheSessionKeepsTheTurnCardAcrossTheDrop() = withRoute { route, _, events ->
        Hs5NavigationOutputs.endNow(preserveTurnOverlay = true)
        assertEquals(150, route.currentApple()!!.distanceMeters)
        assertEquals(150, events.last()!!.distanceMeters)
    }

    @Test fun explicitStopStillClearsTheTurnCardImmediately() = withRoute { route, _, events ->
        Hs5NavigationOutputs.endNow()
        assertNull(route.currentApple())
        assertNull(events.last())
        assertEquals(2, events.size)
    }

    @Test fun noRoutePacketsDoNotExtendRetainedGuidanceOrReplaceItsRoad() = withRoute { route, advance, events ->
        val before = route.currentApple()
        advance(119_000_000_000L)
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x01, 0) + tlv(0x03, 88))
        assertEquals(before, route.currentApple())
        advance(120_000_000_000L)
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x01, 0))
        Hs5NavigationOutputs.refreshTurnOverlay()
        assertNull(route.currentApple())
        assertNull(events.last())
        route.clear() // Explicit session stop, rather than guessing state two means arrival.
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE, tlv(0x0a, 0, 0, 0, 5))
        assertNull(route.currentApple())
    }

    private fun withRoute(test: (Hs5RouteState, (Long) -> Unit, MutableList<ClusterTurnGuidance?>) -> Unit) {
        var now = 0L
        val field = Hs5NavigationOutputs::class.java.getDeclaredField("overlayRoute").apply { isAccessible = true }
        val route = field.get(Hs5NavigationOutputs) as Hs5RouteState
        val clock = Hs5RouteState::class.java.getDeclaredField("nanoTime").apply { isAccessible = true }
        val previousClock = clock.get(route)
        route.clear()
        clock.set(route, { now })
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE,
            tlv(0x01, 0, 7) + tlv(0x03, 1))
        route.accept(Hs5RouteState.ROUTE_GUIDANCE_UPDATE,
            tlv(0x01, 1) + tlv(0x0a, 0, 0, 0, 150) + tlv(0x0d, 0, 7))
        val events = mutableListOf<ClusterTurnGuidance?>()
        try {
            Hs5NavigationOutputs.setTurnOverlayListener { events += it }
            test(route, { now = it }, events)
        } finally {
            Hs5NavigationOutputs.setTurnOverlayListener(null)
            route.clear()
            clock.set(route, previousClock)
        }
    }

    private fun tlv(type: Int, vararg bytes: Int): ByteArray =
        byteArrayOf(0, (bytes.size + 4).toByte(), (type ushr 8).toByte(), type.toByte()) +
            bytes.map(Int::toByte).toByteArray()
}
