package com.shilapi.xcertplay

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.host.R
import java.util.concurrent.CopyOnWriteArraySet

/** One authorized capture session. No persisted/reused authorization tokens or privileged APIs. */
class ClusterScreenCaptureService : Service() {
    private var worker: HandlerThread? = null
    private var capture: Handler? = null
    private var projection: MediaProjection? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var stopped = false
    private val firstFrameReported = java.util.concurrent.atomic.AtomicBoolean(false)
    private val callback = object : MediaProjection.Callback() {
        override fun onStop() { stopCapture("MediaProjection stopped or revoked") }
        override fun onCapturedContentResize(width: Int, height: Int) {
            CarPlayStartupDiagnostics.capture("cluster-only captured-content resize=${width}x$height")
            if (width > 0 && height > 0) resize(width, height)
        }
    }

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP || !AirPlayPersistence.loadClusterOnlyMode(this)) {
            CarPlayStartupDiagnostics.capture("cluster-only capture service stop request action=${intent?.action == STOP} enabled=${AirPlayPersistence.loadClusterOnlyMode(this)}")
            stopSelf(); return START_NOT_STICKY
        }
        // A new consent must start a new service instance/session, never reuse a token.
        if (worker != null) {
            CarPlayStartupDiagnostics.capture("cluster-only capture service ignored duplicate start")
            return START_NOT_STICKY
        }
        val consent = if (Build.VERSION.SDK_INT >= 33) intent?.getParcelableExtra(CONSENT, Intent::class.java)
            else @Suppress("DEPRECATION") (intent?.getParcelableExtra<Intent>(CONSENT))
        if (consent == null) {
            CarPlayStartupDiagnostics.capture("cluster-only capture service failed: consent intent missing")
            stopSelf(); return START_NOT_STICKY
        }
        CarPlayStartupDiagnostics.capture("cluster-only capture service starting sdk=${Build.VERSION.SDK_INT}")
        isRunning = true
        try {
            val notifications = getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) notifications.createNotificationChannel(NotificationChannel(CHANNEL,
                getString(R.string.cluster_copy_channel), NotificationManager.IMPORTANCE_LOW))
            val stop = PendingIntent.getService(this, 0, Intent(this, javaClass).setAction(STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val open = PendingIntent.getActivity(this, 0, Intent(this,
                com.shilapi.xcertplay.newui.NewUiActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
            val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else @Suppress("DEPRECATION") Notification.Builder(this)
            val notification = builder
                .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.cluster_copy_notification)).setOngoing(true)
                .setContentIntent(open).addAction(Notification.Action.Builder(null,
                    getString(R.string.cluster_copy_stop), stop).build()).build()
            if (Build.VERSION.SDK_INT >= 29) startForeground(ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
            else startForeground(ID, notification)
            worker = HandlerThread("cluster-screen-capture").also { it.start() }
            capture = Handler(checkNotNull(worker).looper)
            capture?.post {
                try {
                    projection = getSystemService(MediaProjectionManager::class.java)
                        .getMediaProjection(Activity.RESULT_OK, consent)
                    CarPlayStartupDiagnostics.capture("cluster-only MediaProjection acquired")
                    // API 34 requires registration before createVirtualDisplay.
                    projection!!.registerCallback(callback, capture)
                    val (w, h) = screenSize()
                    resize(w, h)
                    log("started ${w}x$h")
                } catch (error: Exception) { fail(error) }
            }
        } catch (error: Exception) { fail(error) }
        return START_NOT_STICKY
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val wm = getSystemService(WindowManager::class.java)
        if (Build.VERSION.SDK_INT >= 30) return wm.maximumWindowMetrics.bounds.let { it.width() to it.height() }
        return android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }.let { it.widthPixels to it.heightPixels }
    }

    /** Resize the existing VirtualDisplay: Android 14 allows only one creation per consent. */
    private fun resize(width: Int, height: Int) {
        if (stopped || projection == null) return
        if (reader?.width == width && reader?.height == height) return
        try {
            val previous = reader
            val next = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader = next
            try {
                previous?.setOnImageAvailableListener(null, null)
                display?.surface = null
            } finally { previous?.close() }
            next.setOnImageAvailableListener({ source ->
                try {
                    source.acquireLatestImage()?.use { image ->
                        if (source !== reader || stopped) return@use
                        val plane = image.planes[0]
                        ClusterScreenFrames.update(image.width, image.height, plane.pixelStride,
                            plane.rowStride, plane.buffer)
                        if (firstFrameReported.compareAndSet(false, true)) {
                            CarPlayStartupDiagnostics.capture("cluster-only first screen frame=${image.width}x${image.height} pixelStride=${plane.pixelStride} rowStride=${plane.rowStride}")
                        }
                    }
                } catch (error: Exception) { if (!stopped) fail(error) }
            }, capture)
            val density = resources.displayMetrics.densityDpi
            if (display == null) {
                display = projection!!.createVirtualDisplay("DiPlay-ScreenCapture",
                    width, height, density, DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, next.surface, null, capture)
                    ?: throw IllegalStateException("MediaProjection did not create a VirtualDisplay")
                CarPlayStartupDiagnostics.capture("cluster-only VirtualDisplay created=${display != null} size=${width}x$height densityDpi=$density")
            } else {
                display!!.surface = null; display!!.resize(width, height, density); display!!.surface = next.surface
                CarPlayStartupDiagnostics.capture("cluster-only VirtualDisplay resized=${width}x$height densityDpi=$density")
            }
        } catch (error: Exception) { fail(error) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        capture?.post { val (w, h) = screenSize(); resize(w, h) }
    }

    private fun fail(error: Exception) {
        CarPlayStartupDiagnostics.capture("cluster-only capture failed type=${error.javaClass.simpleName} message=${error.message}")
        log("stopped: ${error.javaClass.simpleName}")
        Handler(Looper.getMainLooper()).post { Toast.makeText(this, R.string.cluster_copy_error, Toast.LENGTH_LONG).show() }
        stopCapture()
    }

    private fun stopCapture(reason: String = "service destroyed or stopped") {
        // All output resources are confined to the worker, including system revocation.
        val handler = capture
        if (handler != null && Looper.myLooper() != handler.looper) { handler.post { stopCapture() }; return }
        if (stopped) return
        stopped = true
        isRunning = false
        stoppedGeneration++
        CarPlayStartupDiagnostics.capture("cluster-only capture stopping reason=$reason status=${ClusterScreenFrames.status()}")
        runCatching { display?.release() }; display = null
        runCatching { reader?.setOnImageAvailableListener(null, null); reader?.close() }; reader = null
        val previous = projection; projection = null
        runCatching { previous?.unregisterCallback(callback); previous?.stop() }
        ClusterScreenFrames.clear()
        worker?.quitSafely(); worker = null; capture = null
        log("released reason=$reason")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }
    override fun onDestroy() { stopCapture(); super.onDestroy() }

    private fun log(message: String) = DiagnosticLogging.logcat { android.util.Log.i("DiPlay-ScreenCopy", message) }

    companion object {
        @Volatile var isRunning = false
            private set
        @Volatile var stoppedGeneration = 0L
            private set
        private const val CHANNEL = "cluster-screen-copy"
        private const val ID = 3107
        private const val CONSENT = "consent"
        private const val STOP = "stop"
        fun start(context: Context, consent: Intent) {
            val intent = Intent(context, ClusterScreenCaptureService::class.java).putExtra(CONSENT, consent)
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent) else context.startService(intent)
            isRunning = true
        }
        fun stop(context: Context) { context.stopService(Intent(context, ClusterScreenCaptureService::class.java)) }
    }
}

