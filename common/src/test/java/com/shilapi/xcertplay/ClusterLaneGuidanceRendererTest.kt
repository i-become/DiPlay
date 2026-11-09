package com.shilapi.xcertplay

import android.content.res.Configuration
import android.graphics.*
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.glance.CarPlayLane
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "zh-rCN-w2048dp-h768dp-land-mdpi")
class ClusterLaneGuidanceRendererTest {
    private val context get() = RuntimeEnvironment.getApplication().createConfigurationContext(
        Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply {
            setLocale(Locale.SIMPLIFIED_CHINESE); fontScale = 1.4f
        })
    private val lanes = listOf(CarPlayLane(0, -90, 0), CarPlayLane(1, 0, 0),
        CarPlayLane(2, 0, 0), CarPlayLane(3, 0, 2, 90, listOf(0, 90)), CarPlayLane(4, 90, 2))
    private val guidance = CarPlayGlance.Snapshot(connected = true, navigating = true,
        maneuverType = 2, distanceMeters = 500, road = "进入 望京西路", lanes = lanes,
        remainingMeters = 12800, remainingSeconds = 1080)
    private fun renderer(sport: Boolean): ClusterInstrumentRenderer =
        if (sport) SportClusterRenderer(context) else OemClusterRenderer(context)
    private fun draw(sport: Boolean, nav: CarPlayGlance.Snapshot): Bitmap =
        Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888).also {
            val renderer = renderer(sport)
            try { renderer.draw(Canvas(it), 2048, 768, ClusterDashboardState(guidance = nav), true) }
            finally { renderer.release() }
        }
    private fun pixel(bitmap: Bitmap, sport: Boolean, x: Int, y: Int): Int = if (sport) bitmap.getPixel(
        (SportClusterGeometry.offsetX(2048,768) + x * SportClusterGeometry.CONTENT_SCALE).toInt(),
        (SportClusterGeometry.CONTENT_TOP + y * SportClusterGeometry.CONTENT_SCALE).toInt())
        else bitmap.getPixel(x, y)

    @Test fun turnAndLanesCollapseIndependentlyWhileRouteTotalsRemainIndependent() {
        for (sport in listOf(false, true)) {
            val with = draw(sport, guidance)
            val without = draw(sport, guidance.copy(lanes = emptyList()))
            val lanesOnly = draw(sport, guidance.copy(maneuverType = null))
            val noCard = draw(sport, guidance.copy(maneuverType = null, lanes = emptyList()))
            val noRoute = draw(sport, guidance.copy(navigating = false))
            val disconnected = draw(sport, guidance.copy(connected = false))
            try {
                val x = if (sport) 1620 else 997
                val top = if (sport) 156 else 112
                val y = top + 120
                assertTrue(Color.alpha(pixel(with, sport, x, y)) >= 245)
                assertEquals(0, Color.alpha(pixel(without, sport, x, y)))
                assertTrue(Color.alpha(pixel(lanesOnly, sport, x, top + 20)) >= 245)
                assertEquals(0, Color.alpha(pixel(lanesOnly, sport, x, top + 64)))
                assertEquals(0, Color.alpha(pixel(noCard, sport, x, top + 20)))
                assertEquals(0, Color.alpha(pixel(noRoute, sport, x, y)))
                assertEquals(0, Color.alpha(pixel(disconnected, sport, x, y)))
                // Summary is near the map's lower edge, not appended below the lane row.
                for (image in listOf(with, lanesOnly, noCard)) {
                    assertTrue(Color.alpha(pixel(image, sport, if (sport) 1698 else 900, if (sport) 605 else 550)) >= 239)
                }
                assertEquals(0, Color.alpha(pixel(noRoute, sport, if (sport) 1698 else 900, if (sport) 605 else 550)))
                // The central map remains uncovered between both components.
                assertEquals(0, Color.alpha(pixel(with, sport, x, 420)))
            } finally { listOf(with, without, lanesOnly, noCard, noRoute, disconnected).forEach { it.recycle() } }
        }
    }

    @Test fun turnWidthFollowsTextAndSportTotalsPackOnlyAvailableValuesAtTheLowerEdge() {
        for (sport in listOf(false, true)) {
            val short = draw(sport, guidance.copy(road = "向右", lanes = emptyList()))
            val long = draw(sport, guidance.copy(road = "进入红旗大道辅路向东行驶", lanes = emptyList()))
            try {
                val x = if (sport) 1780 else 1160
                val y = if (sport) 170 else 125
                assertEquals(0, Color.alpha(pixel(short, sport, x, y)))
                assertTrue(Color.alpha(pixel(long, sport, x, y)) >= 245)
            } finally { short.recycle(); long.recycle() }
        }
        for (nav in listOf(guidance, guidance.copy(remainingSeconds = null), guidance.copy(remainingMeters = null))) {
            val image = draw(true, nav.copy(maneuverType = null, lanes = emptyList()))
            try {
                assertTrue(Color.alpha(pixel(image, true, 1698, 605)) >= 239)
                // The previous full-width bar and its empty middle gap are gone.
                assertEquals(0, Color.alpha(pixel(image, true, 1450, 605)))
                // Keep the curved red frame visible to the right of the content-sized strip.
                val bare = draw(true, CarPlayGlance.Snapshot())
                try { assertEquals(pixel(bare, true, 1940, 605), pixel(image, true, 1940, 605)) }
                finally { bare.recycle() }
                assertEquals(0, Color.alpha(pixel(image, true, 1698, 570)))
            } finally { image.recycle() }
        }
        val none = draw(true, guidance.copy(remainingSeconds = null, remainingMeters = null))
        try { assertEquals(0, Color.alpha(pixel(none, true, 1698, 605))) } finally { none.recycle() }
    }

    @Test fun splitLaneHighlightsOnlyTheSelectedBranchAndDoesNotMirrorForRtl() {
        val strip = ClusterLaneStrip()
        val image = Bitmap.createBitmap(160, 64, Bitmap.Config.ARGB_8888)
        val cyan = Color.CYAN
        try {
            strip.draw(Canvas(image), RectF(0f, 0f, 160f, 64f), listOf(CarPlayLane(0, 0, 2, 90, listOf(0, 90))), cyan)
            fun cyanPixels(x1: Int, y1: Int, x2: Int, y2: Int) = (y1..y2).sumOf { y ->
                (x1..x2).count { x -> image.getPixel(x,y) == cyan }
            }
            assertTrue(cyanPixels(90, 21, 100, 30) > 8) // Right branch.
            assertEquals(0, cyanPixels(77, 6, 83, 17)) // Straight branch remains grey.
            assertTrue(cyanPixels(72, 51, 88, 56) > 20) // Underline gives a non-colour cue.
            assertFalse(strip.fits(List(16) { CarPlayLane(it, 0, 0) }, 349f))
            assertTrue(strip.fits(List(8) { CarPlayLane(it, 0, 0) }, 349f))
            assertFalse(strip.fits(listOf(CarPlayLane(0, 1000, 2, directions = emptyList())), 349f))
        } finally { image.recycle() }
    }

    @Test fun nativeLanePreviewsAtFullAndCompactSizes() {
        for (sport in listOf(false, true)) {
            val name = if (sport) "sport" else "urban"
            val reference = javaClass.classLoader!!.getResourceAsStream(
                "cluster/${if (sport) "sport" else "basic"}-reference.png")!!.use { BitmapFactory.decodeStream(it) }
            val crop = if (sport) Rect(1528,320,1928,620) else Rect(712,300,1310,516)
            val cover = if (sport) Bitmap.createBitmap(reference,976,239,96,96)
                else Bitmap.createBitmap(reference,1534,225,107,100)
            try {
                for ((suffix, width, height) in listOf(Triple("full",2048,768), Triple("compact",1280,480),
                    Triple("eight-lanes",1280,480), Triple("no-lanes",1280,480),
                    Triple("lanes-only",1280,480), Triple("totals-only",1280,480),
                    Triple("no-navigation",1280,480), Triple("rtl",1280,480))) {
                    val bitmap = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap); canvas.drawColor(Color.rgb(2,3,5))
                    val map = if (sport) SportClusterGeometry.mapBounds(width,height) else OemClusterGeometry.mapBounds(width,height)
                    canvas.drawBitmap(reference,crop,map,Paint(Paint.FILTER_BITMAP_FLAG))
                    val nav = when (suffix) {
                        "eight-lanes" -> guidance.copy(road = "进入 红旗大街辅路向东行驶", lanes = List(8) {
                            CarPlayLane(it, if (it == 0) -90 else 0, if (it >= 6) 2 else 0,
                                if (it >= 6) 90 else null, if (it >= 6) listOf(0, 90) else listOf(if (it == 0) -90 else 0))
                        })
                        "no-lanes" -> guidance.copy(lanes = emptyList())
                        "lanes-only" -> guidance.copy(maneuverType = null)
                        "totals-only" -> guidance.copy(maneuverType = null, lanes = emptyList())
                        "no-navigation" -> guidance.copy(navigating = false)
                        else -> guidance
                    }
                    val state = ClusterDashboardState(guidance = nav,
                        music = ClusterDashboardMusic(CarPlayNowPlaying(title="晴天",artist="周杰伦",
                            elapsedMillis=112000,durationMillis=269000,playing=true),cover),
                        vehicle = OemClusterVehicleState(speedKph=80f,rpm=2300f,
                            assistance=OemClusterAssistance(true,0,true,true),
                            tirePressureBar=List(4){2.4f},tireWarning=List(4){false}))
                    val renderer = if (suffix == "rtl") {
                        val rtl = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                            setLocale(Locale.forLanguageTag("ar")); fontScale = 1.4f
                        })
                        if (sport) SportClusterRenderer(rtl) else OemClusterRenderer(rtl)
                    } else renderer(sport)
                    try { renderer.draw(canvas,width,height,state,true) } finally { renderer.release() }
                    val file=File("build/reports/cluster-dashboard/$name-lanes-$suffix.png")
                    requireNotNull(file.parentFile).mkdirs()
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
                    bitmap.recycle(); assertTrue(file.length()>10000)
                }
            } finally { cover.recycle(); reference.recycle() }
        }
    }

    @Test fun handoffLaneCountsDayNightAndNativeOverlayPreviews() {
        val cases = listOf(
            listOf(CarPlayLane(0, -180, 2)),
            listOf(CarPlayLane(0, -90, 0), CarPlayLane(1, 0, 2),
                CarPlayLane(2, 0, 2, 90, listOf(0, 90)), CarPlayLane(3, 180, 1)),
            List(12) { CarPlayLane(it, if (it == 0) -180 else if (it == 11) 180 else 0, if (it in 5..6) 2 else 0) },
        )
        for (night in listOf(false, true)) for (width in listOf(640, 1280)) for (lanes in cases) {
            val bitmap = Bitmap.createBitmap(width, 360, Bitmap.Config.ARGB_8888)
            try {
                val canvas = Canvas(bitmap)
                canvas.drawColor(if (night) Color.rgb(15, 20, 30) else Color.rgb(215, 220, 210))
                val view = ClusterTurnCardView(context)
                view.setLayout(50, 25, 70)
                view.setNightMode(night)
                view.setGuidance(com.shilapi.xcertplay.hud.ClusterTurnGuidance(0, 0, 0, "", lanes = lanes))
                view.layout(0, 0, width, 360)
                view.draw(canvas)
                val file = File("build/reports/cluster-dashboard/native-lanes-${lanes.size}-${width}-${if (night) "night" else "day"}.png")
                file.parentFile!!.mkdirs()
                file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                assertTrue(file.length() > 1000)
                assertTrue(ClusterLaneStrip().fits(lanes, width - 24f))
            } finally { bitmap.recycle() }
        }
    }
}
