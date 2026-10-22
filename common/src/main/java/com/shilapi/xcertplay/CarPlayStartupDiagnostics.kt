package com.shilapi.xcertplay

import android.os.SystemClock
import android.util.Log
import android.content.Context
import com.shilapi.xcertplay.orchestration.ConnectionDiagnosticTrace
import java.io.File
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/** First connection of this process. Monotonic times include startup before the session log exists. */
internal object CarPlayStartupDiagnostics {
    enum class Phase(val label: String) {
        PROCESS_READY("process-ready"),
        BLUETOOTH_PRECONNECT_STARTED("bluetooth-preconnect-started"),
        BLUETOOTH_RADIO_READY("bluetooth-radio-ready"),
        BLUETOOTH_CONNECTED("bluetooth-connected"),
        BLUETOOTH_PRECONNECT_FAILED("bluetooth-preconnect-failed"),
        BOOT_RECEIVED("boot-received"),
        HOST_CREATED("host-created"),
        CONTROLLER_STARTED("controller-started"),
        HOST_UI_ATTACHED("host-ui-attached"),
        HOME_UI_ATTACHED("home-ui-attached"),
        SESSION_ACTIVE("session-active"),
        FIRST_VIDEO_FRAME("first-video-frame"),
    }

    private val times = linkedMapOf<Phase, Long>()
    private val events = ArrayDeque<Pair<Long, String>>()
    private val timeline = StartupTimeline()
    private var captureStarted = 0L
    private var observer: BootConnectionObserver? = null
    @Volatile private var store: StartupTraceStore? = null
    @Volatile private var enabled = false
    private val threadCharacters = Regex("[^A-Za-z0-9_.-]")
    private val filtered = java.util.concurrent.atomic.AtomicLong()

    @Synchronized fun initialize(context: Context) {
        if (enabled || !DiagnosticLogging.enabled) return
        captureStarted = SystemClock.elapsedRealtime()
        store = StartupTraceStore(File(context.filesDir, "logs"))
        enabled = true
        DiagnosticLogging.onDisabled(::stop)
        ConnectionDiagnosticTrace.sink = ::capture
        capture("capture-start schema=2 pid=${android.os.Process.myPid()} " +
            "processStartElapsedMs=${android.os.Process.getStartElapsedRealtime()} " +
            "wallEpochMs=${System.currentTimeMillis()} keyEventWindowMs=600000 radioWindowMs=180000")
        observer = BootConnectionObserver(context.applicationContext, ::capture).also { it.start() }
    }

    @Synchronized private fun stop() {
        enabled = false
        ConnectionDiagnosticTrace.sink = null
        observer?.close()
        observer = null
        store?.close()
        store = null
        timeline.clear()
        events.clear()
        times.clear()
        filtered.set(0)
    }

    @Synchronized fun capture(message: String) {
        if (!DiagnosticLogging.enabled || !enabled || SystemClock.elapsedRealtime() - captureStarted > 600_000L) return
        val safe = DiagnosticRedactor.redact(message) ?: run { filtered.incrementAndGet(); return }
        val elapsed = SystemClock.elapsedRealtime()
        val thread = Thread.currentThread().name.replace(threadCharacters, "_").take(48)
        val line = timeline.add("elapsedMs=$elapsed sinceCaptureMs=${elapsed - captureStarted} " +
            "uptimeMs=${SystemClock.uptimeMillis()} thread=$thread", safe)
        store?.append(line)
    }

    fun timelineReport(): String = "capture enabled=$enabled active=${enabled && SystemClock.elapsedRealtime() - captureStarted <= 600_000L} " +
        "ageMs=${SystemClock.elapsedRealtime() - captureStarted} keyEventWindowMs=600000 filtered=${filtered.get()}\n" +
        (store?.status() ?: "traceStorage not_started") + "\n" + timeline.report(store?.current().orEmpty())

    fun previousTraceReport(): String = store?.previous() ?: "Trace storage not started."

    fun recordingPreferenceChanged(context: Context, recording: Boolean) {
        if (!recording) stop()
        else if (com.shilapi.xcertplay.hud.Hs5HeadUnit.isHeadUnit(context)) initialize(context)
    }

    @Synchronized fun clearTrace() {
        timeline.clear()
        events.clear()
        times.clear()
        store?.clear()
        capture("capture-cleared previous startup evidence removed by user")
    }

    /** Includes startup events emitted before the session log is opened. */
    @Synchronized fun event(message: String) {
        if (!DiagnosticLogging.enabled) return
        if (events.size == 64) events.removeFirst()
        events.addLast(SystemClock.elapsedRealtime() to message.take(500))
        capture(message)
    }

    @Synchronized fun eventReport(): String {
        val origin = times[Phase.PROCESS_READY] ?: times.values.firstOrNull() ?: 0L
        return events.joinToString("\n") { (time, message) ->
            "uptimeMs=$time processElapsedMs=${time - origin} $message"
        }.ifEmpty { "No boot Bluetooth events recorded in this process." }
    }

    @Synchronized fun mark(phase: Phase) {
        if (!DiagnosticLogging.enabled) return
        if (times.containsKey(phase)) return
        val now = SystemClock.elapsedRealtime()
        times[phase] = now
        DiagnosticLogging.logcat { Log.i("xcertplay-startup", "${phase.label} elapsedRealtimeMs=$now") }
        capture("milestone=${phase.label}")
    }

    @Synchronized fun report(): String {
        val origin = times[Phase.PROCESS_READY] ?: times.values.firstOrNull()
            ?: return "Process startup timings unavailable."
        return "Process startup timings originElapsedRealtimeMs=$origin " +
            times.entries.joinToString(" ") { (phase, time) -> "${phase.label}=${time - origin}ms" }
    }
}