/** A bounded, worker-owned full-resolution frame. Consumers use a SOFTWARE layer, so drawBitmap
 * copies pixels into the layer synchronously instead of retaining this mutable bitmap in a GPU
 * display list. The same lock protects writing, drawing and resource release. */
internal object ClusterScreenFrames {
    private val lock = Any()
    private var frame: Bitmap? = null
    private var frameWidth = 0
    private var frameHeight = 0
    private var lastFrameAt = 0L
    private val observers = CopyOnWriteArraySet<() -> Unit>()
    private var rowBuffer: java.nio.ByteBuffer? = null
    fun status(): String = synchronized(lock) {
        val bitmap = frame
        val ageMs = lastFrameAt.takeIf { it > 0 }?.let { SystemClock.uptimeMillis() - it }
        val samples = if (bitmap == null || frameWidth <= 0 || frameHeight <= 0) emptyList() else
            (0..2).flatMap { row -> (0..2).map { col ->
                bitmap.getPixel((frameWidth - 1) * col / 2, (frameHeight - 1) * row / 2) and 0x00ffffff
            } }
        val nonBlack = samples.count { it != 0 }
        "running=${ClusterScreenCaptureService.isRunning} frame=${if (bitmap == null) "none" else "${frameWidth}x${frameHeight}"} frameAgeMs=${ageMs ?: "unavailable"} contentSamples=$nonBlack/${samples.size} distinctSamples=${samples.distinct().size}"
    }
    fun hasFrame(): Boolean = synchronized(lock) { frame != null }
    fun update(width: Int, height: Int, pixelStride: Int, rowStride: Int, buffer: java.nio.ByteBuffer) {
        require(width > 0 && height > 0)
        require(pixelStride == 4 && rowStride >= width * pixelStride && rowStride % pixelStride == 0)
        // ImageReader may omit padding bytes after the final row, but it must contain
        // every visible pixel. Reject truncated frames rather than publishing stale data.
        buffer.rewind()
        val minimumBytes = (height - 1L) * rowStride + width.toLong() * pixelStride
        require(buffer.remaining().toLong() >= minimumBytes) { "ImageReader frame is truncated" }
        val now = SystemClock.uptimeMillis()
        if (lastFrameAt != 0L && now - lastFrameAt < 32L) return // drain ImageReader, never accumulate old frames
        synchronized(lock) {
            val paddedWidth = rowStride / pixelStride
            if (frame?.width != paddedWidth || frame?.height != height) {
                frame?.recycle(); frame = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
                rowBuffer = null
            }
            frameWidth = width; frameHeight = height
            if (buffer.remaining() >= rowStride * height) frame!!.copyPixelsFromBuffer(buffer)
            else {
                val bytes = rowBuffer ?: java.nio.ByteBuffer.allocateDirect(rowStride * height).also { rowBuffer = it }
                bytes.clear(); bytes.put(buffer); bytes.rewind(); frame!!.copyPixelsFromBuffer(bytes)
            }
            lastFrameAt = now
        }
        observers.forEach { it() }
    }
    fun draw(canvas: Canvas, region: ClusterCopyRegion, zoom: Float, width: Int, height: Int, paint: Paint) = synchronized(lock) {
        val bitmap = frame ?: return@synchronized
        val cx = (region.left + region.right) / 2; val cy = (region.top + region.bottom) / 2
        val hw = (region.right - region.left) / zoom / 2; val hh = (region.bottom - region.top) / zoom / 2
        canvas.drawBitmap(bitmap, Rect(((cx - hw) * frameWidth).toInt(), ((cy - hh) * frameHeight).toInt(),
            ((cx + hw) * frameWidth).toInt(), ((cy + hh) * frameHeight).toInt()), Rect(0, 0, width, height), paint)
    }
    fun clear() {
        synchronized(lock) { frame?.recycle(); frame = null; rowBuffer = null; lastFrameAt = 0 }
        observers.forEach { it() }
    }
    fun subscribe(observer: () -> Unit): () -> Unit { observers.add(observer); return { observers.remove(observer) } }
}

