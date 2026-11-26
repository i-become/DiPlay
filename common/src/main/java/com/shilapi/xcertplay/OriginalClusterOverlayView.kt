package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.glance.CarPlayLane
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** A transparent, event-driven overlay. Preview instances never bind the vehicle service. */
internal class OriginalClusterOverlayView(context: Context, private val preview: Boolean = false) : View(context) {
    var layout = OriginalClusterSettings.load(context)
        set(value) { if (field != value) { field = value; invalidate(); onDataInvalidated?.invoke() } }
    var onSelectionChanged: ((String) -> Unit)? = null
    var selectedId: String? = null
        set(value) { if (field != value) { field = value; invalidate(); onDataInvalidated?.invoke() } }
    var drawPreviewBackground = true
    var onLayoutChanged: ((OriginalClusterLayout) -> Unit)? = null
    var onDataInvalidated: (() -> Unit)? = null
    private val renderer = OriginalClusterRenderer(context)
    private val main = Handler(Looper.getMainLooper())
    private val subscriptions = mutableListOf<() -> Unit>()
    private var stopVehicle: (() -> Unit)? = null
    private var vehicleDemand: Pair<Boolean, Boolean>? = null
    private var observing = false
    private var drawingVisible = true
    private var vehicle = OemClusterVehicleState()
    private var nav = CarPlayGlance.Snapshot()
    private val refresh = Runnable {
        vehicle = HongqiClusterVehicleData.snapshot()
        nav = ClusterInstrumentData.guidance(context)
        invalidate()
        onDataInvalidated?.invoke()
    }
    private val musicRefresh = Runnable { invalidate(); onDataInvalidated?.invoke() }
    private val background by lazy { com.shilapi.xcertplay.newui.display.ClusterStylePreview.create(context, ClusterStyle.ORIGINAL) }
    private var dragging: String? = null
    private var candidate: String? = null
    private var offsetX = 0f
    private var offsetY = 0f

    override fun onAttachedToWindow() { super.onAttachedToWindow(); updateObservation() }
    override fun onDetachedFromWindow() { stopObservation(); renderer.releaseNavigationIcon(); super.onDetachedFromWindow() }
    override fun onWindowVisibilityChanged(visibility: Int) { super.onWindowVisibilityChanged(visibility); updateObservation() }
    override fun onVisibilityAggregated(isVisible: Boolean) { super.onVisibilityAggregated(isVisible); updateObservation() }

    fun setOverlayVisible(value: Boolean) {
        drawingVisible = value
        visibility = if (value) VISIBLE else INVISIBLE
        updateObservation()
    }

    private fun updateObservation() {
        val needed = !preview && isAttachedToWindow && isShown && windowVisibility == VISIBLE && drawingVisible
        if (needed == observing) return
        if (!needed) { stopObservation(); return }
        observing = true
        layout = OriginalClusterSettings.load(context)
        subscriptions += OriginalClusterSettings.subscribe(context) {
            layout = OriginalClusterSettings.load(context)
            subscribeVehicle()
            invalidate()
        }
        subscriptions += ClusterInstrumentData.subscribe(context) { scheduleRefresh(); scheduleMusicRefresh() }
        subscribeVehicle()
        scheduleRefresh()
    }

    private fun subscribeVehicle() {
        val demand = (layout[OriginalClusterComponent.TIRES].visible || layout[OriginalClusterComponent.DRIVING].visible) to
            layout[OriginalClusterComponent.DRIVING].visible
        if (vehicleDemand == demand) return
        vehicleDemand = demand
        stopVehicle?.invoke()
        stopVehicle = if (demand.first) HongqiClusterVehicleData.subscribe(context, ::scheduleRefresh, demand.second) else null
        scheduleRefresh()
    }

    private fun stopObservation() {
        observing = false
        subscriptions.forEach { it() }; subscriptions.clear()
        stopVehicle?.invoke(); stopVehicle = null
        vehicleDemand = null
        main.removeCallbacks(refresh)
        main.removeCallbacks(musicRefresh)
        nav = CarPlayGlance.Snapshot(); vehicle = OemClusterVehicleState()
    }

