package com.shilapi.xcertplay.carlink

import com.shilapi.xcertplay.*
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.hud.Hs5NavigationOutputs
import com.shilapi.xcertplay.newui.NewUiActivity
import android.content.Intent
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Owns the ICCOA video surface and forwards head-unit input to the phone. */
class CarlinkConnectionActivity : Activity() {
    private lateinit var bridge: CarlinkBridge
    private lateinit var surfaceView: TextureView
    private var projectionSurface: Surface? = null
    private var captureBitmap: Bitmap? = null
    private var cluster: ClusterMapPresentation? = null
    private var clusterActivityOwned = false
    private var flyScreenVisible = false
    private var handlingDesktopRequest = false
    private val gestures = CarlinkGesturePolicy()
    private val frameHandler = Handler(Looper.getMainLooper())
    private var frameBitmap: Bitmap? = null
    private var lastCopyResult: Int? = null
    private fun scheduleFrameCopy(delay: Long = 0L) {
        frameHandler.removeCallbacks(copyCenterFrame)
        if (cluster != null && !isFinishing) frameHandler.postDelayed(copyCenterFrame, delay)
    }
    private val copyCenterFrame = object : Runnable {
        override fun run() {
            val target = cluster ?: return
            if (isFinishing || !surfaceView.isAvailable || surfaceView.width <= 0 || surfaceView.height <= 0) {
                scheduleFrameCopy(100L)
                return
            }
            // Read the decoder texture directly: PixelCopy on this firmware returns ERROR_UNKNOWN.
            val width = bridge.videoWidth
            val height = bridge.videoHeight
            val capture = captureBitmap?.takeIf { it.width == width && it.height == height }
                ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { captureBitmap = it }
            val region = ClusterCopyRegion.load(this@CarlinkConnectionActivity)
            runCatching {
                checkNotNull(surfaceView.getBitmap(capture))
                val output = CarlinkFrameCopy.crop(capture, region, frameBitmap)
                if (lastCopyResult != 0 || output !== frameBitmap) bridge.recordProjectionEvent(
                    "cluster texture copy ready source=${width}x$height region=$region output=${output.width}x${output.height} nativePixels=true")
                frameBitmap = output
                lastCopyResult = 0
                if (cluster === target) target.setCopiedMap(output)
            }.onFailure {
                if (lastCopyResult != -1) bridge.recordProjectionEvent("cluster texture copy failed ${it.javaClass.simpleName}: ${it.message}")
                lastCopyResult = -1
            }
            scheduleFrameCopy(if (lastCopyResult == 0) 50L else 250L)
        }
    }
    private var unsubscribeGlance: (() -> Unit)? = null
    private val clusterExitRequest: () -> Unit = { runOnUiThread { closeFlyScreen() } }
    private val clusterOwner = object : Hs5ClusterActivityOutput.Host {
        override val externalNavigation = { bridge.navigation }
        // Carlink has one decoder surface. Cluster uses the existing native instruments
        // and navigation metadata; binding its surface would steal the centre stream.
        override fun onClusterWindowSurface(surface: android.view.Surface?) = Unit
        override fun onClusterWindowClosed(reason: String) { clusterActivityOwned = false; flyScreenVisible = false; Hs5NavigationOutputs.setClusterMapShown(false) }
    }
    private val activityScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStart() {
        super.onStart()
        isForeground = true
    }

