package com.shilapi.xcertplay.orchestration

import com.shilapi.xcertplay.airplay.AirPlayListenerIdentity
import com.shilapi.xcertplay.airplay.AirPlayTcpAccepted
import com.shilapi.xcertplay.network.WirelessStartupPolicy

internal class FirstTcpWatchdog(
    val listener: AirPlayListenerIdentity,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val onTimeout: () -> Unit,
    private val nowNanos: () -> Long = System::nanoTime,
    private val timeoutMillis: Long = WirelessStartupPolicy.FIRST_TCP_MILLIS,
    private val log: (String) -> Unit = {},
) {
    @Volatile var terminated = false
        private set
    private var firstStartNanos: Long? = null
    private var firstAcceptNanos: Long? = null
    private var success = false
    private var recoveries = 0
    private var cancelTimer: (() -> Unit)? = null

    @Synchronized fun startSessionSent(sentAtNanos: Long) {
        if (terminated || firstStartNanos != null) return
        firstStartNanos = sentAtNanos
        log("StartSession atNs=$sentAtNanos")
        if (success || firstAcceptNanos != null) return
        val remaining = (timeoutMillis - (nowNanos() - sentAtNanos) / 1_000_000).coerceAtLeast(0)
        cancelTimer = schedule(remaining, ::expire)
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

    /** Only sample association briefly before the first timed recovery; never after real TCP. */
    @Synchronized fun shouldObserveAssociation(): Boolean {
        val ageMillis = recoveryAgeMillis() ?: return false
        return recoveries == 0 && ageMillis in 2_000L until 6_000L
    }

    /** Called only by the authenticated control loop; retries never extend the hard deadline. */
    @Synchronized fun takeRecoveryRequest(clientAssociated: Boolean = false): Boolean {
        val ageMillis = recoveryAgeMillis() ?: return false
        val earlyAssociation = recoveries == 0 && clientAssociated && ageMillis >= 2_000
        if (!earlyAssociation && ageMillis < (recoveries + 1) * 6_000L) return false
        recoveries++
        log("discovery recovery attempt=$recoveries startRequestAgeMs=$ageMillis " +
            "reason=${if (earlyAssociation) "client_associated" else "timer"}")
        return true
    }

    private fun recoveryAgeMillis(): Long? {
        val started = firstStartNanos ?: return null
        if (terminated || success || firstAcceptNanos != null || recoveries >= 2) return null
        val ageMillis = (nowNanos() - started) / 1_000_000
        return ageMillis.takeIf { it < timeoutMillis }
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

    private fun cancel() { cancelTimer?.invoke(); cancelTimer = null }
}
