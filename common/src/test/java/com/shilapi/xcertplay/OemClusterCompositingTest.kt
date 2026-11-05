package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import com.shilapi.xcertplay.glance.CarPlayGlance
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class OemClusterCompositingTest {
    private fun render(state: ClusterDashboardState = ClusterDashboardState()): Bitmap {
        val renderer = OemClusterRenderer(RuntimeEnvironment.getApplication())
        return Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888).also {
            renderer.draw(Canvas(it), 1920, 720, state, true)
            renderer.release()
        }
    }

    @Test fun independentNeedleLayerMatchesTheCombinedRenderer() {
        val state = ClusterDashboardState(vehicle = OemClusterVehicleState(speedKph = 43f, rpm = 1500f))
        val combined = render(state)
        val split = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
        val renderer = OemClusterRenderer(RuntimeEnvironment.getApplication())
        try {
            val canvas = Canvas(split)
            renderer.draw(canvas, 1920, 720, state, true, includeNeedles = false)
            renderer.drawNeedles(canvas, 1920, 720, 43f, 1500f)
            assertTrue("Separate animation layer must preserve the exact foreground", combined.sameAs(split))
        } finally { combined.recycle(); split.recycle(); renderer.release() }
    }

    @Test fun mapOpeningStaysTransparentBehindCardsAndCircularRims() {
        val foreground = render()
        try {
            for ((x, y) in listOf(900 to 240, 1100 to 360, 950 to 470)) {
                assertEquals("map opening at $x,$y", 0, Color.alpha(foreground.getPixel(x, y)))
            }
            for ((x, y) in listOf(407 to 350, 1492 to 350, 900 to 610)) {
                assertEquals("art/tire panel at $x,$y", 255, Color.alpha(foreground.getPixel(x, y)))
            }
            for ((x, y) in listOf(900 to 140, 900 to 510)) {
                assertEquals("idle browsing must not draw a navigation card", 0, Color.alpha(foreground.getPixel(x, y)))
            }
        } finally { foreground.recycle() }
    }

    @Test fun navigationCardsRequireAConnectedManeuverAndOnlyShowAvailableRouteFields() {
        val empty = render()
        val maneuver = CarPlayGlance.Snapshot(connected = true, navigating = true, maneuverType = 2,
            distanceMeters = 300, road = "测试路段")
        fun sameRegion(a: Bitmap, b: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
            for (y in top until bottom) for (x in left until right) {
                assertEquals("card at $x,$y", a.getPixel(x, y), b.getPixel(x, y))
            }
        }
        try {
            for (nav in listOf(CarPlayGlance.Snapshot(connected = true),
                maneuver.copy(navigating = false, maneuverType = null, remainingMeters = 12300,
                    remainingSeconds = 600, arrivalEpochSeconds = 1792904280),
                maneuver.copy(connected = false, remainingMeters = 12300))) {
                val image = render(ClusterDashboardState(guidance = nav))
                try { sameRegion(empty, image, 720, 105, 1150, 202)
                    sameRegion(empty, image, 640, 503, 1270, 546)
                } finally { image.recycle() }
            }
            val turnOnly = render(ClusterDashboardState(guidance = maneuver))
            val withRoute = render(ClusterDashboardState(guidance = maneuver.copy(remainingMeters = 12300)))
            try {
                assertTrue(Color.alpha(turnOnly.getPixel(900, 140)) >= 245)
                sameRegion(empty, turnOnly, 640, 503, 1270, 546)
                assertTrue(Color.alpha(withRoute.getPixel(900, 515)) >= 245)
                // Missing ETA/duration leaves the right-hand strip empty, without dash text or clock.
                for (y in 506..538) for (x in 920..1220) {
                    val color = withRoute.getPixel(x, y)
                    assertTrue("unexpected placeholder or arrival icon",
                        Color.red(color) < 100 && Color.green(color) < 100 && Color.blue(color) < 100)
                }
            } finally { turnOnly.recycle(); withRoute.recycle() }
        } finally { empty.recycle() }
    }

    @Test fun videoFillsBehindCircularDialsWithoutBroadSideTint() {
        val image = render()
        try {
            assertEquals(255, Color.alpha(image.getPixel(565, 350)))
            for (x in listOf(580, 620, 650, 1300, 1320))
                assertEquals("map must stay clear outside the rim x=$x", 0, Color.alpha(image.getPixel(x, 350)))
            assertEquals(255, Color.alpha(image.getPixel(1340, 350)))
            // Top and bottom meet the slot boundary with only a thin antialias strip.
            assertTrue(Color.alpha(image.getPixel(960, 93)) > Color.alpha(image.getPixel(960, 97)))
            assertEquals(0, Color.alpha(image.getPixel(960, 100)))
            assertEquals(0, Color.alpha(image.getPixel(960, 545)))
            assertTrue(Color.alpha(image.getPixel(960, 552)) > Color.alpha(image.getPixel(960, 549)))
        } finally { image.recycle() }
    }

    @Test fun formerRectangularMapEdgeIsClearOnLightAndDarkVideo() {
        val foreground = render()
        try {
            for (background in listOf(Color.rgb(235, 239, 231), Color.rgb(11, 29, 47))) {
                val composite = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(composite)
                canvas.drawColor(background); canvas.drawBitmap(foreground, 0f, 0f, null)
                try {
                    // Both sides of the previous upper-right seam are now live video.
                    for (y in 100..125 step 5) for (x in 1395..1410)
                        assertEquals("old edge at $x,$y", background, composite.getPixel(x, y))
                } finally { composite.recycle() }
            }
        } finally { foreground.recycle() }
    }

    @Test fun eachTireWarningColorsOnlyItsOwnWheelAndPressureEvenWhenPressureIsUnavailable() {
        fun red(c: Int) = Color.red(c) > 180 && Color.green(c) < 110 && Color.blue(c) < 140
        for (warnedIndex in 0..3) for (missing in listOf(false, true)) {
            val image = render(ClusterDashboardState(vehicle = OemClusterVehicleState(
                tirePressureBar = List(4) { if (missing && it == warnedIndex) null else 2.4f },
                tireWarning = List(4) { it == warnedIndex })))
            try {
                for (i in 0..3) {
                    val left = i % 2 == 0
                    val x = if (left) 875 else 1136
                    val y = if (i < 2) 627 else 675
                    var redTextPixels = 0
                    for (py in ((y-22)*.9375f).toInt() until (y*.9375f).toInt()) {
                        for (px in (x*.9375f).toInt() until ((x+105)*.9375f).toInt()) {
                            if (red(image.getPixel(px, py))) redTextPixels++
                        }
                    }
                    assertEquals("pressure warning tire=$i selected=$warnedIndex", i == warnedIndex, redTextPixels > 20)
                    val wheelX = if (left) 980f else 1027f
                    val wheelY = if (i < 2) 618f else 662f
                    assertEquals("wheel warning tire=$i selected=$warnedIndex", i == warnedIndex,
                        red(image.getPixel(((wheelX+2)*.9375f).toInt(), ((wheelY-3)*.9375f).toInt())))
                }
            } finally { image.recycle() }
        }
    }

    @Test fun navigationDoesNotReintroduceAnInstructionOrDistanceInTheFooter() {
        val plain = render()
        val navigating = render(ClusterDashboardState(guidance = CarPlayGlance.Snapshot(
            connected = true, navigating = true, maneuverType = 2, distanceMeters = 300, road = "道路不可覆盖地图",
            remainingMeters = 12400)))
        try {
            for (y in 648 until 720) for (x in 800 until 1200) {
                assertEquals("Footer changed at $x,$y", plain.getPixel(x, y), navigating.getPixel(x, y))
            }
        } finally { plain.recycle(); navigating.recycle() }
    }

    @Test fun movingNeedlesRemainVisibleThroughoutBothDialSweeps() {
        val absent = render()
        try {
            for (speed in listOf(0f, 10f, 30f, 60f, 90f, 120f, 160f, 200f, 230f, 260f)) {
                val rpm = speed / 260 * 7000
                val image = render(ClusterDashboardState(vehicle = OemClusterVehicleState(speedKph = speed, rpm = rpm)))
                try {
                    for ((cx, angle) in listOf(OemClusterGeometry.DIAL_RIGHT_X to OemClusterGeometry.speedDegrees(speed),
                        OemClusterGeometry.DIAL_LEFT_X to OemClusterGeometry.needleDegrees(rpm, 7000f))) {
                        val radians = Math.toRadians(angle.toDouble())
                        var changed = 0
                        for (r in 154..248) {
                            val x = (cx + kotlin.math.cos(radians) * r).toInt()
                            val y = (OemClusterGeometry.DIAL_Y + kotlin.math.sin(radians) * r).toInt()
                            for (dx in -2..2) for (dy in -2..2) {
                                if (image.getPixel(x + dx, y + dy) != absent.getPixel(x + dx, y + dy)) changed++
                            }
                        }
                        assertTrue("needle disappeared at speed=$speed rpm=$rpm dial=$cx", changed > 90)
                    }
                } finally { image.recycle() }
            }
        } finally { absent.recycle() }
    }

    @Test fun assistanceWorkingStatesDoNotDrawWarningNumbersOrFakeLeadVehicles() {
        val plain = render()
        val active = render(ClusterDashboardState(vehicle = OemClusterVehicleState(
            assistanceStatus = OemClusterAssistanceStatus(acc = 2, fcwWarning = 0, aebWarning = 2,
                hudFcwDisplay = 7, hudAdasDisplay = 1, cruiseMode = 3, aebDisplay = 1))))
        try {
            for (y in 180..540) for (x in 160..540) {
                assertEquals("unverified ADAS state changed the scene", plain.getPixel(x, y), active.getPixel(x, y))
            }
        } finally { plain.recycle(); active.recycle() }
    }

    @Test fun verifiedCollisionWarningsDrawSymbolsAndNeverInventGreenDistanceBars() {
        val plain = render()
        try {
            for (level in listOf(1, 2)) {
                val warned = render(ClusterDashboardState(vehicle = OemClusterVehicleState(
                    assistanceStatus = OemClusterAssistanceStatus(fcwWarning = level))))
                try {
                    var changed = 0
                    for (y in 235..272) for (x in 291..332) {
                        if (warned.getPixel(x, y) != plain.getPixel(x, y)) changed++
                    }
                    assertTrue("missing collision symbol for level $level", changed > 150)
                    for (y in 290..350) for (x in 287..337) {
                        assertEquals("warning invented a target/distance", plain.getPixel(x, y), warned.getPixel(x, y))
                    }
                } finally { warned.recycle() }
            }
        } finally { plain.recycle() }
    }

    @Test fun fuelAndCoolantNeverAddAppValuesOrStrips() {
        val empty = render(ClusterDashboardState(vehicle = OemClusterVehicleState(fuelFraction = 0f, coolantCelsius = 50f)))
        val full = render(ClusterDashboardState(vehicle = OemClusterVehicleState(fuelFraction = 1f, coolantCelsius = 130f)))
        try {
            for (y in 0 until 720) for (x in 0 until 1920) {
                assertEquals("Application drew fuel/coolant at $x,$y", empty.getPixel(x, y), full.getPixel(x, y))
            }
        } finally { empty.recycle(); full.recycle() }
    }

    @Test fun nativeStatusBarIsTheOnlyClockModeBeltAndTemperatureDisplay() {
        val plain = render()
        val active = render(ClusterDashboardState(vehicle = OemClusterVehicleState(
            positionLights = true, lowBeam = true, driveModeCode = 2, seatBeltWarning = true,
            outsideCelsius = 25f, parkingBrake = true)))
        try {
            for (y in 0..70) for (x in 650..1350) {
                assertEquals("duplicate application status bar", plain.getPixel(x, y), active.getPixel(x, y))
            }
        } finally { plain.recycle(); active.recycle() }
    }
}
