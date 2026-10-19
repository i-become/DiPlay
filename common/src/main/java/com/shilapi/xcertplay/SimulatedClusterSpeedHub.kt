package com.shilapi.xcertplay

import java.util.concurrent.CopyOnWriteArrayList

/** Speed for simulated Ultra gauge overlays on developer presentation displays. */
internal object SimulatedClusterSpeedHub {
    private val listeners = CopyOnWriteArrayList<(Int?) -> Unit>()

    @Volatile
    var speedKmh: Int? = null
        private set

    fun publishMetersPerSecond(metersPerSecond: Double) {
        val kmh = (metersPerSecond.coerceAtLeast(0.0) * 3.6).toInt().coerceIn(0, 320)
        if (kmh == speedKmh) return
        speedKmh = kmh
        listeners.forEach { it(kmh) }
    }

    fun observe(changed: (Int?) -> Unit): () -> Unit {
        listeners.add(changed)
        changed(speedKmh)
        return { listeners.remove(changed) }
    }
}
