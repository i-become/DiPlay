package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.shilapi.xcertplay.host.R
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
    private var waiting: View? = null
    private var turnCard: ClusterTurnCardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        if (!Hs5ClusterActivityOutput.attach(this, intent.getStringExtra(EXTRA_TOKEN))) {
            finish()
            return
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        val dark = resolveAppNightNow()
        val placeholder = if (dark) Color.BLACK else Color.rgb(233, 238, 246)
        val root = FrameLayout(this).apply { setBackgroundColor(placeholder) }
        val surfaceView = SurfaceView(this)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                Hs5ClusterActivityOutput.surfaceChanged(this@Hs5ClusterActivity, holder.surface)
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                DiagnosticLogging.logcat { Log.i(ClusterMapPresentation.TAG, "hs5 cluster window surface ${width}x$height") }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                Hs5ClusterActivityOutput.surfaceChanged(this@Hs5ClusterActivity, null)
            }
        })
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        val density = resources.displayMetrics.density
        waiting = FrameLayout(this).apply {
            setBackgroundColor(placeholder)
            addView(ProgressBar(context).apply {
                isIndeterminate = true
                indeterminateTintList = ColorStateList.valueOf(if (dark) Color.WHITE else Color.DKGRAY)
                contentDescription = context.getString(R.string.cluster_waiting_for_map)
            }, FrameLayout.LayoutParams((40 * density).toInt(), (40 * density).toInt(), Gravity.CENTER))
        }
        root.addView(waiting, FrameLayout.LayoutParams(-1, -1))
        turnCard = ClusterTurnCardView(this).apply { visibility = View.GONE }
        root.addView(turnCard, FrameLayout.LayoutParams(-1, -1))
        root.addView(ClusterSpeedometerView(this), FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
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
        Hs5ClusterActivityOutput.focusChanged(this, resumed = true)
    }

    override fun onPause() {
        Hs5ClusterActivityOutput.focusChanged(this, resumed = false)
        if (isFinishing) Hs5ClusterActivityOutput.closedOutside(this, "finished by system")
        super.onPause()
    }

    override fun onStop() {
        // Another window covering the cluster means the head unit took the fly screen back.
        if (!isFinishing) Hs5ClusterActivityOutput.closedOutside(this, "covered on cluster")
        super.onStop()
    }

    override fun onDestroy() {
        Hs5ClusterActivityOutput.closedOutside(this, "destroyed")
        super.onDestroy()
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
        val active = Hs5ClusterActivityOutput.streamActive
        waiting?.visibility = if (active) View.GONE else View.VISIBLE
        val card = Hs5ClusterActivityOutput.turnCard
        turnCard?.setLayout(card.xPercent, card.yPercent, card.sizePercent)
        turnCard?.setOpacity(card.opacityPercent)
        turnCard?.setNightMode(card.night)
        turnCard?.setGuidance(if (active) card.guidance else null)
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
            appendLine("open=${isOpen()} focused=$resumed streamActive=$streamActive surface=${outputSurface != null}")
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
        DiagnosticLogging.logcat { Log.i(TAG, message) }
        val line = "${SimpleDateFormat("HH:mm:ss.SSS", Locale.US).format(Date())} $message"
        synchronized(events) {
            events.addLast(line)
            while (events.size > MAX_EVENTS) events.removeFirst()
        }
    }
}
