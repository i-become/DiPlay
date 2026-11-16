package com.shilapi.xcertplay.hud

import android.content.Context
import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/**
 * Nonblocking boundary between phone control messages and the Hongqi head unit.
 *
 * The class name is kept for source compatibility with older settings/controller code. Runtime
 * navigation output is intentionally Hongqi-only: QiSi or the Hongqi Gaode broadcast receiver is
 * the only destination supported by this project.
 */
object Hs5NavigationOutputs {
    /** Start the Hongqi output before a phone reconnects. */
    fun onAppOpened(context: Context) {
        start(context)
    }
    @Volatile private var overlayListener: ((ClusterTurnGuidance?) -> Unit)? = null
    @Volatile private var callListener: ((CarPlayCallCard?) -> Unit)? = null
    private val overlayLock = Any()
    private val callLock = Any()
    private val calls = CarPlayCallState()
    private var publishedOverlay: ClusterTurnGuidance? = null
    @Volatile private var externalGuidance: Hs5GuidanceFrame? = null
    private val overlayRoute = Hs5RouteState(
        staleRouteNs = 120_000_000_000L,
        emptyListHideNs = 8_000_000_000L,
        keepAcrossNoRoute = true,
    )
    private val hs5Guidance = NavigationOutputWorker("xcert-hs5-guidance-out", Hs5GuidanceBridge::clear)

    /** The host reports whether its CarPlay map window is on the Hongqi cluster. */
    fun setClusterMapShown(shown: Boolean) {
        Hs5GuidanceBridge.setClusterMapShown(shown)
    }

    fun setHs5ClusterExitListener(listener: (() -> Unit)?) {
        Hs5GuidanceBridge.setClusterMapCloseRequest(listener)
    }

    fun clearHs5ClusterExitListener(expected: () -> Unit) {
        Hs5GuidanceBridge.clearClusterMapCloseRequest(expected)
    }

    fun hs5ClusterDiagnosticReport(): String = Hs5GuidanceBridge.clusterMapDiagnosticReport()

    fun start(context: android.content.Context) {
        val app = context.applicationContext
        hs5Guidance.start { Hs5GuidanceBridge.initialize(app) }
    }

    fun onFrame(frame: Iap2Frame) {
        externalGuidance = null
        if (frame.messageId == CarPlayCallState.CALL_STATE_UPDATE) {
            val changed = synchronized(callLock) { calls.accept(frame) }
            if (changed) callListener?.invoke(carPlayCall())
            return
        }
        val routeFrame = frame.messageId == Hs5RouteState.ROUTE_GUIDANCE_UPDATE ||
            frame.messageId == Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE
        val laneFrame = frame.messageId == Hs5RouteState.LANE_GUIDANCE_UPDATE
        if (!routeFrame && !laneFrame) return
        val owned = frame // Iap2Frame is immutable and defensively copies its payload.
        // Cache prefetched events and publish late events for the selected junction.
        updateOverlay(owned)
        hs5Guidance.submit { Hs5GuidanceBridge.onFrame(owned) }
    }

    /** Sends already-decoded Carlink navigation through the same vehicle output as CarPlay. */
    fun onExternalNavigation(active: Boolean, icon: Int, distanceMeters: Int, road: String) {
        externalGuidance = if (active) Hs5GuidanceFrame(icon, 0, distanceMeters.coerceAtLeast(0), road) else null
        if (!active) hs5Guidance.clear() else hs5Guidance.submit {
            Hs5GuidanceBridge.onExternalFrame(externalGuidance ?: return@submit)
        }
    }

    fun carPlayCall(): CarPlayCallCard? = synchronized(callLock) { calls.current() }

    fun setCallListener(listener: ((CarPlayCallCard?) -> Unit)?) {
        callListener = listener
        listener?.invoke(carPlayCall())
    }

    /** Live next-turn state for the dashboard overlay. Called from the iAP2 thread. */
    fun setTurnOverlayListener(listener: ((ClusterTurnGuidance?) -> Unit)?) {
        overlayListener = listener
        val next = currentOverlay()
        synchronized(overlayLock) { publishedOverlay = next }
        listener?.invoke(next)
    }

    private fun updateOverlay(frame: Iap2Frame) {
        synchronized(overlayLock) { overlayRoute.accept(frame.messageId, frame.payload) }
        // Lane-only guidance has no maneuver, so the HUD change enum may be NONE.
        // Publish independently; refreshTurnOverlay suppresses unchanged snapshots.
        refreshTurnOverlay()
    }

    /** Called every second while the presentation owner lives, even without incoming frames. */
    fun refreshTurnOverlay() {
        val next = synchronized(overlayLock) {
            val current = currentOverlay()
            if (current == publishedOverlay) return
            publishedOverlay = current
            current
        }
        overlayListener?.invoke(next)
    }

    private fun currentOverlay(): ClusterTurnGuidance? = synchronized(overlayLock) {
        overlayRoute.currentApple()?.let { apple ->
            ClusterTurnGuidance.from(Hs5GuidanceFrame.from(apple)).copy(
                arrivalEpochSeconds = apple.arrivalEpochSeconds,
                remainingSeconds = apple.remainingSeconds,
                remainingMeters = apple.remainingMeters,
            )
        } ?: overlayRoute.currentLaneBlock()?.let { block ->
            ClusterTurnGuidance(0, 0, 0, "", lanes = block.lanes)
        }
    }

    /** Best effort while alive; Android does not guarantee callbacks before force-stop. */
    fun endNow(preserveTurnOverlay: Boolean = false) {
        externalGuidance = null
        hs5Guidance.clear()
        // Only a wireless session replacement retains the card. Explicit controller close
        // and wired disconnect still clear it immediately.
        if (!preserveTurnOverlay) {
            synchronized(overlayLock) { overlayRoute.clear() }
            refreshTurnOverlay()
        } else {
            synchronized(overlayLock) { overlayRoute.clearLaneMetadata() }
            refreshTurnOverlay()
        }
        synchronized(callLock) { calls.clear() }
        callListener?.invoke(null)
    }
}
