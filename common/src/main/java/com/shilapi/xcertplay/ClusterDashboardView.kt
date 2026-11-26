package com.shilapi.xcertplay

import android.content.Context
import android.animation.ValueAnimator
import android.os.Build
import android.os.SystemClock
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.widget.ImageView
import android.widget.ImageView.ScaleType
import android.view.Surface
import android.view.TextureView
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.view.View
import android.widget.FrameLayout
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.hud.Hs5HeadUnit
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import kotlin.math.min
import kotlin.math.roundToInt

internal data class ClusterDashboardMusic(
    val metadata: CarPlayNowPlaying = CarPlayNowPlaying(),
    val artwork: Bitmap? = null,
)

internal data class ClusterDashboardState(
    val guidance: CarPlayGlance.Snapshot = CarPlayGlance.Snapshot(),
    val music: ClusterDashboardMusic = ClusterDashboardMusic(),
    val vehicle: OemClusterVehicleState = OemClusterVehicleState(),
    val standalone: Boolean = false,
) {
    // Vehicle state belongs to the car, so clearing a phone session must retain it.
    fun current(): ClusterDashboardState = if (guidance.connected || standalone) this
        else ClusterDashboardState(vehicle = vehicle)

    fun progress(): Float? {
        val duration = music.metadata.durationMillis?.takeIf { it > 0 } ?: return null
        val elapsed = music.metadata.elapsedMillis ?: return null
        return (elapsed.toDouble() / duration).coerceIn(0.0, 1.0).toFloat()
    }
}

