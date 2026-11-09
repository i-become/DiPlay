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
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
) {
    // Vehicle state belongs to the car, so clearing a phone session must retain it.
    fun current(): ClusterDashboardState = if (guidance.connected) this
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
        ClusterDashboardState(CarPlayGlance.snapshot(), CarPlayMediaKeys.dashboardMusic(),
            HongqiClusterVehicleData.snapshot())
    },
    private val readNavigationCrop: () -> Int = { ClusterMapCropSettings.load(context) },
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
        }
    }
    private val needles = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val now = SystemClock.uptimeMillis()
            renderer.drawNeedles(canvas, width, height, speedMotion.valueAt(now), rpmMotion.valueAt(now))
            if (ticking && (speedMotion.moving(now) || rpmMotion.moving(now))) postInvalidateOnAnimation()
        }
    }
    internal val mapSurface = SurfaceView(context)
    private val waiting = MapWaitingView(context)
    private val refresh = object : Runnable {
        override fun run() {
            if (!ticking) return
            refreshState()
        }
    }

    init {
        setBackgroundColor(Color.TRANSPARENT)
        mapSurface.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = onSurface(holder.surface)
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit
            override fun surfaceDestroyed(holder: SurfaceHolder) = onSurface(null)
        })
        addView(mapSurface, LayoutParams(1, 1))
        addView(waiting, LayoutParams(1, 1))
        // Keep the opaque dial faces and their feathered rims above the map Surface.
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
        waiting.visibility = if (streamActive && state.guidance.connected) GONE else VISIBLE
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
            CarPlayGlance.subscribe { scheduleRefresh() },
            CarPlayMediaKeys.subscribeDashboard(::scheduleRefresh),
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
        for (child in listOf(mapSurface, waiting)) {
            child.measure(MeasureSpec.makeMeasureSpec(map.width().roundToInt(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(map.height().roundToInt(), MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        picture.layout(0, 0, width, height)
        needles.layout(0, 0, width, height)
        val map = style.mapBounds(width, height, mapAspect)
        for (child in listOf(mapSurface, waiting)) {
            child.layout(map.left.roundToInt(), map.top.roundToInt(),
                map.left.roundToInt() + child.measuredWidth, map.top.roundToInt() + child.measuredHeight)
        }
        updateMapViewport()
    }

    private fun updateMapViewport() {
        val crop = ClusterMapViewport.navigation(state.guidance.connected && state.guidance.navigating,
            readNavigationCrop(), AirPlayPersistence.loadClusterMarkerYPercent(context))
        // API 28 SurfaceView transforms are composed by the platform. Keep holder dimensions,
        // decoder ownership and Surface identity unchanged when navigation starts/stops.
        mapSurface.pivotX = 0f
        mapSurface.pivotY = 0f
        mapSurface.scaleX = crop.scale
        mapSurface.scaleY = crop.scale
        mapSurface.translationX = crop.x * mapSurface.width
        mapSurface.translationY = crop.y * mapSurface.height
    }

    private inner class MapWaitingView(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            canvas.drawColor(if (style == ClusterStyle.SPORT) Color.rgb(3, 7, 12) else Color.rgb(14, 17, 39))
            paint.color = Color.rgb(170, 179, 207)
            paint.textSize = min(width / 32f, height / 16f)
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(context.getString(R.string.cluster_dashboard_waiting_map),
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