    override fun onStop() {
        isForeground = false
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        bridge = CarlinkBridge.get(this)
        if (bridge.snapshot.value.state != CarlinkLinkState.CONNECTED) {
            finish()
            return
        }
        current = java.lang.ref.WeakReference(this)
        Hs5NavigationOutputs.setHs5ClusterExitListener(clusterExitRequest)
        applySystemBars()
        flyScreenVisible = AirPlayPersistence.loadClusterFlyScreenDefaultOnConnect(this)
        val root = FrameLayout(this)
        surfaceView = TextureView(this).apply {
            isClickable = false
            isFocusable = false
            surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                    texture.setDefaultBufferSize(bridge.videoWidth, bridge.videoHeight)
                    val surface = Surface(texture)
                    projectionSurface = surface
                    bridge.setSurface(surface)
                    bridge.recordProjectionEvent("texture surface=${width}x$height video=${bridge.videoWidth}x${bridge.videoHeight}")
                    scheduleFrameCopy()
                }
                override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = Unit
                override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
                override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                    projectionSurface?.let { bridge.onSurfaceDestroyed(it); it.release() }
                    projectionSurface = null
                    return true
                }
            }
            setOnTouchListener { view, event -> forwardTouch(view, event) }
        }
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        root.post { updateCluster() }
        unsubscribeGlance = CarPlayGlance.subscribe { runOnUiThread { refreshClusterGuidance() } }
        activityScope.launch {
            bridge.snapshot.collect { snapshot ->
                // Discovery, pairing and errors belong to the existing connection hub.
                if (snapshot.state != CarlinkLinkState.CONNECTED) finish()
                else if (snapshot.desktopRequested) returnToHome()
            }
        }
    }

    private fun forwardTouch(view: View, event: MotionEvent): Boolean {
        val settingsFingers = AirPlayPersistence.loadSettingsGestureFingers(this)
        val clusterGesture = AirPlayPersistence.loadClusterMapEnabled(this) && AirPlayPersistence.loadClusterMapHorizontalGestureEnabled(this)
        val decision = gestures.accept(event.actionMasked, event.pointerCount,
            (0 until event.pointerCount).map { event.getX(it) }.average().toFloat(),
            (0 until event.pointerCount).map { event.getY(it) }.average().toFloat(),
            settingsFingers, clusterGesture, 72f * resources.displayMetrics.density)
        if (decision.cancelPhone) {
            val cancel = MotionEvent.obtain(event)
            cancel.action = MotionEvent.ACTION_CANCEL
            sendTouch(view, cancel)
            cancel.recycle()
        }
        decision.action?.let { bridge.recordProjectionEvent("gesture=$it") }
        when (decision.action) {
            CarlinkGesturePolicy.Action.SETTINGS -> startActivity(Intent(this, NewUiActivity::class.java)
                .putExtra(DiPlayLauncher.EXTRA_PAGE, "settings").addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
            CarlinkGesturePolicy.Action.SHOW_CLUSTER -> { flyScreenVisible = true; updateCluster() }
            CarlinkGesturePolicy.Action.HIDE_CLUSTER -> closeFlyScreen()
            null -> Unit
        }
        if (decision.consume) return true
        sendTouch(view, event)
        return true
    }

    private fun sendTouch(view: View, event: MotionEvent): Boolean {
        val viewWidth = view.width
        val viewHeight = view.height
        val videoWidth = bridge.videoWidth
        val videoHeight = bridge.videoHeight
        if (viewWidth <= 0 || viewHeight <= 0 || videoWidth <= 0 || videoHeight <= 0) return false
        val count = event.pointerCount
        if (count <= 0) return false
        val ids = IntArray(count)
        val xs = IntArray(count)
        val ys = IntArray(count)
        for (index in 0 until count) {
            ids[index] = event.getPointerId(index)
            xs[index] = (event.getX(index) * videoWidth / viewWidth).toInt().coerceIn(0, videoWidth - 1)
            ys[index] = (event.getY(index) * videoHeight / viewHeight).toInt().coerceIn(0, videoHeight - 1)
        }
        return bridge.sendTouch(event.action, ids, xs, ys)
    }

    private val nextTrackPress = com.shilapi.xcertplay.NextTrackPress()
    private fun clickMediaKey(key: Int) {
        bridge.sendKey(key, android.view.KeyEvent.ACTION_DOWN)
        bridge.sendKey(key, android.view.KeyEvent.ACTION_UP)
    }
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (nextTrackPress.dispatch(event,
            next = { clickMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_NEXT) },
            toggle = { clickMediaKey(android.view.KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) })) return true
        return if (bridge.sendKey(event.keyCode, event.action)) true else super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    private fun applySystemBars() {
        var flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        if (AirPlayPersistence.loadHideTopBar(this)) flags = flags or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        if (AirPlayPersistence.loadHideBottomBar(this)) flags = flags or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        window.decorView.systemUiVisibility = flags
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applySystemBars()
    }

    override fun onResume() {
        super.onResume()
        if (::bridge.isInitialized) {
            if (!bridge.snapshot.value.desktopRequested) handlingDesktopRequest = false
            applySystemBars(); updateCluster()
        }
    }

    internal fun updateCluster() {
        if (!AirPlayPersistence.loadClusterMapEnabled(this) || !flyScreenVisible || isFinishing) { closeFlyScreen(); return }
        if (cluster != null || clusterActivityOwned) {
            refreshClusterGuidance()
            scheduleFrameCopy()
            return
        }
        val display = ClusterMapPresentation.findDisplay(this) ?: run {
            bridge.recordProjectionEvent("cluster unavailable: no display")
            return
        }
        if (Hs5ClusterActivityOutput.enabled(this) &&
            DiPlayPreferences.phoneLinkMode(this) != DiPlayPreferences.PhoneLinkMode.CARLINK) {
            clusterActivityOwned = Hs5ClusterActivityOutput.open(this, display, clusterOwner)
        }
        if (!clusterActivityOwned) {
            val presentation = ClusterMapPresentation(this, display, externalNavigation = { bridge.navigation }, onSurface = {})
            presentation.setOnDismissListener {
                if (cluster === presentation) { cluster = null; flyScreenVisible = false; Hs5NavigationOutputs.setClusterMapShown(false) }
            }
            runCatching { presentation.show(); cluster = presentation }
                .onFailure { bridge.recordProjectionEvent("cluster show failed ${it.javaClass.simpleName}") }
        }
        bridge.recordProjectionEvent("cluster shown=${clusterActivityOwned || cluster != null} activity=$clusterActivityOwned display=${display.displayId}")
        if (cluster != null && shouldCopyCenterMap()) scheduleFrameCopy()
        Hs5NavigationOutputs.setClusterMapShown(clusterActivityOwned || cluster != null)
        refreshClusterGuidance()
    }

    private fun refreshClusterGuidance() {
        cluster?.setTurnCardGuidance(bridge.navigation)
        cluster?.setTurnCardOverlay(AirPlayPersistence.loadClusterTurnCardOverlayXPercent(this),
            AirPlayPersistence.loadClusterTurnCardOverlayYPercent(this), AirPlayPersistence.loadClusterTurnCardOverlaySizePercent(this))
        cluster?.setTurnCardOpacity(AirPlayPersistence.loadClusterTurnCardOpacityPercent(this))
        cluster?.setTurnCardNightMode(resolveAppNightNow())
        if (clusterActivityOwned) Hs5ClusterActivityOutput.setTurnCard(Hs5ClusterActivityOutput.TurnCard(guidance = bridge.navigation, night = resolveAppNightNow()))
    }

    internal fun flyScreenShown(): Boolean = !isDestroyed && (cluster != null || clusterActivityOwned)

    internal fun closeFlyScreen(): Boolean {
        frameHandler.removeCallbacks(copyCenterFrame)
        val shown = cluster != null || clusterActivityOwned
        flyScreenVisible = false
        val old = cluster; cluster = null; old?.dismiss()
        if (clusterActivityOwned) { clusterActivityOwned = false; Hs5ClusterActivityOutput.close("Carlink flyscreen close") }
        if (shown) Hs5NavigationOutputs.setClusterMapShown(false)
        if (shown) bridge.recordProjectionEvent("cluster closed")
        return shown
    }

    private fun shouldCopyCenterMap(): Boolean =
        DiPlayPreferences.phoneLinkMode(this) == DiPlayPreferences.PhoneLinkMode.CARLINK ||
            AirPlayPersistence.loadClusterOnlyMode(this)

    private fun returnToHome() {
        if (handlingDesktopRequest) return
        handlingDesktopRequest = true
        bridge.recordProjectionEvent("return to home")
        // Only switch the centre display; keep the session and cluster output alive.
        runCatching { startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)) }
            .onFailure { bridge.recordProjectionEvent("return to car launcher failed ${it.javaClass.simpleName}: ${it.message}") }
    }

    override fun onDestroy() {
        unsubscribeGlance?.invoke()
        closeFlyScreen()
        if (current?.get() === this) current = null
        Hs5NavigationOutputs.clearHs5ClusterExitListener(clusterExitRequest)
        activityScope.cancel()
        frameHandler.removeCallbacksAndMessages(null)
        frameBitmap = null
        projectionSurface?.let { bridge.onSurfaceDestroyed(it); it.release() }
        projectionSurface = null
        captureBitmap = null
        super.onDestroy()
    }

    companion object {
        internal var current: java.lang.ref.WeakReference<CarlinkConnectionActivity>? = null
        @Volatile var isForeground: Boolean = false
            private set
    }
}
