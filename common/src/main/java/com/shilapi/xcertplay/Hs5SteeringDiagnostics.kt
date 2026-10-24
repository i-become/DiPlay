package com.shilapi.xcertplay

import android.os.SystemClock
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.util.ArrayDeque

/** Bounded, in-memory evidence. No timer, I/O or event strings while recording is disabled. */
internal object Hs5SteeringDiagnostics {
    private val lock = Any()
    private var events: ArrayDeque<String>? = null
    private var stop: AutoCloseable? = null

    fun record(message: String) = synchronized(lock) {
        if (stop == null) {
            stop = DiagnosticLogging.onDisabled {
                synchronized(lock) {
                    events = null
                    stop = null
                }
            }
        }
        val buffer = events ?: ArrayDeque<String>().also { events = it }
        if (buffer.size == 128) buffer.removeFirst()
        buffer.addLast("uptime=${SystemClock.uptimeMillis()} $message")
    }

    fun report(): String = synchronized(lock) { events?.joinToString("\n") ?: "no recorded Qisi key events" }
}
