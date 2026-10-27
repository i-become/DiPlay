package com.shilapi.xcertplay

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max

/** Critically damped measured-value tracking, evaluated from time rather than frame count.
 * Retargets retain forward velocity without overshoot. Missing signals clear immediately. */
internal class ClusterNeedleMotion(private val responseMs: Long = 75L) {
    init { require(responseMs > 0) }
    private var from: Float? = null
    private var target: Float? = null
    private var velocity = 0.0 // units per millisecond at startedAt
    private var startedAt = 0L

    fun update(value: Float?, now: Long, animate: Boolean) {
        val next = value?.takeIf { it.isFinite() }
        if (next == target && animate) return
        val current = valueAt(now)
        val previousVelocity = velocityAt(now)
        target = next
        from = if (animate && next != null) current ?: next else next
        startedAt = now
        val delta = if (next != null && from != null) next.toDouble() - from!! else 0.0
        // Limit inherited speed to a monotone critically damped trajectory. On reversal,
        // brake first instead of briefly sweeping away from the new measurement.
        velocity = when {
            delta > 0 -> previousVelocity.coerceIn(0.0, delta / responseMs)
            delta < 0 -> previousVelocity.coerceIn(delta / responseMs, 0.0)
            else -> 0.0
        }
    }

    fun valueAt(now: Long): Float? {
        val end = target ?: return null
        val start = from ?: return end
        if (!moving(now)) return end
        val t = (now - startedAt).coerceAtLeast(0L).toDouble()
        val offset = start.toDouble() - end
        val b = velocity + offset / responseMs
        return (end + (offset + b * t) * exp(-t / responseMs)).toFloat()
            .coerceIn(minOf(start, end), maxOf(start, end))
    }

    fun moving(now: Long): Boolean {
        val end = target ?: return false
        val start = from ?: return false
        if (start == end) return false
        val t = (now - startedAt).coerceAtLeast(0L).toDouble()
        if (t >= responseMs * 10.0) return false
        val offset = start.toDouble() - end
        val residual = (offset + (velocity + offset / responseMs) * t) * exp(-t / responseMs)
        return abs(residual) > max(abs(offset) * .0005, .001)
    }

    private fun velocityAt(now: Long): Double {
        if (!moving(now)) return 0.0
        val offset = from!!.toDouble() - target!!
        val t = (now - startedAt).coerceAtLeast(0L).toDouble()
        val b = velocity + offset / responseMs
        return (velocity - b * t / responseMs) * exp(-t / responseMs)
    }
}
