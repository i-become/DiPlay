package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.util.concurrent.Executors

/** One scene for editor, Presentation and Activity. Parameter edits retain the decoder surface. */
internal class OriginalClusterScene(context: Context, private val preview: Boolean = false,
    onSurface: (Surface?) -> Unit = {}) : FrameLayout(context) {
    private val map = FrameLayout(context)
    private val layerHost = FrameLayout(context)
    private val video = if (!preview) ClusterVideoTexture(context, onSurface = onSurface) else null
    private val copied = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_XY; visibility = GONE }
    val overlay = OriginalClusterOverlayView(context, preview).apply { drawPreviewBackground = false }
    private val mediaViews = linkedMapOf<String, OriginalMediaView>()
    private val componentViews = OriginalClusterComponent.entries.filterNot { it == OriginalClusterComponent.MAP }.associateWith {
        OriginalComponentLayerView(context, overlay = this@OriginalClusterScene.overlay, component = it)
    }
    private var unsubscribe: (() -> Unit)? = null
    private var sceneVisible = true
    var sceneLayout = OriginalClusterSettings.load(context)
        set(value) {
            if (field == value && childCount > 0) return
            field = value
            overlay.layout = value
            reconcileMedia()
            orderLayers()
            // Each widget is a separate full-screen view; invalidating its parent alone
            // does not invalidate a previously recorded child display list.
            componentViews.values.forEach { it.invalidate() }
            layerHost.invalidate()
            invalidate()
            requestLayout()
        }

    init {
        setBackgroundColor(Color.BLACK)
        clipChildren = true
        video?.let { map.addView(it, LayoutParams(-1, -1)) }
        if (preview) map.addView(ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setImageBitmap(com.shilapi.xcertplay.newui.display.ClusterStylePreview.create(context, ClusterStyle.ORIGINAL))
        }, LayoutParams(-1, -1))
        map.addView(copied, LayoutParams(-1, -1))
        if (!preview && AirPlayPersistence.loadClusterOnlyMode(context))
            map.addView(ClusterScreenCopyView(context, ClusterStyle.ORIGINAL), LayoutParams(-1, -1))
        layerHost.addView(map, LayoutParams(-1, -1))
        componentViews.values.forEach { layerHost.addView(it, LayoutParams(-1, -1)) }
        addView(layerHost, LayoutParams(-1, -1)); addView(overlay, LayoutParams(-1, -1))
        overlay.onLayoutChanged = { sceneLayout = it; onLayoutChanged?.invoke(it) }
        overlay.onDataInvalidated = { componentViews.values.forEach { it.invalidate() } }
        reconcileMedia()
        orderLayers()
    }
    var onLayoutChanged: ((OriginalClusterLayout) -> Unit)? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!preview) {
            sceneLayout = OriginalClusterSettings.load(context)
            unsubscribe = OriginalClusterSettings.subscribe(context) {
                post { sceneLayout = OriginalClusterSettings.load(context) }
            }
        }
    }
    override fun onDetachedFromWindow() {
        unsubscribe?.invoke(); unsubscribe = null
        super.onDetachedFromWindow()
    }
    fun setCopiedMap(bitmap: Bitmap?) {
        copied.setImageBitmap(bitmap); copied.visibility = if (bitmap == null) GONE else VISIBLE
    }
    fun setSceneVisible(visible: Boolean) {
        sceneVisible = visible
        overlay.setOverlayVisible(visible)
        alpha = if (visible) 1f else 0f
        mediaViews.values.forEach { it.setPlaybackVisible(visible) }
    }
    private fun reconcileMedia() {
        val wanted = sceneLayout.media.associateBy { it.id }
        mediaViews.keys.toList().filter { it !in wanted }.forEach { id -> mediaViews.remove(id)?.let(layerHost::removeView) }
        wanted.forEach { (id, item) ->
            var view = mediaViews[id]
            if (view == null || view.item.uri != item.uri || view.item.type != item.type) {
                view?.let(layerHost::removeView)
                view = OriginalMediaView(context, item)
                view.setPlaybackVisible(sceneVisible)
                mediaViews[id] = view
                layerHost.addView(view, LayoutParams(-1, -1))
            }
            view.setStretchFullScreen(item.stretchFullScreen)
        }
        orderLayers()
    }
    private fun orderLayers() {
        sceneLayout.orderedLayers.forEach { id ->
            val view = when (id) {
                OriginalClusterComponent.MAP.key -> map
                else -> OriginalClusterComponent.entries.firstOrNull { it.key == id }?.let(componentViews::get)
                    ?: mediaViews[id]
            }
            if (view != null && view.parent === layerHost) layerHost.bringChildToFront(view)
        }
    }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        fun measure(view: View, w: Float, h: Float) = view.measure(
            MeasureSpec.makeMeasureSpec((measuredWidth * w).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec((measuredHeight * h).toInt().coerceAtLeast(1), MeasureSpec.EXACTLY))
        measure(map, 1f, 1f)
        componentViews.values.forEach { measure(it, 1f, 1f) }
        sceneLayout.media.forEach { item -> mediaViews[item.id]?.let { measure(it, item.width, item.height) } }
    }
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        fun place(view: View, p: OriginalClusterPlacement, w: Float, h: Float) {
            val x = (width * p.x).toInt(); val y = (height * p.y).toInt()
            view.layout(x, y, x + (width * w).toInt(), y + (height * h).toInt())
            view.scaleX = p.scale * if (p.flipHorizontal) -1f else 1f
            view.scaleY = p.scale * if (p.flipVertical) -1f else 1f; view.rotation = p.rotation; view.alpha = p.opacity
            // Keep the map texture alive while hidden; no reconnect or decoder rebuild.
            if (view === map) view.alpha = if (p.visible) p.opacity else 0f
            else view.visibility = if (p.visible) VISIBLE else INVISIBLE
        }
        place(map, sceneLayout[OriginalClusterComponent.MAP], 1f, 1f)
        componentViews.values.forEach { it.layout(0, 0, width, height) }
        sceneLayout.media.forEach { item -> mediaViews[item.id]?.let {
            place(it, item.placement, item.width, item.height)
        } }
        overlay.bringToFront()
    }
}

