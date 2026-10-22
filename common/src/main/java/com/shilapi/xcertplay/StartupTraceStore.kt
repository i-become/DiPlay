package com.shilapi.xcertplay

import java.io.File
import java.io.RandomAccessFile
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** Only the daemon writer touches disk. A UI recreation cannot truncate the startup trace. */
internal class StartupTraceStore(private val directory: File) {
    private val recordingToken = DiagnosticLogging.generation
    private data class Entry(val generation: Long, val line: String?)
    @Volatile private var generation = 0L
    @Volatile private var writtenGeneration = -1L
    @Volatile private var failures = 0L
    @Volatile private var full = false
    private val writer = BoundedDiagnosticWriter<Entry>(maxPending = 1024) { entry ->
        try {
            DiagnosticLogging.write(recordingToken) { if (entry.generation == generation) {
                val current = File(directory, CURRENT)
                if (writtenGeneration != entry.generation) {
                    directory.mkdirs()
                    if (entry.generation == 0L) {
                        if (current.isFile) current.copyTo(File(directory, PREVIOUS), overwrite = true)
                    } else File(directory, PREVIOUS).delete()
                    current.writeText("")
                    writtenGeneration = entry.generation
                    full = false
                }
                entry.line?.let { line ->
                    if (!full && current.length() + line.toByteArray(Charsets.UTF_8).size + 1 <= MAX_BYTES - 128) {
                        current.appendText("$line\n")
                    } else if (!full) {
                        current.appendText("traceStorage limitReached=true; subsequent disk events omitted\n")
                        full = true
                    }
                }
            } }
        } catch (_: Exception) { failures++ }
    }

    @Synchronized fun append(line: String) {
        if (DiagnosticLogging.enabled && recordingToken == DiagnosticLogging.generation) writer.enqueue(Entry(generation, line))
    }

    @Synchronized fun clear() { generation++; writer.enqueue(Entry(generation, null)) }

    fun status(): String = "traceStorage queued=${writer.pendingCount} queueDropped=${writer.droppedCount} " +
        "writeFailures=$failures limitReached=$full"

    /** Merge this snapshot with memory on export; never flush or wait on the UI thread. */
    fun current(): String = runCatching {
        val snapshotGeneration = generation
        if (writtenGeneration != snapshotGeneration) return@runCatching ""
        val text = read(File(directory, CURRENT))
        if (generation == snapshotGeneration) text else ""
    }.getOrDefault("")

    fun previous(): String = runCatching {
        if (generation > 0 && writtenGeneration != generation) return@runCatching "Trace clear pending."
        val file = File(directory, PREVIOUS)
        if (!file.isFile) "No previous process trace." else read(file)
    }.getOrElse { "previousTrace unavailable failureClass=${it.javaClass.simpleName}" }

    private fun read(file: File): String = RandomAccessFile(file, "r").use {
        val bytes = ByteArray(it.length().coerceAtMost(MAX_BYTES).toInt())
        it.readFully(bytes)
        String(bytes, Charsets.UTF_8)
    }

    internal fun awaitIdle(timeoutMillis: Long): Boolean = writer.awaitIdle(timeoutMillis)
    internal fun close() { writer.cancel() }

    companion object {
        const val CURRENT = "startup-current.log"
        const val PREVIOUS = "startup-previous.log"
        private const val MAX_BYTES = 2 * 1024 * 1024L
    }
}