    private fun scheduleRefresh() { main.removeCallbacks(refresh); if (observing) main.post(refresh) }
    private fun scheduleMusicRefresh() { main.removeCallbacks(musicRefresh); if (observing) main.post(musicRefresh) }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!drawingVisible) return
    }

    fun drawComponent(canvas: Canvas, width: Int, height: Int, component: OriginalClusterComponent) {
        if (!drawingVisible) return
        val editing = component.takeIf { preview && selectedId == it.key }
        renderer.draw(canvas, width, height, layout,
            if (preview) previewVehicle else vehicle, if (preview) previewNav else nav,
            editing = editing, only = component,
            musicState = if (preview) previewMusic else if (component == OriginalClusterComponent.MUSIC)
                ClusterInstrumentData.music(context) else emptyMusic)
    }
    fun shouldAnimate(component: OriginalClusterComponent) = observing && component == OriginalClusterComponent.MUSIC &&
        layout[component].visible && renderer.lyricAnimating

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!preview || width == 0 || height == 0) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x / width * OriginalClusterLayout.WIDTH
            val y = event.y / height * OriginalClusterLayout.HEIGHT
            candidate = OriginalClusterComponent.entries.filter { it != OriginalClusterComponent.MAP }
                .lastOrNull { originalHit(layout.bounds(it), layout[it], x, y) }?.key
                ?: layout.media.lastOrNull { originalHit(layout.mediaBounds(it), it.placement, x, y) }?.id
                ?: OriginalClusterComponent.MAP.takeIf { originalHit(layout.bounds(it), layout[it], x, y) }?.key
            candidate?.let { id ->
                val p = placement(id) ?: return@let
                offsetX = event.x / width - p.x; offsetY = event.y / height - p.y
                selectedId = id; onSelectionChanged?.invoke(id)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
        }
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> (dragging ?: candidate)?.let {
                dragging = it
                placement(it)?.let { p ->
                    val next = p.copy(x = event.x / width - offsetX, y = event.y / height - offsetY)
                    val component = OriginalClusterComponent.entries.find { c -> c.key == it }
                    layout = if (component != null) layout.update(component, next) else layout.updateMedia(it, next)
                }
                onLayoutChanged?.invoke(layout)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging == null && event.actionMasked == MotionEvent.ACTION_UP) performClick()
                dragging = null; candidate = null; parent?.requestDisallowInterceptTouchEvent(false)
                invalidate()
            }
        }
        return candidate != null || event.actionMasked == MotionEvent.ACTION_UP
    }

    private fun placement(id: String): OriginalClusterPlacement? =
        OriginalClusterComponent.entries.find { it.key == id }?.let { layout[it] }
            ?: layout.media.find { it.id == id }?.placement

    override fun performClick(): Boolean { super.performClick(); return true }

    companion object {
        private val emptyMusic = ClusterDashboardMusic()
        private val previewMusic = ClusterDashboardMusic(CarPlayNowPlaying(
            title = "City Lights", artist = "The Midnight", lyrics = "City lights glow",
            elapsedMillis = 84000L, durationMillis = 214000L,
        ))
        val previewVehicle = OemClusterVehicleState(tirePressureBar = listOf(2.7f, 2.7f, 2.7f, 2.7f),
            averageFuelConsumption = 8.6f, totalMileageKm = 12680f, remainingMileageKm = 620)
        val previewNav = CarPlayGlance.Snapshot(connected = true, navigating = true, maneuverType = 0,
            distanceMeters = 181000, road = "进入 龙下立交", arrivalEpochSeconds = 1793457180,
            remainingSeconds = 12900, lanes = listOf(CarPlayLane(0, -90, 0), CarPlayLane(1, 0, 2), CarPlayLane(2, 0, 2), CarPlayLane(3, 90, 0)))
    }
}
