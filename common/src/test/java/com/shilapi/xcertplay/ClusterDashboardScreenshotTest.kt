package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.content.res.Configuration
import android.view.View
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "zh-rCN-w1920dp-h720dp-land-mdpi")
class ClusterDashboardScreenshotTest {
    @Test fun copiedMapPixelsReachEveryCustomInstrumentWithoutNavigationMetadata() {
        val context = RuntimeEnvironment.getApplication()
        for (style in listOf(ClusterStyle.BASIC, ClusterStyle.SPORT)) {
            val view = ClusterDashboardView(context, style = style, readState = { ClusterDashboardState() })
            view.measure(View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(720, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, 1920, 720)
            val source = Bitmap.createBitmap(640, 360, Bitmap.Config.RGB_565)
            source.eraseColor(Color.MAGENTA)
            val output = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
            try {
                view.setCopiedMap(source)
                view.refreshState()
                view.draw(Canvas(output))
                val aperture = view.mapViewport
                var copiedPixels = 0
                for (y in aperture.top until aperture.bottom) {
                    for (x in aperture.left until aperture.right) {
                        if (output.getPixel(x, y) == Color.MAGENTA) copiedPixels++
                    }
                }
                assertTrue("$style must show the copied frame without phone navigation metadata", copiedPixels > 1000)
            } finally { output.recycle(); source.recycle() }
        }
    }

    @Test fun fullAndCompactLargeFontLightDarkAndWaiting() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.fontScale = 1.4f
        val fixture = ClusterDashboardState(
            CarPlayGlance.Snapshot(connected = true, maneuverType = 2, distanceMeters = 300,
                road = "沿人民大街行驶", arrivalEpochSeconds = 1792827000,
                remainingMeters = 12400, remainingSeconds = 1560),
            ClusterDashboardMusic(CarPlayNowPlaying(title = "有缘天涯亦咫尺", artist = "音乐信息来自手机", lyrics = "[00:00.00]有缘天涯亦咫尺",
                elapsedMillis = 64000, durationMillis = 240000, playing = true)),
        )
        for ((name, w, h) in listOf(Triple("full-dark", 1920, 720), Triple("compact-dark", 1280, 480),
            Triple("carlink-full", 1920, 720), Triple("carlink-compact", 1280, 480),
            Triple("carlink-arabic", 1920, 720), Triple("full-light", 1920, 720), Triple("waiting", 1920, 720),
            Triple("synthetic-map-fixture", 1920, 720), Triple("full-arabic", 1920, 720),
            Triple("vehicle-integration-fixture-only", 1920, 720),
            Triple("vehicle-integration-compact-fixture-only", 1280, 480),
            Triple("vehicle-integration-arabic-fixture-only", 1920, 720),
            Triple("vehicle-warning-fixture-only", 1920, 720),
            Triple("vehicle-warning-compact-fixture-only", 1280, 480),
            Triple("vehicle-warning-arabic-fixture-only", 1920, 720),
            Triple("road-report-fixture-only", 1920, 720),
            Triple("road-report-compact-fixture-only", 1280, 480),
            Triple("road-report-arabic-fixture-only", 1920, 720))) {
            val renderContext = if (name.contains("arabic")) context.createConfigurationContext(
                Configuration(context.resources.configuration).apply {
                    setLocale(Locale.forLanguageTag("ar"))
                    setLayoutDirection(Locale.forLanguageTag("ar"))
                }) else context
            val view = ClusterDashboardView(renderContext, readState = {
                when {
                    name == "waiting" -> ClusterDashboardState()
                    // Synthetic confirmed /100 speed; unavailable RPM stays unknown.
                    name.startsWith("road-report-") -> fixture.copy(vehicle =
                        Hs5VehicleDisplayPolicy.apply(OemClusterVehicleState(
                            speedKph = Hs5VehicleSpeed.kph(125), rpm = 145.5f, coolantCelsius = 53f,
                            fuelFraction = .22f, gear = "P")))
                    name.startsWith("vehicle-") -> fixture.copy(vehicle = Hs5VehicleSample(
                        speed = Hs5TimedInt(8800, 100), gear = Hs5TimedInt(3, 100),
                        fuel = Hs5TimedInt(if (name.contains("warning")) 9 else 50, 100),
                        fuelWarning = Hs5TimedInt(if (name.contains("warning")) 1 else 0, 100),
                        seatBelt = Hs5TimedInt(0, 100), lowBeam = Hs5TimedInt(1, 100),
                        driveMode = Hs5TimedInt(2, 100), outside = Hs5TimedFloat(17.5f, 100),
                        acc = Hs5TimedInt(2, 100), lka = Hs5TimedInt(1, 100),
                        fcwWarning = Hs5TimedInt(if (name.contains("warning")) 2 else 0, 100),
                        aebWarning = Hs5TimedInt(0, 100),
                        tires = listOf(233, 240, 236, 220).map { Hs5TimedInt(it, 100) },
                        tireWarnings = listOf(if (name.contains("warning")) 1 else 0, 0, 0, 0)
                            .map { Hs5TimedInt(it, 100) },
                        can = Hs5CanReadings(2100f, 90f), canAt = 100).vehicle(100, true))
                    // Demo values exist only in this screenshot fixture, never the APK.
                    name == "synthetic-map-fixture" -> fixture.copy(vehicle = OemClusterVehicleState(
                        speedKph = 0f, rpm = 800f, fuelFraction = .35f, coolantCelsius = 90f,
                        gear = "P", driveMode = "舒适", outsideCelsius = 17f, positionLights = true, lowBeam = true,
                        parkingBrake = true, seatBeltWarning = true,
                        assistance = OemClusterAssistance(true, 5, true, true)))
                    else -> fixture
                }
            }, externalNavigation = if (name.startsWith("carlink-")) ({
                com.shilapi.xcertplay.hud.ClusterTurnGuidance(3, 0, 300, "沿人民大街行驶")
            }) else null)
            view.setNightMode(name != "full-light")
            view.refreshState()
            view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            view.layout(0, 0, w, h)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val file = File("build/reports/cluster-dashboard/$name.png")
            file.parentFile.mkdirs()
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
            assertTrue(file.length() > 10000)
        }

        // Test-only map pattern proves the aperture and gradients against bright map pixels.
        // SurfaceView video cannot be captured by View.draw; this is never an APK fallback.
        val mapFixture = Bitmap.createBitmap(1920, 720, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(mapFixture)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(172, 193, 188)
        canvas.drawRect(OemClusterGeometry.mapBounds(1920, 720), paint)
        paint.color = Color.rgb(225, 232, 220)
        paint.strokeWidth = 18f
        for (i in 0..8) canvas.drawLine(480f, 80f + i * 80, 1440f, 200f + i * 80, paint)
        for (i in 0..9) canvas.drawLine(480f + i * 120, 72f, 600f + i * 120, 648f, paint)
        paint.color = Color.rgb(35, 94, 225)
        paint.strokeWidth = 12f
        canvas.drawLine(960f, 640f, 960f, 100f, paint)
        paint.color = Color.rgb(15, 28, 55)
        paint.textSize = 23f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("地图融合测试图（非 CarPlay 视频）", 960f, 410f, paint)
        val renderer = OemClusterRenderer(context)
        renderer.draw(canvas, 1920, 720, fixture, true)
        renderer.release()
        File("build/reports/cluster-dashboard/map-fixture-only.png").outputStream().use {
            mapFixture.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        mapFixture.recycle()
    }

    @Test fun syntheticMapCompositeUsesTheRealAndroidRenderer() {
        val context = RuntimeEnvironment.getApplication().createConfigurationContext(
            Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply {
                setLocale(Locale.SIMPLIFIED_CHINESE)
                fontScale = 1.4f
            })
        val testMap = syntheticMap(640, 360)
        val cover = syntheticCover(107, 100)
        val state = ClusterDashboardState(
            guidance = CarPlayGlance.Snapshot(connected = true, maneuverType = 2,
                distanceMeters = 500, road = "到达   红旗路", remainingSeconds = 1080,
                arrivalEpochSeconds = 1792904280, remainingMeters = 12400),
            music = ClusterDashboardMusic(CarPlayNowPlaying(title = "风吹过的夏天", artist = "夏日回声",
                elapsedMillis = 156000, durationMillis = 268000, playing = true), cover),
            vehicle = OemClusterVehicleState(speedKph = 0f, rpm = 0f,
                assistance = OemClusterAssistance(true, 0, true, true),
                tirePressureBar = List(4) { 2.4f }, tireWarning = List(4) { false }))
        for ((name, w, h) in listOf(Triple("basic-synthetic-map-composite", 2048, 768),
            Triple("basic-synthetic-map-compact", 1280, 480),
            Triple("basic-idle-composite", 2048, 768), Triple("basic-idle-compact", 1280, 480),
            Triple("basic-no-maneuver-composite", 2048, 768),
            Triple("basic-tire-warning-composite", 2048, 768))) {
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(1, 6, 16))
            canvas.drawBitmap(testMap, null, OemClusterGeometry.mapBounds(w, h),
                Paint(Paint.FILTER_BITMAP_FLAG))
            val renderer = OemClusterRenderer(context)
            val fixture = when {
                name.startsWith("basic-idle") -> state.copy(guidance = CarPlayGlance.Snapshot(connected = true))
                name.startsWith("basic-no-maneuver") -> state.copy(guidance = state.guidance.copy(maneuverType = null))
                name.startsWith("basic-tire-warning") -> state.copy(vehicle = state.vehicle.copy(
                    tireWarning = listOf(true, false, false, true),
                    tirePressureBar = listOf(1.7f, 2.4f, 2.4f, 3.4f)))
                else -> state
            }
            renderer.draw(canvas, w, h, fixture, true)
            renderer.release()
            File("build/reports/cluster-dashboard/$name.png").outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            bitmap.recycle()
        }
        cover.recycle(); testMap.recycle()
    }

    private fun syntheticMap(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height,
        Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(62, 79, 73))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(183, 183, 164); strokeWidth = 9f }
        for (i in 0..8) canvas.drawLine(width * .08f, height * i / 8f, width * .92f,
            height * (i + 1) / 9f, paint)
        paint.color = Color.rgb(203, 194, 166)
        paint.strokeWidth = 5f
        for (i in 0..8) canvas.drawLine(width * i / 8f, height * .08f,
            width * (i + 1) / 9f, height * .92f, paint)
        paint.color = Color.rgb(66, 143, 219)
        paint.strokeWidth = 6f
        canvas.drawLine(width * .5f, height.toFloat(), width * .58f, 0f, paint)
    }

    private fun syntheticCover(width: Int, height: Int): Bitmap = Bitmap.createBitmap(width, height,
        Bitmap.Config.ARGB_8888).also { bitmap ->
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(84, 38, 114))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(236, 174, 88) }
        canvas.drawCircle(width * .5f, height * .45f, width * .28f, paint)
        paint.color = Color.rgb(42, 119, 132)
        canvas.drawRect(0f, height * .75f, width.toFloat(), height.toFloat(), paint)
    }
}
