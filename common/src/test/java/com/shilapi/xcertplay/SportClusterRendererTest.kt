package com.shilapi.xcertplay

import android.graphics.*
import android.content.res.Configuration
import android.view.View
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "zh-rCN-w2048dp-h768dp-land-mdpi")
class SportClusterRendererTest {
    private val context get() = RuntimeEnvironment.getApplication().createConfigurationContext(
        Configuration(RuntimeEnvironment.getApplication().resources.configuration).apply { setLocale(Locale.SIMPLIFIED_CHINESE) })
    private fun render(state: ClusterDashboardState = ClusterDashboardState(), split: Boolean = false): Bitmap {
        val image = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        val renderer = SportClusterRenderer(context)
        try {
            val canvas = Canvas(image)
            renderer.draw(canvas, 2048, 768, state, true, includeNeedles = !split)
            if (split) renderer.drawNeedles(canvas, 2048, 768, state.vehicle.speedKph, state.vehicle.rpm)
        } finally { renderer.release() }
        return image
    }
    private fun pixel(image: Bitmap, x: Int, y: Int): Int = image.getPixel(
        (SportClusterGeometry.offsetX(2048, 768) + x * SportClusterGeometry.horizontalScale(2048, 768)).toInt(),
        (SportClusterGeometry.CONTENT_TOP + y * SportClusterGeometry.CONTENT_SCALE).toInt())

    private fun assertSameRegion(a: Bitmap, b: Bitmap, left: Int, top: Int, right: Int, bottom: Int) {
        for (y in top until bottom) for (x in left until right)
            assertEquals("pixel at $x,$y", pixel(a, x,y), pixel(b, x,y))
    }

