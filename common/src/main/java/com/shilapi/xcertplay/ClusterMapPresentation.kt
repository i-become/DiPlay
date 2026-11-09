package com.shilapi.xcertplay

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.widget.FrameLayout
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import com.shilapi.xcertplay.hud.Hs5ClusterDisplay
import com.shilapi.xcertplay.hud.Hs5HeadUnit

/**
 * Shows CarPlay's instrument-cluster stream on the Hongqi HS5 projection display.
 */
internal class ClusterMapPresentation(
    context: Context,
    display: Display,
    private val onSurface: (Surface?) -> Unit,
) : Presentation(context, display) {
    // The placeholder until the map arrives: a small spinner.
    private var waitingLabel: View? = null
    private var turnCardView: ClusterTurnCardView? = null
    private var videoView: View? = null
    private var streamActive = false
    private var dashboard: ClusterDashboardView? = null
    private var originalOverlay: OriginalClusterOverlayView? = null
    private val dashboardForeground = Hs5ClusterForeground()
    var outputSurface: Surface? = null
        private set
    var mapVisible = true
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dark = context.resolveAppNightNow()
        if (ClusterDashboardView.enabled(context)) {
            dashboard = ClusterDashboardView(context) { surface ->
                outputSurface = surface
                onSurface(surface)
            }.apply {
                setNightMode(dark)
                setStreamActive(streamActive)
                setDashboardVisible(mapVisible)
            }
            if (dashboardForeground.attach(context, display, checkNotNull(dashboard)) { event ->
                if (event.action == android.view.KeyEvent.ACTION_UP && !event.isCanceled) {
                    CarPlayHostSettingsBridge.closeFlyScreen("cluster overlay back")
                }
                true
            }) {
                setContentView(View(context).apply { setBackgroundColor(Color.BLACK) })
            } else setContentView(checkNotNull(dashboard))
            dashboardForeground.setVisible(mapVisible)
            DiagnosticLogging.record { CarPlayStartupDiagnostics.capture("HS5 dashboard window=${dashboardForeground.mode}") }
            return
        }
        val placeholder = if (dark) Color.BLACK else Color.rgb(233, 238, 246)
        val root = FrameLayout(context).apply {
            setBackgroundColor(placeholder)
        }
        val surfaceView = SurfaceView(context)
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                DiagnosticLogging.logcat { Log.i(TAG, "cluster surface created") }
                outputSurface = holder.surface
                onSurface(holder.surface)
            }
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                DiagnosticLogging.logcat { Log.i(TAG, "cluster surface ${width}x$height") }
            }
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                DiagnosticLogging.logcat { Log.i(TAG, "cluster surface destroyed") }
                onSurface(null)
                outputSurface = null
            }
        })
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        videoView = surfaceView
        if (com.shilapi.xcertplay.hud.Hs5HeadUnit.isHeadUnit(context) &&
            AirPlayPersistence.loadClusterStyle(context) == ClusterStyle.ORIGINAL) {
            root.addView(OriginalClusterOverlayView(context).also { originalOverlay = it }, FrameLayout.LayoutParams(-1, -1))
            originalOverlay?.setOverlayVisible(mapVisible)
        } else {
            turnCardView = ClusterTurnCardView(context).apply { visibility = View.GONE }
            root.addView(turnCardView, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(ClusterSpeedometerView(context), FrameLayout.LayoutParams(-1, -1))
        if (AirPlayPersistence.loadDebugSimulatedClusterPresentation(context)) {
            root.addView(SimulatedUltraLeftSpeedGauge(context), FrameLayout.LayoutParams(-1, -1))
            root.addView(SimulatedUltraRightGauge(context), FrameLayout.LayoutParams(-1, -1))
        }
        // Start with the placeholder visible; SurfaceView would otherwise cover the light
        // backdrop with an empty black surface before the first stream notification.
        // SurfaceView ignores fractional alpha before API 34. Keep its decoder surface
        // visible and fade an opaque ordinary View over it instead.
        videoView?.alpha = 1f
        waitingLabel?.apply {
            alpha = if (streamActive) 0f else 1f
            visibility = if (streamActive) View.GONE else View.VISIBLE
        }
        setContentView(root)
    }

    /**
     * Cross-fades the waiting placeholder over the live map. TextureView can also fade the
     * video; legacy SurfaceView stays visible under the placeholder to preserve its surface.
     */
    fun setStreamActive(active: Boolean) {
        dashboard?.setStreamActive(active)
        if (streamActive == active) return
        // Update before cancelling: a cancelled fade must not hide the newly restored label.
        streamActive = active
        val video = videoView
        val label = waitingLabel
        video?.animate()?.cancel()
        label?.animate()?.cancel()
        if (video == null || label == null) return
        label.visibility = View.VISIBLE
        // Animate from the current opacity so a stream reversal does not jump or restart.
        if (video !is SurfaceView) video.animate().alpha(if (active) 1f else 0f).setDuration(300).start()
        label.animate().alpha(if (active) 0f else 1f).setDuration(300).withEndAction {
            if (streamActive == active) label.visibility = if (active) View.GONE else View.VISIBLE
        }.start()
    }

    /** Window alpha hides the pixels without destroying the TextureView/decoder surface. */
    fun setMapVisible(visible: Boolean) {
        mapVisible = visible
        dashboard?.setDashboardVisible(visible)
        originalOverlay?.setOverlayVisible(visible)
        dashboardForeground.setVisible(visible)
        window?.let { window ->
            val alpha = if (visible) 1f else 0f
            if (window.attributes.alpha != alpha) window.attributes = window.attributes.apply { this.alpha = alpha }
        }
    }

    fun setTurnCardOverlay(xPercent: Int, yPercent: Int, sizePercent: Int) {
        turnCardView?.setLayout(xPercent, yPercent, sizePercent)
    }

    fun setTurnCardNightMode(night: Boolean) {
        dashboard?.setNightMode(night)
        turnCardView?.setNightMode(night)
    }

    fun setTurnCardOpacity(percent: Int) {
        turnCardView?.setOpacity(percent)
    }

    fun setTurnCardGuidance(guidance: ClusterTurnGuidance?) {
        turnCardView?.setGuidance(guidance)
    }

    override fun onStop() {
        dashboardForeground.close()
        super.onStop()
    }

    companion object {
        const val TAG = "DiPlay-Cluster"

        fun findDisplay(context: Context): Display? {
            val displays = context.getSystemService(DisplayManager::class.java)
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION) ?: return null
            if (AirPlayPersistence.loadDebugSimulatedClusterPresentation(context)) {
                val mainId = context.display?.displayId ?: Display.DEFAULT_DISPLAY
                SimulatedClusterPresentationPick.pick(mainId, displays, ::sizeOf)?.let { return it }
            }
            return pickHs5Cluster(context, displays)
        }

        private fun pickHs5Cluster(context: Context, displays: Array<Display>): Display? {
            if (!Hs5HeadUnit.isHeadUnit(context) &&
                !Hs5HeadUnit.hasClusterPresentation(context) &&
                !Hs5HeadUnit.showsHs5NavigationBranding(context)
            ) {
                return null
            }
            val mainId = context.display?.displayId ?: android.view.Display.DEFAULT_DISPLAY
            return Hs5ClusterDisplay.pick(displays, mainId)
        }

        fun describeDisplays(context: Context): String =
            context.getSystemService(DisplayManager::class.java)?.displays
                ?.joinToString {
                    val size = sizeOf(it)
                    "${it.displayId}:${it.name} ${size.x}x${size.y} flags=${it.flags} valid=${it.isValid}"
                }.orEmpty()

        fun diagnosticReport(context: Context): String = buildString {
            appendLine("clusterEnabled=${AirPlayPersistence.loadClusterMapEnabled(context)}")
            appendLine("clusterNavigationCropPercent=${ClusterMapCropSettings.load(context)} navigating=${com.shilapi.xcertplay.glance.CarPlayGlance.snapshot().navigating}")
            appendLine(
                "debugSimulatedCluster=${AirPlayPersistence.loadDebugSimulatedClusterPresentation(context)}",
            )
            appendLine("navigationReceiverAvailable=${com.shilapi.xcertplay.hud.Hs5OutputSettings.navigationAvailable(context)}")
            appendLine("gaodePackage=${com.shilapi.xcertplay.hud.Hs5OutputSettings.resolvedGaodePackage(context)}")
            appendLine("hs5ClusterPresentation=${Hs5HeadUnit.hasClusterPresentation(context)}")
            appendLine("allDisplays=${describeDisplays(context)}")
            val presentations = context.getSystemService(DisplayManager::class.java)
                ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION).orEmpty()
            appendLine("presentationDisplayIds=${presentations.joinToString { it.displayId.toString() }}")
            val selected = findDisplay(context)
            append("selectedCluster=${selected?.let { "${it.displayId}:${it.name}" } ?: "none"}")
        }

        fun sizeOf(display: Display): Point = Point().also {
            @Suppress("DEPRECATION")
            display.getRealSize(it)
        }
    }
}