/** Native OEM instruments surround an independent, undistorted CarPlay map surface. */
internal class ClusterDashboardView(
    context: Context,
    private val style: ClusterStyle = AirPlayPersistence.loadClusterStyle(context),
    private val mapAspect: Float = style.mapAspect,
    private val readState: () -> ClusterDashboardState = {
        ClusterDashboardState(ClusterInstrumentData.guidance(context), ClusterInstrumentData.music(context),
            HongqiClusterVehicleData.snapshot(), standalone = AirPlayPersistence.loadClusterOnlyMode(context))
    },
    private val readNavigationCrop: () -> Int = { ClusterMapCropSettings.load(context) },
    private val externalNavigation: (() -> com.shilapi.xcertplay.hud.ClusterTurnGuidance?)? = null,
    private val onSurface: (Surface?) -> Unit = {},
) : FrameLayout(context) {
    private var state = ClusterDashboardState()
    private var night = context.resolveAppNightNow()
    private var streamActive = false
    private var dashboardVisible = true
    private var ticking = false
    private var subscriptions = emptyList<() -> Unit>()
    private val renderer = style.renderer(context)
    private val speedMotion = ClusterNeedleMotion()
    // RPM arrives at a lower rate than speed; a longer response bridges its samples.
    private val rpmMotion = ClusterNeedleMotion(125L)
    private val picture = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            renderer.draw(canvas, width, height, state, night, includeNeedles = false)
            if (ticking && renderer.lyricAnimating) postInvalidateDelayed(33L)
        }
    }
    private val needles = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            renderer.drawNeedles(canvas, width, height, speedMotion.valueAt(now), rpmMotion.valueAt(now))
            if (ticking && (speedMotion.moving(now) || rpmMotion.moving(now))) postInvalidateOnAnimation()
        }
    }
    // Texture transforms stay inside a fixed, clipping viewport. Neither zoom nor crop
    // changes the dashboard aperture or replaces the decoder Surface.
    internal val mapViewport = FrameLayout(context).apply {
        clipChildren = true
        clipToPadding = true
        setBackgroundColor(Color.BLACK)
    }
    // Decode the original stream into a texture; transform only texture coordinates,
    // never the fixed instrument aperture or the decoder Surface identity.
    internal val mapSurface = TextureView(context)
    internal var decoderSurface: Surface? = null
        private set
    // Keep the fixed map slot measured before the first copied frame arrives.
    private val copiedMap = ImageView(context).apply { scaleType = ScaleType.FIT_XY; visibility = INVISIBLE }
    private val externalTurn: ClusterTurnCardView? = null
    private val waiting = MapWaitingView(context)
    private val refresh = object : Runnable {
        override fun run() {
            if (!ticking) return
            refreshState()
        }
    }

    init {
        // The reference artwork is intentionally letterboxed on some cluster sizes.
        // Keep those uncovered pixels on the instrument's dark base instead of exposing
        // the head unit's white window background through the translucent activity.
        setBackgroundColor(Color.BLACK)
        mapSurface.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                decoderSurface = Surface(texture)
                onSurface(decoderSurface)
            }
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) = updateMapViewport()
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                try { onSurface(null) } finally {
                    decoderSurface?.release()
                    decoderSurface = null
                }
                return true
            }
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) = Unit
        }
        mapViewport.addView(mapSurface, LayoutParams(-1, -1))
        mapViewport.addView(copiedMap, LayoutParams(-1, -1))
        if (AirPlayPersistence.loadClusterOnlyMode(context)) {
            mapViewport.addView(ClusterScreenCopyView(context, style), LayoutParams(-1, -1))
        }
        addView(mapViewport, LayoutParams(1, 1))
        addView(waiting, LayoutParams(1, 1))
        externalTurn?.let { addView(it, LayoutParams(1, 1)) }
        // Keep the opaque dial faces and their feathered rims above the map texture.
        // The cached picture has a transparent opening only between the instruments.
        addView(picture, LayoutParams(-1, -1))
        addView(needles, LayoutParams(-1, -1))
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        updateMapWaiting()
    }

    fun setStreamActive(active: Boolean) {
        streamActive = active
        updateMapWaiting()
    }

    internal fun setCopiedMap(bitmap: Bitmap?) {
        copiedMap.setImageBitmap(bitmap)
        updateMapViewport()
        copiedMap.visibility = if (bitmap == null) INVISIBLE else VISIBLE
        if (bitmap != null) streamActive = true
        updateMapWaiting()
    }

    fun setNightMode(dark: Boolean) {
        if (night == dark) return
        night = dark
        picture.invalidate()
        waiting.invalidate()
    }

    /** Presentation hides by window alpha, which does not trigger View visibility callbacks. */
    fun setDashboardVisible(visible: Boolean) {
        dashboardVisible = visible
        updateTicking()
    }

    internal fun refreshState() {
        val previous = state
        state = readState().current()
        updateMapViewport()
        val now = SystemClock.uptimeMillis()
        val animate = ticking && (Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled())
        val measured = state.vehicle.validated()
        speedMotion.update(measured.speedKph, now, animate)
        rpmMotion.update(measured.rpm, now, animate)
        updateMapWaiting()
        contentDescription = listOf(
            context.getString(if (state.guidance.connected) R.string.cluster_dashboard_connected
                else R.string.cluster_dashboard_disconnected),
            state.guidance.road.ifBlank { context.getString(R.string.cluster_dashboard_waiting_navigation) },
            state.music.metadata.title?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.cluster_dashboard_waiting_music),
            state.music.metadata.artist.orEmpty(),
            if (state.vehicle.speedKph == null) context.getString(R.string.cluster_oem_vehicle_unavailable)
                else "${state.vehicle.speedKph} km/h",
        ).joinToString(" · ")
        if (previous.guidance != state.guidance || previous.music != state.music ||
            previous.vehicle.copy(speedKph = null, rpm = null) != state.vehicle.copy(speedKph = null, rpm = null)) {
            picture.invalidate()
        }
        needles.postInvalidateOnAnimation()
    }

    private fun updateMapWaiting() {
        val guidance = externalNavigation?.invoke()
        externalTurn?.setGuidance(guidance)
        externalTurn?.setNightMode(night)
        externalTurn?.visibility = if (guidance != null) VISIBLE else GONE
        // Copied video is already a usable map, even before navigation metadata arrives.
        waiting.visibility = if (AirPlayPersistence.loadClusterOnlyMode(context) || copiedMap.visibility == VISIBLE || guidance != null ||
            (streamActive && state.guidance.connected)) GONE else VISIBLE
        waiting.invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        updateTicking()
    }

    override fun onDetachedFromWindow() {
        ticking = false
        subscriptions.forEach { it() }
        subscriptions = emptyList()
        removeCallbacks(refresh)
        // Artwork belongs to the media cache, not to this window. Do not recycle it.
        state = ClusterDashboardState()
        speedMotion.update(null, 0, false)
        rpmMotion.update(null, 0, false)
        renderer.release()
        super.onDetachedFromWindow()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (isAttachedToWindow) updateTicking()
    }

    private fun updateTicking() {
        val shouldTick = isAttachedToWindow && windowVisibility == VISIBLE && dashboardVisible
        if (ticking == shouldTick) return
        ticking = shouldTick
        removeCallbacks(refresh)
        subscriptions.forEach { it() }
        subscriptions = if (ticking) listOf(
            HongqiClusterVehicleData.subscribe(context, ::scheduleRefresh),
            ClusterInstrumentData.subscribe(context, ::scheduleRefresh),
            ClusterMapCropSettings.subscribe(context, ::scheduleRefresh),
        ) else emptyList()
        if (ticking) refresh.run()
    }

    // Binder/iAP2 events can arrive off main. Coalesce only UI delivery, never fetch on a timer.
    private fun scheduleRefresh() {
        removeCallbacks(refresh)
        post(refresh)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)
        for (child in listOf(picture, needles)) {
            child.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY))
        }
        val map = style.mapBounds(w, h, mapAspect)
        for (child in listOfNotNull(mapViewport, waiting, externalTurn)) {
            child.measure(MeasureSpec.makeMeasureSpec(map.width().roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(map.height().roundToInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        picture.layout(0, 0, width, height)
        needles.layout(0, 0, width, height)
        val map = style.mapBounds(width, height, mapAspect)
        for (child in listOfNotNull(mapViewport, waiting, externalTurn)) {
            child.layout(map.left.roundToInt(), map.top.roundToInt(),
                map.left.roundToInt() + child.measuredWidth, map.top.roundToInt() + child.measuredHeight)
        }
        updateMapViewport()
    }

    private fun updateMapViewport() {
        val anchor = AirPlayPersistence.loadClusterMarkerYPercent(context)
        val crop = ClusterMapViewport.navigation(state.guidance.connected && state.guidance.navigating,
            readNavigationCrop(), anchor).zoom(ClusterMapCropSettings.loadZoom(context), anchor).cover()
        // Rebuild from the original texture on every update. Never concatenate the
        // previous matrix: repeated zoom changes must not accumulate resampling/crop.
        mapSurface.setTransform(Matrix().apply {
            setScale(crop.scale, crop.scale)
            postTranslate(crop.x * mapSurface.width, crop.y * mapSurface.height)
        })
        copiedMap.scaleType = ScaleType.MATRIX
        copiedMap.drawable?.let { drawable ->
            if (drawable.intrinsicWidth > 0 && drawable.intrinsicHeight > 0) {
                copiedMap.imageMatrix = Matrix().apply {
                    setScale(copiedMap.width * crop.scale / drawable.intrinsicWidth,
                        copiedMap.height * crop.scale / drawable.intrinsicHeight)
                    postTranslate(crop.x * copiedMap.width, crop.y * copiedMap.height)
                }
            }
        }
    }

    private inner class MapWaitingView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(if (style == ClusterStyle.SPORT) Color.rgb(3, 7, 12) else Color.rgb(14, 17, 39))
            paint.color = Color.rgb(170, 179, 207)
            paint.textSize = min(width / 32f, height / 16f)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(if (externalNavigation != null) R.string.cluster_dashboard_waiting_navigation else R.string.cluster_dashboard_waiting_map),
                width / 2f, height / 2f - (paint.ascent() + paint.descent()) / 2f, paint)
        }
    }

    internal companion object {

        fun enabled(context: Context): Boolean = supported(context) &&
            AirPlayPersistence.loadClusterStyle(context).customDashboard

        fun supported(context: Context): Boolean =
            !AirPlayPersistence.loadDebugSimulatedClusterPresentation(context) &&
            (Hs5HeadUnit.isHeadUnit(context) || Hs5HeadUnit.hasClusterPresentation(context) ||
                Hs5HeadUnit.showsHs5NavigationBranding(context))

        fun mapBounds(width: Int, height: Int, aspect: Float): RectF =
            OemClusterGeometry.mapBounds(width, height, aspect)
    }
}
