package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.hud.Hs5RouteState
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/**
 * What CarPlay is doing, at a glance, for widgets and other screens outside CarPlay: the next
 * maneuver and junction lanes from iAP2 route guidance (0x5201/0x5202/0x5204), and the song
 * from NowPlayingUpdate (0x5001).
 * Fed by every CarPlay session, independently of vehicle output settings.
 */
object CarPlayGlance {
    /** [maneuverType] is Apple's RouteGuidanceManeuverType; a route may have no next turn. */
    data class Snapshot(
        val connected: Boolean = false,
        val maneuverType: Int? = null,
        val drivingSide: Int = 0,
        val distanceMeters: Int = 0,
        val road: String = "",
        val arrivalEpochSeconds: Long? = null,
        val remainingSeconds: Long? = null,
        val remainingMeters: Long? = null,
        val song: String? = null,
        val playing: Boolean = false,
        val navigating: Boolean = false,
        val lanes: List<CarPlayLane> = emptyList(),
    ) {
        val laneSourceType: String get() = if (lanes.isEmpty()) "none" else "metadata"
    }

    private val route = Hs5RouteState()
    private var connected = false
    private var last = Snapshot()
    private val laneState = CarPlayLaneState { route.monotonicNow() }
    private val expiryExecutor by lazy {
        Executors.newSingleThreadScheduledExecutor { task -> Thread(task, "carplay-guidance-expiry").apply { isDaemon = true } }
    }
    private var expiryTask: ScheduledFuture<*>? = null
    private var evidenceGeneration = -1L
    private var evidenceStop: AutoCloseable? = null
    private var routePackets = 0L
    private var lanePackets = 0L
    private var rejectedLanePackets = 0L
    private val laneEvidence = ArrayDeque<String>()

    /** Called with each new snapshot, on the thread that changed it. */
    @Volatile var listener: ((Snapshot) -> Unit)? = null
    private val observers = CopyOnWriteArraySet<(Snapshot) -> Unit>()

    /** Independent screens can observe without replacing the navigation widget listener. */
    fun subscribe(observer: (Snapshot) -> Unit): () -> Unit {
        observers.add(observer)
        return { observers.remove(observer) }
    }

    private fun notifyChanged(snapshot: Snapshot) {
        listener?.invoke(snapshot)
        observers.forEach { it(snapshot) }
    }

    /** Refresh time-dependent guidance even when no new metadata frame has arrived. */
    fun snapshot(): Snapshot {
        val (changed, current) = synchronized(this) { publishLocked() to last }
        changed?.let(::notifyChanged)
        return current
    }

    fun onFrame(frame: Iap2Frame) {
        val changed = synchronized(this) {
            var lanesAccepted: Boolean? = null
            when (frame.messageId) {
                Hs5RouteState.ROUTE_GUIDANCE_UPDATE, Hs5RouteState.ROUTE_GUIDANCE_MANEUVER_UPDATE -> {
                    runCatching { route.accept(frame.messageId, frame.payload) }
                    if (frame.messageId == Hs5RouteState.ROUTE_GUIDANCE_UPDATE) {
                        laneState.acceptRoute(frame.payload)
                    }
                }
                CarPlayLaneGuidance.MESSAGE_ID -> {
                    if (!connected) return
                    lanesAccepted = laneState.acceptLanes(frame.payload)
                }
                else -> return
            }
            publishLocked().also {
                if (DiagnosticLogging.enabled && (frame.messageId == 0x5201 || frame.messageId == 0x5204)) {
                    recordLaneEvidence(frame, lanesAccepted)
                }
            }
        }
        changed?.let(::notifyChanged)
    }

    /** Bounded protocol evidence; never capture payloads or format them with logging disabled. */
    private fun recordLaneEvidence(frame: Iap2Frame, accepted: Boolean?) {
        val token = DiagnosticLogging.generation
        DiagnosticLogging.write(token) {
            if (evidenceGeneration != token) {
                clearLaneEvidence()
                evidenceGeneration = token
                evidenceStop = DiagnosticLogging.onDisabled { synchronized(this) { clearLaneEvidence() } }
            }
            if (frame.messageId == 0x5201) routePackets++ else {
                lanePackets++
                if (accepted == false) rejectedLanePackets++
            }
            if (laneEvidence.size == 12) laneEvidence.removeFirst()
            laneEvidence.addLast("id=${frame.messageId.toString(16)} bytes=${frame.payload.size} " +
                "accepted=$accepted navigating=${last.navigating} ${laneState.diagnosticState()}")
        }
    }

    private fun clearLaneEvidence() {
        evidenceStop?.close()
        evidenceStop = null
        evidenceGeneration = -1L
        routePackets = 0
        lanePackets = 0
        rejectedLanePackets = 0
        laneEvidence.clear()
    }

    fun laneDiagnosticReport(): String = synchronized(this) {
        if (!DiagnosticLogging.enabled) return "CarPlay lane diagnostics disabled"
        "routePackets=$routePackets lanePackets=$lanePackets rejectedLanePackets=$rejectedLanePackets " +
            "connected=$connected navigating=${last.navigating} displayedLanes=${last.lanes.size} " +
            "${laneState.diagnosticState()}\n" + laneEvidence.joinToString("\n")
    }

    fun setConnected(next: Boolean) {
        val changed = synchronized(this) {
            if (connected == next) return
            connected = next
            if (next && expiryTask == null) {
                expiryTask = expiryExecutor.scheduleWithFixedDelay({ snapshot() }, 1, 1, TimeUnit.SECONDS)
            }
            if (!next) {
                expiryTask?.cancel(false)
                expiryTask = null
                route.clear()
                laneState.clear()
            }
            publishLocked()
        }
        changed?.let(::notifyChanged)
    }

    private fun publishLocked(): Snapshot? {
        val maneuver = route.currentApple()
        val navigating = connected && (route.isNavigating() || laneState.current().isNotEmpty())
        val summary = if (navigating) route.currentSummary() else null
        val next = Snapshot(
            connected = connected,
            maneuverType = maneuver?.type,
            navigating = navigating,
            drivingSide = maneuver?.drivingSide ?: 0,
            distanceMeters = maneuver?.distanceMeters ?: 0,
            road = maneuver?.road.orEmpty(),
            arrivalEpochSeconds = summary?.arrivalEpochSeconds,
            remainingSeconds = summary?.remainingSeconds,
            remainingMeters = summary?.remainingMeters,
            song = null,
            playing = false,
            lanes = if (connected) laneState.current() else emptyList(),
        )
        if (next == last) return null
        last = next
        return next
    }
}
