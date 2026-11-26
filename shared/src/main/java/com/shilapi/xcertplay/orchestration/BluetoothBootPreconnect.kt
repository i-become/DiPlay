package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothSocket
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** One bounded boot connection, handed to the controller without opening a second RFCOMM link. */
object BluetoothBootPreconnect {
    const val IAP2_SERVICE_UUID = "00000000-deca-fade-deca-deafdecacafe"
    const val SOCKET_CONNECT_TIMEOUT_MILLIS = 15_000L
    // Covers the 35s observed cold-boot radio wait, RFCOMM connection, and a small handoff margin.
    const val BOOT_CONNECTION_DEADLINE_MILLIS = 55_000L
    private val gate = Any()
    private var pending: Pair<String, Attempt>? = null
    private val timer = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "diplay-boot-bt-timeout").apply { isDaemon = true }
    }

    fun schedule(address: String, prepareSocket: () -> BluetoothSocket, log: (String) -> Unit = {}): Boolean =
        synchronized(gate) {
            if (pending?.first.equals(address, ignoreCase = true)) return@synchronized false
            pending?.second?.close()
            val attempt = Attempt(prepareSocket, log)
            pending = address to attempt
            attempt.start()
            true
        }

    /** A different phone must never inherit the previous phone's socket. */
    fun take(address: String): Attempt? = synchronized(gate) {
        val entry = pending ?: return@synchronized null
        pending = null
        if (entry.first.equals(address, ignoreCase = true)) entry.second
        else { entry.second.close(); null }
    }

    fun cancel() = synchronized(gate) {
        pending?.second?.close()
        pending = null
    }

    data class Connection(val socket: BluetoothSocket, val elapsedMillis: Long)

    class Attempt internal constructor(
        prepareSocket: () -> BluetoothSocket,
        private val log: (String) -> Unit,
    ) : Closeable {
        private val lock = Any()
        private var closed = false
        private var socket: BluetoothSocket? = null
        private var deadline: ScheduledFuture<*>? = null
        @Volatile private var stage = "prepare-socket"
        private val createdNanos = System.nanoTime()
        private val task = FutureTask {
            try {
                log("preparing stage=$stage")
                val candidate = prepareSocket()
                synchronized(lock) {
                    if (closed) {
                        runCatching { candidate.close() }
                        throw IOException("Boot Bluetooth connection cancelled")
                    }
                    socket = candidate
                    deadline?.cancel(false)
                    deadline = timer.schedule({ closeWithReason("socket-deadline") }, SOCKET_CONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                }
                stage = "rfcomm-connect"
                log("connecting")
                val started = System.nanoTime()
                candidate.connect()
                val elapsed = (System.nanoTime() - started) / 1_000_000
                stage = "connected"
                log("connected elapsedMs=$elapsed")
                Connection(candidate, elapsed)
            } catch (error: Throwable) {
                synchronized(lock) {
                    socket?.let { runCatching { it.close() } }
                    socket = null
                }
                log("failed failureClass=${error.javaClass.simpleName} stage=$stage totalElapsedMs=${totalElapsedMillis()}")
                throw error
            }
        }

        internal fun start() {
            // Allows the slowest observed Hongqi cold boot radio transition and 15s for RFCOMM,
            // with a small startup margin. The prepare callback exits earlier on normal boots.
            // Also releases a connected socket if the boot UI/controller never starts.
            deadline = timer.schedule({ closeWithReason("boot-deadline") }, BOOT_CONNECTION_DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
            Thread(task, "diplay-boot-bt-connect").apply { isDaemon = true; start() }
        }

        /** Ownership transfers exactly once. Closing this attempt afterwards cannot close the session. */
        fun awaitAndTake(): Connection {
            val started = System.nanoTime()
            log("handoff-wait stage=$stage totalElapsedMs=${totalElapsedMillis()}")
            val connection = try {
                task.get()
            } catch (error: ExecutionException) {
                throw IOException("Boot Bluetooth connection failed", error.cause)
            } catch (error: java.util.concurrent.CancellationException) {
                throw IOException("Boot Bluetooth connection cancelled", error)
            }
            return synchronized(lock) {
                if (closed) throw IOException("Boot Bluetooth connection is no longer available")
                closed = true
                socket = null
                deadline?.cancel(false)
                log("handed-off waitElapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                connection
            }
        }

        private fun totalElapsedMillis() = (System.nanoTime() - createdNanos) / 1_000_000

        override fun close() = closeWithReason("cancel-or-release")

        private fun closeWithReason(reason: String) {
            synchronized(lock) {
                if (closed) return
                closed = true
                deadline?.cancel(false)
                task.cancel(true)
                socket?.let { runCatching { it.close() } }
                socket = null
                log("released reason=$reason stage=$stage totalElapsedMs=${totalElapsedMillis()}")
            }
        }
    }
}