    @Test fun sportSurfaceAndStreamCoverTheEntireRedFrameWithoutStretching() {
        for ((w,h) in listOf(2048 to 768, 1280 to 480, 1920 to 1080)) {
            val expected = SportClusterGeometry.mapBounds(w,h)
            assertEquals(4f/3f/SportClusterGeometry.CONTENT_SCALE, expected.width()/expected.height(), .001f)
            // The surface intentionally overscans beneath the tachometer to soften the lower-left join.
            assertTrue(expected.left > w*.58f)
            assertTrue(expected.right <= w)
            val view = ClusterDashboardView(context, style = ClusterStyle.SPORT)
            view.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY))
            view.layout(0,0,w,h)
            assertTrue(kotlin.math.abs(expected.left.toInt() - view.mapViewport.left) <= 1)
            assertTrue(kotlin.math.abs(expected.top.toInt() - view.mapViewport.top) <= 1)
        }
        val config = ClusterStyle.SPORT.streamConfig(2048,768,100,50,50)
        assertEquals(4f/3f, config.widthPixels.toFloat()/config.heightPixels, .001f)
        assertEquals(0, config.widthPixels%8)
        assertEquals(0, config.heightPixels%8)
    }

    @Test fun contentMovesBelowOemHeaderWithoutReducingBottomIconClearance() {
        val top = SportClusterGeometry.offsetY(2048, 768) + 52 * SportClusterGeometry.scale(2048, 768)
        val bottom = SportClusterGeometry.offsetY(2048, 768) + 700 * SportClusterGeometry.scale(2048, 768)
        assertTrue(top > 72)
        assertTrue(bottom <= 700)
    }

    @Test fun speedCardIsAbsentAndFuelCoolantStayOwnedByTheCar() {
        val empty = render()
        val live = render(ClusterDashboardState(vehicle = OemClusterVehicleState(speedKph=80f,rpm=2300f,
            gear="D",fuelFraction=.75f,coolantCelsius=96f,seatBeltWarning=true,outsideCelsius=30f)))
        try {
            assertSameRegion(empty,live,75,129,580,269)
            assertSameRegion(empty,live,100,710,1948,768)
            // Bright demo digits/bevel must not survive in the OEM speed rectangle.
            for(y in 144..254) for(x in 135..545) {
                val color=pixel(empty, x,y)
                assertTrue(Color.red(color)<30 && Color.green(color)<30 && Color.blue(color)<30)
            }
        } finally { empty.recycle();live.recycle() }
    }

    @Test fun separateNeedlesMatchAndRemainVisibleAcrossBothSweeps() {
        val plain = render()
        try {
            for(speed in listOf(0f,10f,40f,80f,120f,180f,230f,260f)) {
                val state=ClusterDashboardState(vehicle=OemClusterVehicleState(speedKph=speed,rpm=speed/260*7000))
                val combined=render(state);val split=render(state,true)
                try {
                    assertTrue(combined.sameAs(split))
                    for((angle,radius) in listOf(OemClusterGeometry.speedDegrees(speed) to 260,
                        OemClusterGeometry.needleDegrees(speed/260*7000,7000f) to 170)) {
                        val a=Math.toRadians(angle.toDouble())
                        val x=(1024+kotlin.math.cos(a)*radius).toInt()
                        val y=(358+kotlin.math.sin(a)*radius).toInt()
                        var changed=0
                        for(dx in -3..3) for(dy in -3..3)
                            if(pixel(plain, x+dx,y+dy)!=pixel(combined, x+dx,y+dy))changed++
                        assertTrue("missing needle at speed=$speed radius=$radius",changed>5)
                    }
                } finally { combined.recycle();split.recycle() }
            }
        } finally {plain.recycle()}
    }

    @Test fun mapApertureIsClearAndGuidanceRequiresAConnectedManeuver() {
        val empty=render()
        try {
            assertEquals(0, Color.alpha(pixel(empty, 1700,430)))
            assertEquals(0, Color.alpha(pixel(empty, 1550,220)))
            // The old rectangular top (140) and bottom (632) must no longer limit the map.
            assertEquals(0, Color.alpha(pixel(empty, 1700,115)))
            assertEquals(0, Color.alpha(pixel(empty, 1500,610)))
            assertTrue(Color.alpha(pixel(empty, 1700,94)) > Color.alpha(pixel(empty, 1700,106)))
            assertEquals(0, Color.alpha(pixel(empty, 1700,106)))
            assertTrue(Color.alpha(pixel(empty, 1700,626)) > Color.alpha(pixel(empty, 1700,613)))
            assertEquals(0, Color.alpha(pixel(empty, 1700,613)))
            assertTrue(Color.alpha(pixel(empty, 1700,86)) >= 248)
            for(nav in listOf(CarPlayGlance.Snapshot(connected=true),
                CarPlayGlance.Snapshot(connected=false,maneuverType=2),
                CarPlayGlance.Snapshot(connected=true,remainingMeters=12800))) {
                val image=render(ClusterDashboardState(guidance=nav))
                try {assertSameRegion(empty,image,1700,72,2000,230)}finally{image.recycle()}
            }
            val image=render(ClusterDashboardState(guidance=CarPlayGlance.Snapshot(connected=true,navigating=true,
                maneuverType=2,distanceMeters=1200,road="望京西路")))
            try {
                assertTrue("guidance card stays at the marked upper map corner", Color.alpha(pixel(image, 1400,130))>=248)
                assertSameRegion(empty,image,1700,72,2000,230)
                assertSameRegion(empty,image,1100,180,1340,315)
            }finally{image.recycle()}
        }finally{empty.recycle()}
    }

    @Test fun topScaleIsFixedAt120AndHasNoSpeedUnitOrLiveReadout() {
        val empty = render()
        val image = render(ClusterDashboardState(vehicle = OemClusterVehicleState(speedKph=80f,rpm=2300f)))
        try {
            assertSameRegion(empty,image,980,85,1070,149)
            // The fixed 120 scale label occupies the top tick, even without vehicle data.
            var digits = 0
            for(y in 80..110) for(x in 1000..1047) {
                val color = pixel(empty,x,y)
                if(Color.red(color)>180 && Color.green(color)>180) digits++
            }
            assertTrue("top tick must include its fixed scale label",digits>60)
            // No extra km/h under that label; only the original dark scale well remains.
            for(y in 120..145) for(x in 995..1053)
                assertTrue("unexpected unit beneath 120", Color.red(pixel(empty,x,y))<100)
        } finally {empty.recycle();image.recycle()}
    }

    @Test fun navigationSummaryEndsWithRemainingDistance() {
        val renderer = SportClusterRenderer(context)
        try {
            val nav = CarPlayGlance.Snapshot(connected = true, navigating = true,
                arrivalEpochSeconds = 1793457180, remainingSeconds = 12900, remainingMeters = 24000)
            val arrival = context.getString(com.shilapi.xcertplay.host.R.string.settings_original_cluster_arrival,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(nav.arrivalEpochSeconds!! * 1000)))
            val duration = context.getString(com.shilapi.xcertplay.host.R.string.original_cluster_hours_minutes, 3, 35)
            val distance = context.getString(com.shilapi.xcertplay.host.R.string.original_cluster_kilometers, "24")
            assertEquals(listOf(arrival, duration, distance).joinToString("   "), renderer.navigationSummary(nav))
        } finally { renderer.release() }
    }

    @Test fun onlyTheWarnedTireAndItsPressureTurnRed() {
        for(warned in 0..3) {
            val image=render(ClusterDashboardState(vehicle=OemClusterVehicleState(
                tirePressureBar=List(4){2.4f},tireWarning=List(4){it==warned})))
            try {
                for(i in 0..3) {
                    val x=if(i%2==0)998 else 1050
                    val y=if(i<2)641 else 685
                    val c=pixel(image, x,y)
                    assertEquals(i==warned,Color.red(c)>200 && Color.green(c)<100)
                    var red=0
                    val tx=if(i%2==0)893 else 1087
                    for(py in y-23..y+5) for(px in tx..tx+80) {
                        val p=pixel(image, px,py)
                        if(Color.red(p)>200 && Color.green(p)<100)red++
                    }
                    assertEquals(i==warned,red>20)
                }
            }finally{image.recycle()}
        }
    }

    @Test fun nativeScreenshotsAtFullCompactWaitingAndWarningStates() {
        val testMap=syntheticMap()
        val cover=syntheticCover()
        val state=ClusterDashboardState(
            guidance=CarPlayGlance.Snapshot(connected=true,navigating=true,maneuverType=2,distanceMeters=1200,
                road="进入 望京西路",remainingMeters=12800,remainingSeconds=1080),
            music=ClusterDashboardMusic(CarPlayNowPlaying(title="晴天",artist="周杰伦",elapsedMillis=112000,
                durationMillis=269000,playing=true,lyrics="故事的小黄花"),cover),
            vehicle=OemClusterVehicleState(speedKph=80f,rpm=2300f,
                assistance=OemClusterAssistance(true,0,false,false),
                tirePressureBar=listOf(2.4f,2.4f,2.5f,2.5f),tireWarning=List(4){false}))
        try {
            for((name,w,h) in listOf(Triple("sport-synthetic-composite",2048,768),
                Triple("sport-synthetic-compact",1280,480),Triple("sport-idle",2048,768),
                Triple("sport-tire-warning",2048,768),Triple("sport-waiting",2048,768),
                Triple("sport-carlink-summary",2048,768))) {
                val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
                val canvas=Canvas(image);canvas.drawColor(Color.rgb(2,3,5))
                // The synthetic map is test content and is never shipped in APK resources.
                if (name != "sport-waiting") {
                    canvas.drawBitmap(testMap,null,SportClusterGeometry.mapBounds(w,h),Paint(Paint.FILTER_BITMAP_FLAG))
                }
                val fixture=when(name) {
                    "sport-idle"->state.copy(guidance=CarPlayGlance.Snapshot(connected=true))
                    "sport-tire-warning"->state.copy(vehicle=state.vehicle.copy(tireWarning=listOf(true,false,false,true)))
                    "sport-waiting"->ClusterDashboardState()
                    "sport-carlink-summary"->state.copy(guidance=state.guidance.copy(routeSummary="18:20 到达   剩余 12.8 公里"))
                    else->state
                }
                val renderer=SportClusterRenderer(context)
                try{renderer.draw(canvas,w,h,fixture,true)}finally{renderer.release()}
                val file=File("build/reports/cluster-dashboard/$name.png");requireNotNull(file.parentFile).mkdirs()
                file.outputStream().use{image.compress(Bitmap.CompressFormat.PNG,100,it)};image.recycle()
                assertTrue(file.length()>10000)
            }
        }finally{cover.recycle();testMap.recycle()}
    }

    private fun syntheticMap():Bitmap=Bitmap.createBitmap(640,480,Bitmap.Config.ARGB_8888).also{bitmap->
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(64,79,72))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(184,184,164);strokeWidth=10f}
        for(i in 0..8)canvas.drawLine(0f,i*60f,640f,(i+1)*54f,paint)
        paint.color=Color.rgb(207,197,168);paint.strokeWidth=5f
        for(i in 0..8)canvas.drawLine(i*70f,0f,(i+1)*64f,480f,paint)
        paint.color=Color.rgb(63,145,218);paint.strokeWidth=7f
        canvas.drawLine(220f,480f,420f,0f,paint)
    }

    private fun syntheticCover():Bitmap=Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).also{bitmap->
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(74,38,112))
        val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.rgb(239,180,86)}
        canvas.drawCircle(48f,43f,29f,paint);paint.color=Color.rgb(42,119,132)
        canvas.drawRect(0f,72f,96f,96f,paint)
    }
}

