package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceView
import android.view.SurfaceHolder
import android.widget.FrameLayout
import android.view.View
import android.view.WindowManager
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Hongqi 仪表飞屏 as its own activity on the cluster display, like the stock 九州之鹰 CarPlay.
 *
 * On the Android 9 head unit the newest activity owns input focus, so the steering-wheel back
 * key reaches this window instead of DiPlay's centre screen and closes the fly screen.
 */
class Hs5ClusterActivity : Activity() {
    private var dashboard: ClusterDashboardView? = null
    private var originalOverlay: OriginalClusterOverlayView? = null
    private val foreground = Hs5ClusterForeground()
    internal val windowOutput: String get() = foreground.mode

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (!Hs5ClusterActivityOutput.attach(this, intent.getStringExtra(EXTRA_TOKEN))) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.setFormat(PixelFormat.TRANSLUCENT)
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val content: View = if (AirPlayPersistence.loadClusterStyle(this).customDashboard) {
            ClusterDashboardView(this) {
                Hs5ClusterActivityOutput.surfaceChanged(this, it)
            }.also { dashboard = it }
        } else {
            FrameLayout(this).apply {
                setBackgroundColor(Color.BLACK)
                addView(SurfaceView(this@Hs5ClusterActivity).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            Hs5ClusterActivityOutput.surfaceChanged(this@Hs5ClusterActivity, holder.surface)
                        }
                        override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            Hs5ClusterActivityOutput.surfaceChanged(this@Hs5ClusterActivity, null)
                        }
                    })
                }, FrameLayout.LayoutParams(-1, -1))
                addView(OriginalClusterOverlayView(this@Hs5ClusterActivity).also { originalOverlay = it },
                    FrameLayout.LayoutParams(-1, -1))
            }
        }
        @Suppress("DEPRECATION")
        val targetDisplay = windowManager.defaultDisplay
        if (dashboard != null && targetDisplay != null && foreground.attach(this, targetDisplay, content, ::dispatchKeyEvent)) {
            setContentView(View(this).apply { setBackgroundColor(Color.TRANSPARENT) })
        } else setContentView(content)
        applyOutputState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (!Hs5ClusterActivityOutput.attach(this, intent.getStringExtra(EXTRA_TOKEN))) finish()
        else applyOutputState()
    }

    override fun onResume() {
        super.onResume()
        originalOverlay?.setOverlayVisible(true)
        Hs5ClusterActivityOutput.focusChanged(this, resumed = true)
    }

    override fun onPause() {
        Hs5ClusterActivityOutput.focusChanged(this, resumed = false)
        if (isFinishing) Hs5ClusterActivityOutput.closedOutside(this, "finished by system")
        super.onPause()
    }

    override fun onStop() {
        originalOverlay?.setOverlayVisible(false)
        foreground.close()
        // Another window covering the cluster means the head unit took the fly screen back.
        if (!isFinishing) Hs5ClusterActivityOutput.closedOutside(this, "covered on cluster")
        super.onStop()
    }

    override fun onDestroy() {
        foreground.close()
        Hs5ClusterActivityOutput.closedOutside(this, "destroyed")
        super.onDestroy()
    }

    override fun finish() {
        foreground.close()
        super.finish()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode in CLOSE_KEYS) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) {
                val reason = "key ${event.keyCode}"
                if (event.keyCode != KeyEvent.KEYCODE_BACK ||
                    !CarPlayHostSettingsBridge.closeFromAndroidBack(reason)) closeByUser(reason)
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun closeByUser(reason: String) {
        Hs5ClusterActivityOutput.closedOutside(this, reason)
        finish()
        overridePendingTransition(0, 0)
    }

    internal fun displayId(): Int {
        @Suppress("DEPRECATION")
        return windowManager.defaultDisplay?.displayId ?: -1
    }

    internal fun applyOutputState() {
        dashboard?.setStreamActive(Hs5ClusterActivityOutput.streamActive)
        dashboard?.setNightMode(Hs5ClusterActivityOutput.turnCard.night)
    }

    internal companion object {
        const val EXTRA_TOKEN = "hs5_cluster_token"
        val CLOSE_KEYS = setOf(KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_ESCAPE)
    }
}

/** Main-thread handoff between [CarPlayHostActivity] and the current [Hs5ClusterActivity]. */
internal object Hs5ClusterActivityOutput {
    interface Host {
        fun onClusterWindowSurface(surface: Surface?)
        fun onClusterWindowClosed(reason: String)
    }

    data class TurnCard(
        val guidance: ClusterTurnGuidance? = null,
        val xPercent: Int = 50,
        val yPercent: Int = 50,
        val sizePercent: Int = 100,
        val opacityPercent: Int = 100,
        val night: Boolean = false,
    )

    private const val TAG = "DiPlay-Hs5Cluster"
    // Android 9 pauses the centre screen before the cluster window reports onResume.
    private const val LAUNCH_FOCUS_GRACE_MS = 4_000L
    private const val MAX_EVENTS = 40

