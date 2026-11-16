package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.glance.CarPlayLane
import com.shilapi.xcertplay.CarPlayMediaKeys

/** A transparent, event-driven overlay. Preview instances never bind the vehicle service. */
internal class OriginalClusterOverlayView(context: Context, private val preview: Boolean = false) : View(context) {
    var layout = OriginalClusterSettings.load(context)
        set(value) { if (field != value) { field = value; invalidate() } }
    var onLayoutChanged: ((OriginalClusterLayout) -> Unit)? = null
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
        nav = CarPlayGlance.snapshot()
        invalidate()
    }
    private val musicRefresh = Runnable { invalidate() }
    private val background by lazy { com.shilapi.xcertplay.newui.display.ClusterStylePreview.create(context, ClusterStyle.ORIGINAL) }
    private var dragging: OriginalClusterComponent? = null
    private var candidate: OriginalClusterComponent? = null
    private var offsetX = 0f
    private var offsetY = 0f
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = candidate != null
        override fun onLongPress(e: MotionEvent) {
            dragging = candidate
            if (dragging != null) {
                parent?.requestDisallowInterceptTouchEvent(true)
                performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                invalidate()
            }
        }
    })

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
        subscriptions += CarPlayGlance.subscribe { scheduleRefresh() }
        subscriptions += CarPlayMediaKeys.subscribeDashboard { scheduleMusicRefresh() }
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
        if (preview) {
            canvas.drawBitmap(background, null, android.graphics.Rect(0, 0, width, height), null)
            renderer.draw(canvas, width, height, layout, previewVehicle, previewNav, editing = dragging)
        } else renderer.draw(canvas, width, height, layout, vehicle, nav)
        if (observing && layout[OriginalClusterComponent.MUSIC].visible && renderer.lyricAnimating) postInvalidateDelayed(33L)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!preview || width == 0 || height == 0) return false
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            val x = event.x / width * OriginalClusterLayout.WIDTH
            val y = event.y / height * OriginalClusterLayout.HEIGHT
            candidate = OriginalClusterComponent.entries.lastOrNull { layout[it].visible && layout.bounds(it).contains(x, y) }
            candidate?.let { offsetX = event.x / width - layout[it].x; offsetY = event.y / height - layout[it].y }
        }
        gestures.onTouchEvent(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> dragging?.let {
                layout = layout.place(it, event.x / width - offsetX, event.y / height - offsetY)
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

    override fun performClick(): Boolean { super.performClick(); return true }

    companion object {
        val previewVehicle = OemClusterVehicleState(tirePressureBar = listOf(2.7f, 2.7f, 2.7f, 2.7f),
            averageFuelConsumption = 8.6f, totalMileageKm = 12680f, remainingMileageKm = 620)
        val previewNav = CarPlayGlance.Snapshot(connected = true, navigating = true, maneuverType = 0,
            distanceMeters = 181000, road = "进入 龙下立交", arrivalEpochSeconds = 1793457180,
            remainingSeconds = 12900, lanes = listOf(CarPlayLane(0, -90, 0), CarPlayLane(1, 0, 2), CarPlayLane(2, 0, 2), CarPlayLane(3, 90, 0)))
    }
}
