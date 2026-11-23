package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.io.File

/** Android 9 has no historical process-exit API. Keep one bounded Java crash stack,
 * only while diagnostic recording is enabled, and preserve Android's crash handler.
 * Exception messages are deliberately excluded (they can contain phone/file data).
 */
internal object CrashDiagnostics {
    private const val NAME = "last-java-crash.txt"
    private const val MAX_CHARS = 16 * 1024
    private var installed = false

    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val file = File(DiPlaySessionLogFiles.directory(context), NAME)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                DiagnosticLogging.write {
                    runCatching {
                        file.parentFile?.mkdirs()
                        file.writeText(format(thread.name, error))
                    }
                }
            } finally { previous?.uncaughtException(thread, error) }
        }
    }

    internal fun format(thread: String, error: Throwable): String = buildString {
        appendLine("Java crash wallEpochMs=${System.currentTimeMillis()} thread=${thread.take(80)}")
        val runtime = Runtime.getRuntime()
        appendLine("heapUsedBytes=${runtime.totalMemory() - runtime.freeMemory()} heapMaxBytes=${runtime.maxMemory()}")
        var cause: Throwable? = error
        val seen = mutableSetOf<Throwable>()
        repeat(4) {
            val current = cause ?: return@repeat
            if (!seen.add(current)) return@repeat
            appendLine(current.javaClass.name)
            current.stackTrace.take(48).forEach { appendLine("  at $it") }
            cause = current.cause
        }
    }.take(MAX_CHARS)

    fun report(context: Context): String {
        if (!DiagnosticLogging.enabled) return "Java crash recording disabled."
        val file = File(DiPlaySessionLogFiles.directory(context), NAME)
        return runCatching {
            if (file.isFile) file.bufferedReader().use { reader ->
                val buffer = CharArray(MAX_CHARS)
                val count = reader.read(buffer)
                if (count > 0) String(buffer, 0, count) else "No retained Java crash."
            } else "No retained Java crash."
        }.getOrElse { "Java crash record unavailable." }
    }

    fun clear(context: Context) { runCatching { File(DiPlaySessionLogFiles.directory(context), NAME).delete() } }
}