    private var token: String? = null
    private var host = WeakReference<Host>(null)
    private var activity = WeakReference<Hs5ClusterActivity>(null)
    private var launchedAt = 0L
    private var resumed = false
    var streamActive = false
        private set
    var turnCard = TurnCard()
        private set
    var outputSurface: Surface? = null
        private set
    private val events = ArrayDeque<String>()

    fun enabled(context: Context): Boolean =
        Hs5HeadUnit.isHeadUnit(context) &&
            AirPlayPersistence.loadHs5ClusterActivityWindow(context) &&
            !DiLink51ClusterLayout.supported() &&
            !AdbClusterRouter.enabled(context)

    fun isOpen(): Boolean {
        if (token == null) return false
        val window = activity.get()
        if (window != null) return !window.isFinishing && !window.isDestroyed
        return SystemClock.uptimeMillis() - launchedAt < LAUNCH_FOCUS_GRACE_MS
    }

    /** True while the cluster window holds, or is about to take, the head unit's input focus. */
    fun ownsFocus(): Boolean =
        token != null && (resumed || SystemClock.uptimeMillis() - launchedAt < LAUNCH_FOCUS_GRACE_MS)

    fun open(launcher: Activity, display: Display, owner: Host): Boolean {
        host = WeakReference(owner)
        if (isOpen()) return true
        val next = UUID.randomUUID().toString()
        token = next
        launchedAt = SystemClock.uptimeMillis()
        val intent = Intent(launcher, Hs5ClusterActivity::class.java)
            .putExtra(Hs5ClusterActivity.EXTRA_TOKEN, next)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
        val options = ActivityOptions.makeBasic().setLaunchDisplayId(display.displayId)
        return runCatching {
            launcher.startActivity(intent, options.toBundle())
            record("open display=${display.displayId}:${display.name}")
            true
        }.getOrElse { error ->
            record("open failed ${error.javaClass.simpleName}: ${error.message}")
            reset()
            false
        }
    }

    /** DiPlay closes the window itself; the host already stopped drawing into its surface. */
    fun close(reason: String) {
        if (token == null && activity.get() == null) return
        val window = activity.get()
        record("close ($reason)")
        reset()
        window?.takeIf { !it.isFinishing }?.finish()
    }

    /** A key outside the window (the accessibility filter) closes it; DiPlay turns 飞屏 off. */
    fun closeByDriver(reason: String): Boolean {
        if (token == null) return false
        val owner = host.get()
        val window = activity.get()
        record("closed by driver ($reason)")
        reset()
        window?.takeIf { !it.isFinishing }?.finish()
        owner?.onClusterWindowClosed(reason)
        return true
    }

    fun setStreamActive(active: Boolean) {
        if (streamActive == active) return
        streamActive = active
        activity.get()?.applyOutputState()
    }

    fun setTurnCard(card: TurnCard) {
        if (turnCard == card) return
        turnCard = card
        activity.get()?.applyOutputState()
    }

    internal fun attach(window: Hs5ClusterActivity, launchToken: String?): Boolean {
        if (launchToken == null || launchToken != token) return false
        activity.get()?.takeIf { it !== window && !it.isFinishing }?.finish()
        activity = WeakReference(window)
        record("attached display=${window.displayId()}")
        return true
    }

    internal fun surfaceChanged(window: Hs5ClusterActivity, surface: Surface?) {
        if (!current(window)) return
        outputSurface = surface
        record(if (surface == null) "surface destroyed" else "surface created")
        host.get()?.onClusterWindowSurface(surface)
    }

    internal fun focusChanged(window: Hs5ClusterActivity, resumed: Boolean) {
        if (!current(window)) return
        this.resumed = resumed
        if (resumed) launchedAt = 0L
        record(if (resumed) "focused" else "focus lost")
    }

    /** The driver or the head unit closed the window; DiPlay turns 飞屏 off. */
    internal fun closedOutside(window: Hs5ClusterActivity, reason: String) {
        if (!current(window)) return
        val owner = host.get()
        record("closed outside DiPlay ($reason)")
        reset()
        owner?.onClusterWindowClosed(reason)
    }

    fun diagnosticReport(): String = synchronized(events) {
        buildString {
            appendLine("open=${isOpen()} focused=$resumed streamActive=$streamActive surface=${outputSurface != null} window=${activity.get()?.windowOutput}")
            if (events.isEmpty()) append("no cluster window events recorded") else append(events.joinToString("\n"))
        }
    }

    private fun current(window: Hs5ClusterActivity): Boolean = token != null && activity.get() === window

    private fun reset() {
        token = null
        host = WeakReference(null)
        activity = WeakReference(null)
        launchedAt = 0L
        resumed = false
        outputSurface = null
    }

    private fun record(message: String) {
        if (!DiagnosticLogging.enabled) return
        DiagnosticLogging.logcat { Log.i(TAG, message) }
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $message"
        synchronized(events) {
            events.addLast(line)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }
}
