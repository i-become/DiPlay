package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.AirPlayListenerIdentity
import com.shilapi.xcertplay.airplay.AirPlayTcpAccepted
internal class FirstTcpWatchdog(
    val listener: AirPlayListenerIdentity,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onTimeout: () -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    private val timeoutMillis: Long,
    private val log: (String) -> Unit = {},
    private val waitProgressIntervalMillis: Long = 3_000L,
) {
    @Volatile var terminated = false
        private set
    private var firstStartNanos: Long? = null
    private var firstAcceptNanos: Long? = null
    private var success = false
    private var cancelTimer: (() -> Unit)? = null
    private var cancelWaitProgress: (() -> Unit)? = null

    @Synchronized fun startSessionSent(sentAtNanos: Long) {
        if (terminated || firstStartNanos != null) return
        firstStartNanos = sentAtNanos
        log("StartSession atNs=$sentAtNanos")
        if (success || firstAcceptNanos != null) return
        val remaining = (timeoutMillis - (nowNanos() - sentAtNanos) / 1_000_000).coerceAtLeast(0)
        cancelTimer = schedule(remaining, ::expire)
        scheduleWaitProgressTick()
    }

    @Synchronized fun accepted(event: AirPlayTcpAccepted): Boolean {
        if (terminated || event.listener !== listener || event.internal) return false
        if (firstAcceptNanos == null) {
            firstAcceptNanos = event.acceptedAtNanos
            log("first TCP atNs=${event.acceptedAtNanos}")
        }
        cancel()
        return true
    }

    @Synchronized fun sessionEstablished(): Boolean {
        if (terminated) return false
        if (!success) log("session established atNs=${nowNanos()}")
        success = true
        cancel()
        return true
    }

    @Synchronized fun terminate(): Boolean {
        if (terminated) return false
        terminated = true
        cancel()
        return true
    }

    private fun expire() {
        synchronized(this) {
            if (terminated || success || firstAcceptNanos != null || firstStartNanos == null) return
            terminated = true
            cancelTimer = null
            log("first TCP timeout atNs=${nowNanos()} startNs=$firstStartNanos")
        }
        onTimeout()
    }

    private fun scheduleWaitProgressTick() {
        if (waitProgressIntervalMillis <= 0) return
        cancelWaitProgress = schedule(waitProgressIntervalMillis, ::emitWaitProgress)
    }

    private fun emitWaitProgress() {
        synchronized(this) {
            if (terminated || success || firstAcceptNanos != null || firstStartNanos == null) return
            val elapsedMs = (nowNanos() - firstStartNanos!!) / 1_000_000
            log("PROGRESS wait-first-tcp elapsedMs=$elapsedMs")
            scheduleWaitProgressTick()
        }
    }

    private fun cancel() {
        cancelTimer?.invoke()
        cancelTimer = null
        cancelWaitProgress?.invoke()
        cancelWaitProgress = null
    }
}
