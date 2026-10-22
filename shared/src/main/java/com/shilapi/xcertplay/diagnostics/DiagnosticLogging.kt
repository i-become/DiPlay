package com.shilapi.xcertplay.diagnostics

/** One process-wide opt-in for diagnostics. The disabled fast path is a volatile read. */
object DiagnosticLogging {
    @Volatile var enabled: Boolean = false
        private set
    @Volatile var generation: Long = 0
        private set
    private val lock = Any()
    private val stops = linkedSetOf<() -> Unit>()

    fun setEnabled(value: Boolean) {
        val callbacks = synchronized(lock) {
            if (enabled == value) return
            enabled = value
            generation++
            if (value) emptyList() else stops.toList().also { stops.clear() }
        }
        callbacks.forEach { runCatching(it) }
    }

    /** Register only while active. Closing a subscription never starts diagnostic work. */
    fun onDisabled(stop: () -> Unit): AutoCloseable {
        val active = synchronized(lock) {
            if (enabled) { stops.add(stop); true } else false
        }
        if (!active) stop()
        return AutoCloseable { synchronized(lock) { stops.remove(stop) } }
    }

    inline fun record(block: () -> Unit) {
        if (enabled) block()
    }

    inline fun logcat(block: () -> Int): Int = if (enabled) block() else 0

    /** A queued write belongs to the opt-in that created it, even after off/on toggles. */
    fun write(token: Long = generation, block: () -> Unit) {
        if (!enabled) return
        synchronized(lock) {
            if (enabled && token == generation) block()
        }
    }
}