/** One fixed aperture; each style crops from the full-resolution original frame independently. */
internal class ClusterScreenCopyView(context: Context, private val style: ClusterStyle) : View(context) {
    init { setLayerType(LAYER_TYPE_SOFTWARE, null) }
    private var unsubscribe: (() -> Unit)? = null
    private var firstDrawReported = false
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        CarPlayStartupDiagnostics.capture("cluster-only map-copy view attached style=${style.preferenceValue} size=${width}x$height mode=${AirPlayPersistence.loadClusterOnlyMode(context)} ${ClusterScreenFrames.status()}")
        unsubscribe = ClusterScreenFrames.subscribe { postInvalidateOnAnimation() }
    }
    override fun onDetachedFromWindow() { unsubscribe?.invoke(); unsubscribe = null; super.onDetachedFromWindow() }
    override fun onDraw(canvas: Canvas) {
        if (!AirPlayPersistence.loadClusterOnlyMode(context)) return
        if (ClusterScreenFrames.hasFrame()) {
            if (!firstDrawReported) {
                firstDrawReported = true
                CarPlayStartupDiagnostics.capture("cluster-only map-copy view first-frame-draw style=${style.preferenceValue} size=${width}x$height ${ClusterScreenFrames.status()}")
            }
            ClusterScreenFrames.draw(canvas, ClusterCopyRegion.load(context, style),
                (ClusterMapCropSettings.loadZoom(context) / 100f).coerceAtLeast(1f), width, height, paint)
        } else {
            if (!firstDrawReported) {
                firstDrawReported = true
                CarPlayStartupDiagnostics.capture("cluster-only map-copy view placeholder style=${style.preferenceValue} size=${width}x$height")
            }
            canvas.drawColor(Color.rgb(7, 9, 18))
            paint.color = Color.rgb(220, 224, 234)
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = (width / 32f).coerceIn(18f, 38f)
            val centerY = height / 2f
            canvas.drawText(context.getString(R.string.cluster_screen_copy_waiting), width / 2f,
                centerY - (paint.ascent() + paint.descent()) / 2f - paint.textSize * .65f, paint)
            paint.color = Color.rgb(154, 162, 180)
            paint.textSize = (width / 55f).coerceIn(12f, 22f)
            canvas.drawText(context.getString(R.string.cluster_screen_copy_permission_hint), width / 2f,
                centerY - (paint.ascent() + paint.descent()) / 2f + paint.textSize * .8f, paint)
        }
    }
}