private class OriginalComponentLayerView(context: Context, private val overlay: OriginalClusterOverlayView,
    private val component: OriginalClusterComponent) : View(context) {
    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        overlay.drawComponent(canvas, width, height, component)
        if (overlay.shouldAnimate(component)) postInvalidateDelayed(33L)
    }
}

/** Muted videos loop; decoders and file handles are released whenever the view is detached. */
private class OriginalMediaView(context: Context, val item: OriginalClusterMedia) : FrameLayout(context), TextureView.SurfaceTextureListener {
    private val image = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val texture = TextureView(context).apply { isOpaque = false; surfaceTextureListener = this@OriginalMediaView }
    private var player: MediaPlayer? = null
    private var output: Surface? = null
    private var stretchFullScreen = false
    fun setStretchFullScreen(stretch: Boolean) {
        if (stretchFullScreen == stretch) return
        stretchFullScreen = stretch
        image.scaleType = if (stretch) ImageView.ScaleType.FIT_XY else ImageView.ScaleType.FIT_CENTER
        fitVideo()
    }
    private var prepared = false
    private var allowed = true
    private var generation = 0
    init { addView(if (item.type == OriginalMediaType.IMAGE) image else texture, LayoutParams(-1, -1)) }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (item.type == OriginalMediaType.IMAGE) {
            val token = ++generation
            val resolver = context.contentResolver
            images.execute {
                val bitmap = runCatching {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(Uri.parse(item.uri))?.use { BitmapFactory.decodeStream(it, null, options) }
                    options.inSampleSize = 1
                    while (options.outWidth / options.inSampleSize > 4096 || options.outHeight / options.inSampleSize > 2048)
                        options.inSampleSize *= 2
                    options.inJustDecodeBounds = false
                    resolver.openInputStream(Uri.parse(item.uri))?.use { BitmapFactory.decodeStream(it, null, options) }
                }.getOrNull()
                post { if (token == generation && isAttachedToWindow) image.setImageBitmap(bitmap) else bitmap?.recycle() }
            }
        } else if (texture.isAvailable) startVideo()
    }
    override fun onDetachedFromWindow() {
        generation++; image.setImageDrawable(null); releaseVideo(); super.onDetachedFromWindow()
    }
    fun setPlaybackVisible(visible: Boolean) { allowed = visible; updatePlayback() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updatePlayback() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updatePlayback() }
    private fun updatePlayback() {
        if (!prepared) return
        runCatching { if (allowed && isShown && windowVisibility == VISIBLE) player?.start() else player?.pause() }
    }
    private fun startVideo() {
        if (player != null || !isAttachedToWindow) return
        val surfaceTexture = texture.surfaceTexture ?: return
        runCatching {
            // Native player creation can fail when projection and previews exhaust decoders.
            output = Surface(surfaceTexture)
            val next = MediaPlayer(); player = next
            next.setDataSource(context, Uri.parse(item.uri)); next.setSurface(output)
            next.setVolume(0f, 0f); next.isLooping = true
            next.setOnVideoSizeChangedListener { current, _, _ -> if (player === current) fitVideo() }
            next.setOnPreparedListener { if (player === it) { prepared = true; updatePlayback() } }
            next.setOnErrorListener { failed, _, _ -> if (player === failed) releaseVideo(); true }
            next.prepareAsync()
        }.onFailure { error ->
            releaseVideo()
            DiagnosticLogging.write {
                android.util.Log.w("OriginalClusterMedia", "Video initialization failed: ${error.javaClass.simpleName}")
            }
        }
    }
    private fun releaseVideo() {
        prepared = false
        val previous = player; player = null
        val previousOutput = output; output = null
        runCatching { previous?.release() }
        runCatching { previousOutput?.release() }
    }
    private fun fitVideo() {
        val current = player ?: return
        val dimensions = runCatching { current.videoWidth to current.videoHeight }.getOrNull() ?: return
        val (w, h) = dimensions
        if (w <= 0 || h <= 0 || texture.width <= 0 || texture.height <= 0) return
        if (stretchFullScreen) { texture.setTransform(null); return }
        val fit = minOf(texture.width.toFloat() / w, texture.height.toFloat() / h)
        texture.setTransform(android.graphics.Matrix().apply {
            setScale(w * fit / texture.width, h * fit / texture.height, texture.width / 2f, texture.height / 2f)
        })
    }
    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) = startVideo()
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) = fitVideo()
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { releaseVideo(); return true }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
    companion object { private val images = Executors.newSingleThreadExecutor { r -> Thread(r, "cluster-images").apply { isDaemon = true } } }
}
