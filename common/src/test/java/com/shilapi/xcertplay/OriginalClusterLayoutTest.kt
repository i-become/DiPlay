package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.ViewConfiguration
import com.shilapi.xcertplay.glance.CarPlayGlance
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalClusterLayoutTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Test fun musicCardUsesTheSameTranslucentSurfaceAsVehicleCards() {
        val renderer = OriginalClusterRenderer(context)
        val layout = OriginalClusterLayout()
        val frame = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        try {
            renderer.draw(Canvas(frame), 2048, 768, layout, OemClusterVehicleState(), CarPlayGlance.Snapshot())
            val colors = listOf(OriginalClusterComponent.TIRES, OriginalClusterComponent.DRIVING,
                OriginalClusterComponent.MUSIC).map { component ->
                val r = layout.bounds(component)
                frame.getPixel((r.left + 24).toInt(), (r.bottom - 16).toInt())
            }
            assertEquals(colors[0], colors[1])
            assertEquals(colors[0], colors[2])
            assertTrue(Color.alpha(colors[2]) in 1..254)
        } finally { frame.recycle(); renderer.releaseNavigationIcon() }
    }

    @Test fun routeSummaryAppearsAndClearsAtRouteEnd() {
        val renderer = OriginalClusterRenderer(context)
        val layout = OriginalClusterLayout()
        val bounds = layout.bounds(OriginalClusterComponent.TIME)
        fun draw(nav: CarPlayGlance.Snapshot): Bitmap =
            Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888).also {
                renderer.draw(Canvas(it), 2048, 768, layout, OemClusterVehicleState(), nav)
            }
        val nav = CarPlayGlance.Snapshot(connected = true, navigating = true,
            routeSummary = "18:20 到达   剩余 12.8 公里")
        val active = draw(nav)
        val ended = draw(nav.copy(navigating = false))
        try {
            assertTrue(Color.alpha(active.getPixel(bounds.centerX().toInt(), bounds.centerY().toInt())) > 0)
            assertEquals(0, Color.alpha(ended.getPixel(bounds.centerX().toInt(), bounds.centerY().toInt())))
        } finally { active.recycle(); ended.recycle(); renderer.releaseNavigationIcon() }
    }

    @Test fun visibleOverlayAppliesSavedSettingsWithoutRebuildingTheVideo() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup()
        val overlay = OriginalClusterOverlayView(controller.get())
        controller.get().setContentView(overlay)
        controller.visible()
        shadowOf(Looper.getMainLooper()).idle()
        try {
            assertTrue("Overlay must be attached to receive live settings", overlay.isAttachedToWindow)
            assertTrue("Overlay must be visible", overlay.isShown)
            val edited = OriginalClusterLayout().show(OriginalClusterComponent.DRIVING, false)
                .place(OriginalClusterComponent.TIRES, .2f, .3f)
            OriginalClusterSettings.save(context, edited)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(edited, overlay.layout)
        } finally { controller.pause().stop().destroy() }
    }

    @Test fun savedLayoutRoundTripsAndDamagedPositionsStayOnScreen() {
        val edited = OriginalClusterLayout().place(OriginalClusterComponent.TIRES, .4f, .2f)
            .show(OriginalClusterComponent.DRIVING, false)
        OriginalClusterSettings.save(context, edited)
        assertEquals(edited, OriginalClusterSettings.load(context))
        assertEquals(OriginalClusterLayout(), OriginalClusterLayout.decode("not json"))
        assertEquals(OriginalClusterLayout(), OriginalClusterLayout.decode("{\"version\":2}"))
        val damaged = OriginalClusterLayout.decode("{\"tires\":{\"x\":100,\"y\":-100},\"time\":{\"x\":\"NaN\"}}")
        assertEquals(1f - OriginalClusterComponent.TIRES.width, damaged[OriginalClusterComponent.TIRES].x, .0001f)
        assertEquals(0f, damaged[OriginalClusterComponent.TIRES].y, 0f)
        assertEquals(OriginalClusterComponent.TIME.left, damaged[OriginalClusterComponent.TIME].x, 0f)
    }

    @Test fun originalMusicCardIsVisibleByDefaultAndCanBeMovedAndHidden() {
        val bounds = OriginalClusterLayout().bounds(OriginalClusterComponent.MUSIC)
        assertTrue(bounds.width() > 400f && bounds.height() > 150f)
        val edited = OriginalClusterLayout().place(OriginalClusterComponent.MUSIC, .1f, .2f)
            .show(OriginalClusterComponent.MUSIC, false)
        assertFalse(edited[OriginalClusterComponent.MUSIC].visible)
        assertEquals(.1f, edited[OriginalClusterComponent.MUSIC].x, .0001f)
        assertEquals(edited, OriginalClusterLayout.decode(edited.encode()))
    }

    @Test fun longPressDragsOnlyTheDraftAtTheSameNormalizedPositionOnAnyDisplay() {
        val previewContext = object : ContextWrapper(context) {
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean =
                error("A configuration preview must not bind vehicle services")
        }
        val saved = OriginalClusterLayout()
        OriginalClusterSettings.save(context, saved)
        val view = OriginalClusterOverlayView(previewContext, preview = true)
        view.layout(0, 0, 1024, 384)
        var changed: OriginalClusterLayout? = null
        view.onLayoutChanged = { changed = it }
        val start = saved.bounds(OriginalClusterComponent.TIRES)
        val x = start.centerX() / 2; val y = start.centerY() / 2
        val downAt = SystemClock.uptimeMillis()
        fun touch(action: Int, px: Float, py: Float) {
            MotionEvent.obtain(downAt, SystemClock.uptimeMillis(), action, px, py, 0).also {
                view.onTouchEvent(it); it.recycle()
            }
        }
        touch(MotionEvent.ACTION_DOWN, x, y)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ViewConfiguration.getLongPressTimeout().toLong() + 150))
        touch(MotionEvent.ACTION_MOVE, x + 102.4f, y + 38.4f)
        touch(MotionEvent.ACTION_UP, x + 102.4f, y + 38.4f)
        assertNotNull(changed)
        assertEquals(.145f, changed!![OriginalClusterComponent.TIRES].x, .001f)
        assertEquals(.479f, changed!![OriginalClusterComponent.TIRES].y, .001f)
        assertEquals(saved, OriginalClusterSettings.load(context))
        val scaled = changed!!.bounds(OriginalClusterComponent.TIRES)
        assertEquals(.145f * 2048, scaled.left, .1f)
    }

    @Test fun navigationCardsDisappearWithoutGuidanceAndDisabledComponentsLeaveTransparentVideo() {
        val renderer = OriginalClusterRenderer(context)
        val layout = OriginalClusterComponent.entries.fold(OriginalClusterLayout()) { value, item -> value.show(item, false) }
            .show(OriginalClusterComponent.TURN, true).show(OriginalClusterComponent.TIME, true)
        val bitmap = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            renderer.draw(canvas, 2048, 768, layout, OemClusterVehicleState(), CarPlayGlance.Snapshot())
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(1530, 100))
            assertEquals(Color.TRANSPARENT, bitmap.getPixel(1530, 660))
            renderer.draw(canvas, 2048, 768, layout, OriginalClusterOverlayView.previewVehicle,
                OriginalClusterOverlayView.previewNav.copy(maneuverType = null))
            assertTrue("A lane-only card is still shown", Color.alpha(bitmap.getPixel(1530, 100)) > 0)
            assertTrue(Color.alpha(bitmap.getPixel(1530, 660)) > 0)
            assertEquals("OEM speed area remains transparent", Color.TRANSPARENT, bitmap.getPixel(200, 180))
            assertEquals("Disabled tire widget leaves video visible", Color.TRANSPARENT, bitmap.getPixel(200, 350))
        } finally { bitmap.recycle() }
    }

    @Test fun drivingValuesUseTheTargetAdapterUnitsAndUnknownValuesStayEmpty() {
        val raw = Hs5VehicleSample(pushConnected = true, averageFuelConsumption = Hs5TimedInt(86, 0),
            totalMileage = Hs5TimedInt(12680, 0), remainingMileage = Hs5TimedInt(620, 0))
        val value = raw.vehicle(0, null)
        assertEquals(86f, value.averageFuelConsumption!!, .001f)
        assertEquals(12680f, value.totalMileageKm!!, .001f)
        assertEquals(620, value.remainingMileageKm)
        assertNull(raw.copy(remainingMileage = Hs5TimedInt(1023, 0)).vehicle(0, null).remainingMileageKm)
    }

    @Test @Config(qualifiers = "zh-rCN-w2048dp-h768dp-land-mdpi")
    fun renderedPanelsKeepTransparencyAndGuidanceFitsTheEditableBounds() {
        val renderer = OriginalClusterRenderer(context)
        val bitmap = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val layout = OriginalClusterLayout()
        val nav = OriginalClusterOverlayView.previewNav.copy(lanes = emptyList())
        val folder = java.io.File("build/reports/original-cluster-config").apply { mkdirs() }
        fun save(name: String) = java.io.File(folder, name).outputStream().use {
            assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
        try {
            renderer.draw(canvas, 2048, 768, layout, OriginalClusterOverlayView.previewVehicle, nav)
            assertEquals("Map stays visible through the tire card", 92, Color.alpha(bitmap.getPixel(110, 450)))
            assertEquals("Driving card shares the same glass surface", 92, Color.alpha(bitmap.getPixel(110, 670)))
            assertEquals("Turn card has a separate near-opaque surface", 252, Color.alpha(bitmap.getPixel(1900, 110)))
            assertEquals("Time pill uses the reference charcoal surface", 232, Color.alpha(bitmap.getPixel(1920, 665)))
            assertEquals("Turn card keeps its broad round corner", Color.TRANSPARENT, bitmap.getPixel(1510, 75))
            assertEquals("Guidance stays inside the configured rectangle", Color.TRANSPARENT, bitmap.getPixel(1700, 275))
            assertEquals("Solid straight arrow", Color.WHITE, bitmap.getPixel(1587, 155))
            assertEquals("Dashed tail leaves a gap", 252, Color.alpha(bitmap.getPixel(1587, 167)))
            save("components-transparent.png")
            val background = com.shilapi.xcertplay.newui.display.ClusterStylePreview.create(context, ClusterStyle.ORIGINAL)
            try {
                fun preview(name: String, guidance: CarPlayGlance.Snapshot) {
                    canvas.drawBitmap(background, null, android.graphics.Rect(0, 0, 2048, 768), null)
                    renderer.draw(canvas, 2048, 768, layout, OriginalClusterOverlayView.previewVehicle, guidance)
                    save(name)
                }
                preview("components-preview.png", nav)
                preview("components-with-lanes.png", OriginalClusterOverlayView.previewNav)
            } finally { background.recycle() }
        } finally { bitmap.recycle() }
    }
}
