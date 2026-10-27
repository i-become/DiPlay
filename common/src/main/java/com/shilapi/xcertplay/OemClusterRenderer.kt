package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path

import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.host.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Null means unavailable, never zero. Only verified car interfaces may populate these fields.
 * No GPS speed, demo RPM, remembered gear or assumed ACC targets are used by this screen. */
internal data class OemClusterVehicleState(
    val speedKph: Float? = null,
    val rpm: Float? = null,
    val fuelFraction: Float? = null,
    val lowFuelWarning: Boolean? = null,
    val coolantCelsius: Float? = null,
    val gear: String? = null,
    val driveMode: String? = null,
    val driveModeCode: Int? = null,
    val outsideCelsius: Float? = null,
    val positionLights: Boolean? = null,
    val lowBeam: Boolean? = null,
    val seatBeltWarning: Boolean? = null,
    val parkingBrake: Boolean? = null,
    val assistance: OemClusterAssistance? = null,
    val assistanceStatus: OemClusterAssistanceStatus? = null,
    // Wheel order follows the Qisi protocol: front-left, front-right, rear-left, rear-right.
    val tirePressureBar: List<Float?> = emptyList(),
    val tireWarning: List<Boolean?> = emptyList(),
) {
    fun validated() = copy(
        speedKph = speedKph.validIn(0f, 260f), rpm = rpm.validIn(0f, 8000f),
        fuelFraction = fuelFraction.validIn(0f, 1f), coolantCelsius = coolantCelsius.validIn(-40f, 150f),
        outsideCelsius = outsideCelsius.validIn(-50f, 70f),
        gear = gear?.takeIf { it in setOf("P", "R", "N", "D", "S", "M", "W", "E", "B", "D1", "D2", "D3", "D4", "D5", "D6") },
        driveModeCode = driveModeCode?.takeIf { it in 1..10 },
        assistanceStatus = assistanceStatus?.validated(),
        tirePressureBar = tirePressureBar.take(4).map { it.validIn(0f, 7f) },
        tireWarning = tireWarning.take(4),
    )

    private fun Float?.validIn(low: Float, high: Float) = this?.takeIf { it.isFinite() && it in low..high }
}

/** Function state is independent of the detected objects used by OemClusterAssistance. */
internal data class OemClusterAssistanceStatus(
    val acc: Int? = null,
    val fcw: Int? = null,
    val aeb: Int? = null,
    val ldw: Int? = null,
    val lka: Int? = null,
    val fcwWarning: Int? = null,
    val aebWarning: Int? = null,
    // Display enums remain raw until the supplied kit verifies their icon meanings.
    val hudFcwDisplay: Int? = null,
    val hudAdasDisplay: Int? = null,
    val cruiseMode: Int? = null,
    val aebDisplay: Int? = null,
) {
    fun validated(): OemClusterAssistanceStatus? = copy(
        acc = acc?.takeIf { it in 0..3 }, fcw = fcw?.takeIf { it in 0..3 },
        aeb = aeb?.takeIf { it in 0..3 }, ldw = ldw?.takeIf { it in 0..3 },
        lka = lka?.takeIf { it in 0..3 }, fcwWarning = fcwWarning?.takeIf { it in 0..2 },
        aebWarning = aebWarning?.takeIf { it in 0..2 },
        hudFcwDisplay = hudFcwDisplay?.takeIf { it >= 0 },
        hudAdasDisplay = hudAdasDisplay?.takeIf { it >= 0 },
        cruiseMode = cruiseMode?.takeIf { it >= 0 },
        aebDisplay = aebDisplay?.takeIf { it >= 0 },
    ).takeUnless { it.acc == null && it.fcw == null && it.aeb == null && it.ldw == null &&
        it.lka == null && it.fcwWarning == null && it.aebWarning == null &&
        it.hudFcwDisplay == null && it.hudAdasDisplay == null && it.cruiseMode == null && it.aebDisplay == null }
}

internal data class OemClusterAssistance(
    val leadVehicleDetected: Boolean,
    val followingBars: Int = 0,
    val leftLaneDetected: Boolean = false,
    val rightLaneDetected: Boolean = false,
)

/** Geometry is shared by Android layout, map negotiation and screenshots. All units are
 * in the rectified 1920 x 720 reference plane, independently of density or window size. */
internal object OemClusterGeometry {
    const val WIDTH = 1920f
    const val HEIGHT = 720f
    // Leave the OEM status glyphs in place and reclaim the excess space below them.
    const val STATUS_BAR_HEIGHT = 80f * .9375f
    const val DIAL_OUTWARD_OFFSET = 100f
    const val DIAL_LEFT_X = (434f - DIAL_OUTWARD_OFFSET) * .9375f
    const val DIAL_RIGHT_X = (1591f + DIAL_OUTWARD_OFFSET) * .9375f
    const val DIAL_TOP = 99f * .9375f
    const val FOOTER_TOP = 590f * .9375f
    // Video fills the middle behind both foreground dials, down to the tire module.
    const val MAP_LEFT = DIAL_LEFT_X
    const val MAP_TOP = DIAL_TOP
    const val MAP_WIDTH = DIAL_RIGHT_X - DIAL_LEFT_X
    const val MAP_HEIGHT = FOOTER_TOP - DIAL_TOP
    const val MAP_ASPECT = MAP_WIDTH / MAP_HEIGHT
    // Coordinates follow the approved 2048x768 artwork, fitted to the 1920x720 plane.
    const val DIAL_SCALE = 1f
    const val DIAL_RADIUS = 277f * .9375f
    const val DIAL_Y = 373f * .9375f



    /** The video stays rectangular; the instrument foreground masks it at the OEM rims. */
    fun mapAperture() = Path().apply {
        addRect(MAP_LEFT, MAP_TOP, MAP_LEFT + MAP_WIDTH, MAP_TOP + MAP_HEIGHT, Path.Direction.CW)
        val instruments = Path().apply {
            addCircle(DIAL_LEFT_X, DIAL_Y, DIAL_RADIUS, Path.Direction.CW)
            addCircle(DIAL_RIGHT_X, DIAL_Y, DIAL_RADIUS, Path.Direction.CW)
        }
        op(instruments, Path.Op.DIFFERENCE)
    }

    fun mapBounds(width: Int, height: Int, sourceAspect: Float = MAP_ASPECT): RectF {
        val scale = min(width.coerceAtLeast(0) / WIDTH, height.coerceAtLeast(0) / HEIGHT)
        val aspect = sourceAspect.takeIf { it.isFinite() && it > 0 } ?: MAP_ASPECT
        val w = min(MAP_WIDTH, MAP_HEIGHT * aspect)
        val h = w / aspect
        val x = (width - WIDTH * scale) / 2 + (MAP_LEFT + (MAP_WIDTH - w) / 2) * scale
        val y = (height - HEIGHT * scale) / 2 + (MAP_TOP + (MAP_HEIGHT - h) / 2) * scale
        return RectF(x, y, x + w * scale, y + h * scale)
    }

    // Retain the actual HS5 scales rather than the illustrative reference numbers.
    fun needleDegrees(value: Float, maximum: Float): Float =
        140f + 260f * (value / maximum).coerceIn(0f, 1f)

    fun speedDegrees(kph: Float): Float {
        val value = kph.coerceIn(0f, 260f)
        return when {
            value <= 200f -> 140f + value * 214f / 200f
            value <= 230f -> 354f + (value - 200f) * 23f / 30f
            else -> 377f + (value - 230f) * 23f / 30f
        }
    }

    fun streamConfig(panelWidth: Int, panelHeight: Int, scalePercent: Int,
                     markerXPercent: Int = 50, markerYPercent: Int = 45) = run {
        val viewport = mapBounds(panelWidth, panelHeight)
        val density = ClusterMapRenderPolicy.scalePercent(scalePercent)
        val height = (viewport.height() * density / 100 / 8).roundToInt().coerceAtLeast(1) * 8
        val width = (height * MAP_ASPECT / 8).roundToInt().coerceAtLeast(1) * 8
        CarPlayClusterDisplay.config(width, height, scalePercent = 100,
            content = CarPlayClusterDisplay.Content.MAP,
            markerXPercent = markerXPercent, markerYPercent = markerYPercent,
            // Some map apps place their navigation marker at the safe area's trailing edge,
            // rather than its centre. A narrow anchor corridor bounds either placement near
            // the driver's chosen X, while drawOutside retains the full-width map. Use the
            // same declaration before/during navigation; never resize the live video surface.
            baseSafeArea = AirPlayInsets(top = 8, bottom = 8, left = 48, right = 48))
    }
}

/** Static artwork follows the supplied reference; live content never comes from its sample data.
 * The map remains a decoder-owned SurfaceView behind a cached, transparent foreground.
 */
internal class OemClusterRenderer(private val context: Context) : ClusterInstrumentRenderer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val images = mutableMapOf<Int, Bitmap>()
    private val white = Color.rgb(234, 247, 255)
    private val cyan = Color.rgb(19, 213, 255)
    private val muted = Color.rgb(166, 191, 222)
    private val navy = Color.rgb(0, 18, 39)
    private val typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val date = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())

    override fun draw(canvas: Canvas, width: Int, height: Int, state: ClusterDashboardState, @Suppress("UNUSED_PARAMETER") night: Boolean,
             includeNeedles: Boolean) {
        val scale = min(width / 2048f, height / 768f)
        if (scale <= 0) return
        canvas.save()
        canvas.translate((width - 2048 * scale) / 2, (height - 768 * scale) / 2)
        canvas.scale(scale, scale)
        reset()
        canvas.drawBitmap(image(R.drawable.cluster_basic_face), null, RectF(0f, 0f, 2048f, 768f), paint)
        val car = state.vehicle.validated()
        canvas.save(); canvas.translate(-OemClusterGeometry.DIAL_OUTWARD_OFFSET, 0f)
        assistance(canvas, car.assistance, car.assistanceStatus?.fcwWarning)
        canvas.restore()
        canvas.save(); canvas.translate(OemClusterGeometry.DIAL_OUTWARD_OFFSET, 0f)
        music(canvas, state)
        canvas.restore()
        navigation(canvas, state)
        tires(canvas, car)
        if (includeNeedles) needles(canvas, car.speedKph, car.rpm)
        canvas.restore()
    }

    /** Separate display list: animation does not redraw maps, artwork, text or cards. */
    override fun drawNeedles(canvas: Canvas, width: Int, height: Int, speedKph: Float?, rpm: Float?) {
        val scale = min(width / 2048f, height / 768f)
        if (scale <= 0) return
        canvas.save()
        canvas.translate((width - 2048 * scale) / 2, (height - 768 * scale) / 2)
        canvas.scale(scale, scale)
        needles(canvas, speedKph, rpm)
        canvas.restore()
    }

    private fun needles(canvas: Canvas, speedKph: Float?, rpm: Float?) {
        val left = OemClusterGeometry.DIAL_LEFT_X / .9375f
        val right = OemClusterGeometry.DIAL_RIGHT_X / .9375f
        rpm?.let { needle(canvas, left, OemClusterGeometry.needleDegrees(it, 7000f)) }
        speedKph?.let { needle(canvas, right, OemClusterGeometry.speedDegrees(it)) }
        if (rpm == null) text(canvas, "—", left, 549f, 19f, 70f, muted, true)
        if (speedKph == null) text(canvas, "—", right, 578f, 17f, 70f, muted, true)
    }

    private fun dialScales(canvas: Canvas) {
        for (left in listOf(true, false)) {
            canvas.save()
            canvas.translate(if (left) -OemClusterGeometry.DIAL_OUTWARD_OFFSET else OemClusterGeometry.DIAL_OUTWARD_OFFSET, 0f)
            dialScale(canvas, left)
            canvas.restore()
        }
    }

    private fun dialScale(canvas: Canvas, left: Boolean) {
        for (cx in floatArrayOf(if (left) 434f else 1591f)) {
            reset(Color.argb(32, 0, 182, 255)); paint.style = Paint.Style.STROKE
            paint.strokeWidth = 12f; canvas.drawCircle(cx, 373f, 258f, paint)
            paint.color = Color.argb(100, 16, 199, 255); paint.strokeWidth = 5f
            canvas.drawCircle(cx, 373f, 258f, paint)
            paint.color = white; paint.strokeWidth = 1.8f
            canvas.drawCircle(cx, 373f, 258f, paint)
        }
        if (left) text(canvas, "x1000r/min", 434f, 578f, 23f, 180f, Color.rgb(161, 220, 246), true)
        else text(canvas, "km/h", 1591f, 602f, 20f, 110f, Color.rgb(161, 220, 246), true)
        fun tick(cx: Float, angle: Float, major: Boolean, half: Boolean, red: Boolean) {
            val a = Math.toRadians(angle.toDouble())
            val start = if (major) 235f else if (half) 240f else 246f
            reset(if (red) Color.rgb(255, 39, 83) else white)
            paint.strokeWidth = if (major) 3f else if (half) 2.3f else 1.3f
            canvas.drawLine(cx + cos(a).toFloat() * start, 373 + sin(a).toFloat() * start,
                cx + cos(a).toFloat() * 252, 373 + sin(a).toFloat() * 252, paint)
        }
        fun label(cx: Float, angle: Float, value: String, red: Boolean, size: Float) {
            val a = Math.toRadians(angle.toDouble())
            text(canvas, value, cx + cos(a).toFloat() * 213,
                373 + sin(a).toFloat() * 213 + size * .34f, size, 66f,
                if (red) Color.rgb(255, 39, 83) else white, true)
        }
        if (left) {
            for (i in 0..70) tick(434f, OemClusterGeometry.needleDegrees(i * 100f, 7000f),
                i % 10 == 0, i % 10 == 5, i >= 60)
            for (i in 0..7) label(434f, OemClusterGeometry.needleDegrees(i * 1000f, 7000f),
                i.toString(), i >= 6, 33f)
            return
        }
        val speedMarks = (0..200 step 20).toList() + listOf(230, 260)
        for (i in 0..260 step 5) tick(1591f, OemClusterGeometry.speedDegrees(i.toFloat()),
            i in speedMarks, i % 10 == 0, false)
        for (i in speedMarks) label(1591f, OemClusterGeometry.speedDegrees(i.toFloat()),
            i.toString(), false, 26f)
    }

    override fun release() { images.values.forEach { it.recycle() }; images.clear() }

    private fun image(id: Int): Bitmap = images.getOrPut(id) {
        val source = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
        if (id == R.drawable.cluster_basic_face) {
            // Bake fixed scale marks once; animation redraws only needles and live content.
            val face = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle()
            dialScales(Canvas(face))
            reset()
            face
        } else source
    }

    private fun reset(color: Int = white) {
        paint.shader = null; paint.alpha = 255; paint.color = color
        paint.style = Paint.Style.FILL; paint.strokeWidth = 1f; paint.typeface = typeface
        paint.textAlign = Paint.Align.LEFT; paint.strokeCap = Paint.Cap.ROUND
    }

    private fun assistance(canvas: Canvas, data: OemClusterAssistance?, warning: Int?) {
        canvas.save(); path.reset(); path.addCircle(432f, 374f, 147f, Path.Direction.CW); canvas.clipPath(path)
        // Reference perspective road, with discrete detections rather than simulated targets.
        for (i in -6..6) line(canvas, 432f + i * 8, 308f, 432f + i * 61, 525f, Color.argb(60, 37, 104, 165), .7f)
        for (i in 1..8) {
            val y = 308f + i * i * 3.5f
            line(canvas, 280f, y, 585f, y, Color.argb(65, 31, 107, 175), .7f)
        }
        for (left in listOf(true, false)) {
            val detected = if (left) data?.leftLaneDetected else data?.rightLaneDetected
            val direction = if (left) -1 else 1
            line(canvas, 432f + direction * 25, 329f, 432f + direction * 115, 514f,
                if (detected == true) cyan else Color.rgb(17, 106, 206), 2f)
            for (i in 0..5) {
                val y = 338f + i * 25
                val x = 432f + direction * (30f + i * 12)
                line(canvas, x, y, x + direction * 5, y + 10, Color.rgb(35, 133, 252), 2f)
            }
        }
        if (data?.leadVehicleDetected == true) {
            bitmap(canvas, R.drawable.cluster_basic_car_lead, RectF(407f, 304f, 459f, 353f))
            for (i in 0 until data.followingBars.coerceIn(0, 5)) {
                val y = 352f + i * 8
                line(canvas, 413f - i * 3, y, 451f + i * 3, y, Color.rgb(24, 235, 131), 4f)
            }
        }
        bitmap(canvas, R.drawable.cluster_basic_car_own, RectF(379f, 391f, 483f, 493f))
        if (warning == 1 || warning == 2) {
            val color = if (warning == 2) Color.rgb(255, 49, 79) else Color.rgb(255, 200, 58)
            path.reset(); path.moveTo(432f, 254f); path.lineTo(451f, 287f); path.lineTo(413f, 287f); path.close()
            reset(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f; canvas.drawPath(path, paint)
            text(canvas, "!", 432f, 282f, 23f, 25f, color, true)
        }
        canvas.restore()
    }

    private fun music(canvas: Canvas, state: ClusterDashboardState) {
        val song = state.music.metadata
        val cover = state.music.artwork?.takeUnless { it.isRecycled }
        val bounds = RectF(1534f, 225f, 1641f, 325f)
        reset(navy); canvas.drawRoundRect(bounds, 7f, 7f, paint)
        if (cover != null) {
            canvas.save(); path.reset(); path.addRoundRect(bounds, 7f, 7f, Path.Direction.CW); canvas.clipPath(path)
            val side = min(cover.width, cover.height)
            val src = android.graphics.Rect((cover.width-side)/2, (cover.height-side)/2,
                (cover.width+side)/2, (cover.height+side)/2)
            reset(); canvas.drawBitmap(cover, src, bounds, paint); canvas.restore()
        } else text(canvas, "♪", 1588f, 302f, 68f, 95f, muted, true)
        outline(canvas, bounds, 7f, Color.rgb(65, 111, 179), 1f)
        text(canvas, song.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.cluster_dashboard_waiting_music),
            1591f, 357f, 23f, 290f, white, true)
        text(canvas, song.artist.orEmpty(), 1591f, 386f, 20f, 290f, muted, true)
        // iAP2 supplies album/source metadata, not timed lyrics. Never render the sample lyrics.
        text(canvas, song.album.orEmpty(), 1591f, 417f, 21f, 290f, cyan, true)
        text(canvas, song.sourceApp.orEmpty(), 1591f, 444f, 19f, 280f, muted, true)
        line(canvas, 1485f, 466f, 1703f, 466f, Color.rgb(29, 64, 106), 6f)
        state.progress()?.let {
            val end = 1485f + 218 * it
            line(canvas, 1485f, 466f, end, 466f, cyan, 6f)
            reset(white); canvas.drawCircle(end, 466f, 5f, paint)
        }
        text(canvas, time(song.elapsedMillis), 1484f, 491f, 16f, 85f, muted)
        text(canvas, time(song.durationMillis), 1705f, 491f, 16f, 85f, muted, right = true)
        reset(cyan); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f
        canvas.drawCircle(1591f, 534f, 37f, paint)
        if (song.playing) {
            line(canvas, 1583f, 518f, 1583f, 549f, white, 7f)
            line(canvas, 1598f, 518f, 1598f, 549f, white, 7f)
        } else triangle(canvas, 1582f, 518f, 1606f, 534f, 1582f, 550f, white)
        for (left in listOf(true, false)) {
            val cx = if (left) 1509f else 1672f
            val direction = if (left) -1 else 1
            line(canvas, cx + direction * 8, 532f, cx + direction * 8, 551f, white, 2.5f)
            reset(white); path.reset(); path.moveTo(cx + direction * 5, 541f)
            path.lineTo(cx - direction * 8, 532f); path.lineTo(cx - direction * 8, 550f); path.close()
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 2.5f; canvas.drawPath(path, paint)
        }
    }

    private fun navigation(canvas: Canvas, state: ClusterDashboardState) {
        val nav = state.guidance
        // The route parser clears the maneuver at route end/disconnect. Metadata alone
        // must not leave an empty card or stale arrival strip over the browsing map.
        if (!nav.connected) return
        val maneuver = nav.maneuverType ?: return
        reset(Color.argb(249, 0, 21, 45)); canvas.drawRoundRect(RectF(767f, 112f, 1228f, 214f), 12f, 12f, paint)
        outline(canvas, RectF(767f, 112f, 1228f, 214f), 12f, Color.rgb(83, 128, 183), 1.4f)
        context.getDrawable(NavigationWidgetUpdater.arrow(maneuver, nav.drivingSide))?.mutate()?.let {
            it.setTint(white); it.setBounds(804, 126, 877, 199); it.draw(canvas)
        }
        val turnDistance = if (nav.distanceMeters < 1000) context.getString(R.string.cluster_dashboard_meters, nav.distanceMeters)
            else context.getString(R.string.cluster_dashboard_kilometers, nav.distanceMeters / 1000.0)
        text(canvas, turnDistance, 889f, 160f, 36f, 320f, Color.rgb(119, 228, 255))
        text(canvas, nav.road, 889f, 195f, 22f, 320f)
        // Every route field is optional. Do not draw empty placeholders over the map.
        val meters = nav.remainingMeters?.takeIf { it >= 0 }
        val arrival = nav.arrivalEpochSeconds?.takeIf { it > 0 && it <= Long.MAX_VALUE / 1000 }
            ?.let { date.format(java.util.Date(it * 1000)) }
        val seconds = nav.remainingSeconds?.takeIf { it >= 0 }
        if (meters == null && arrival == null && seconds == null) return
        reset(Color.argb(247, 0, 19, 41)); canvas.drawRoundRect(RectF(680f, 535f, 1363f, 584f), 20f, 20f, paint)
        outline(canvas, RectF(680f, 535f, 1363f, 584f), 20f, Color.rgb(45, 113, 161), 1f)
        // The next road is not the destination name; this slot is remaining route distance.
        if (meters != null) {
            reset(Color.rgb(107, 246, 133)); path.reset(); path.moveTo(712f, 549f); path.lineTo(732f, 550f)
            path.lineTo(732f, 564f); path.lineTo(712f, 562f); path.close(); canvas.drawPath(path, paint)
            line(canvas, 712f, 549f, 712f, 572f, Color.rgb(107, 246, 133), 2f)
            text(canvas, context.getString(R.string.cluster_dashboard_remaining), 745f, 569f, 20f, 92f, muted)
            text(canvas, context.getString(R.string.cluster_dashboard_kilometers, meters / 1000.0), 841f, 569f, 22f, 116f)
        }
        if (arrival != null) {
            reset(cyan); canvas.drawCircle(982f, 560f, 12f, paint)
            line(canvas, 982f, 553f, 982f, 560f, navy, 2f); line(canvas, 982f, 560f, 987f, 563f, navy, 2f)
            text(canvas, context.getString(R.string.cluster_dashboard_arrival), 1002f, 569f, 20f, 86f, cyan)
            text(canvas, arrival, 1090f, 569f, 23f, 86f)
        }
        if (seconds != null) {
            val duration = context.getString(R.string.cluster_dashboard_minutes, (seconds / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            text(canvas, duration, 1315f, 569f, 22f, 136f, white, right = true)
        }
    }

    private fun tires(canvas: Canvas, vehicle: OemClusterVehicleState) {
        bitmap(canvas, R.drawable.cluster_basic_car_top, RectF(975f, 598f, 1032f, 688f))
        val labels = intArrayOf(R.string.cluster_tire_front_left, R.string.cluster_tire_front_right,
            R.string.cluster_tire_rear_left, R.string.cluster_tire_rear_right)
        for (i in 0..3) {
            val left = i % 2 == 0
            val x = if (left) 812f else 1073f
            val y = if (i < 2) 627f else 675f
            text(canvas, context.getString(labels[i]), x, y, 21f, 57f, muted)
            val pressure = vehicle.tirePressureBar.getOrNull(i)
            val value = pressure?.let { String.format(java.util.Locale.getDefault(), "%.1f bar", it) } ?: "— bar"
            val warning = vehicle.tireWarning.getOrNull(i)
            val color = when { warning == true -> Color.rgb(255, 61, 78); warning == false -> Color.rgb(41, 231, 162)
                pressure != null -> cyan; else -> Color.rgb(66, 84, 106) }
            text(canvas, value, x + 63f, y, 22f, 105f, if (warning == true) color else white)
            // Cover the entire wheel, including the reference cutout's fixed green pixels.
            // Each wheel uses only its matching OEM warning bit, including unavailable pressure.
            val wheelX = if (left) 980f else 1027f
            val wheelY = if (i < 2) 618f else 662f
            reset(color)
            canvas.drawRoundRect(RectF(wheelX - 4f, wheelY - 10f, wheelX + 4f, wheelY + 10f), 3f, 3f, paint)
            if (warning == true) {
                line(canvas, wheelX, wheelY - 5f, wheelX, wheelY + 1f, navy, 1.8f)
                reset(navy); canvas.drawCircle(wheelX, wheelY + 5f, 1f, paint)
            }
        }
    }

    private fun bitmap(canvas: Canvas, resource: Int, bounds: RectF) {
        reset(); canvas.drawBitmap(image(resource), null, bounds, paint)
    }
    private fun needle(canvas: Canvas, cx: Float, degrees: Float) {
        canvas.save(); canvas.rotate(degrees, cx, 373f)
        reset(); paint.shader = LinearGradient(cx + 154f, 373f, cx + 268f, 373f,
            Color.rgb(137, 198, 239), Color.WHITE, Shader.TileMode.CLAMP)
        path.reset(); path.moveTo(cx + 154f, 370.5f); path.lineTo(cx + 268f, 373f)
        path.lineTo(cx + 154f, 375.5f); path.close(); canvas.drawPath(path, paint)
        canvas.restore()
    }
    private fun time(ms: Long?): String = ms?.takeIf { it >= 0 }?.let {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", it / 60000, (it / 1000) % 60)
    } ?: "—:—"
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, maxWidth: Float,
                     color: Int = white, center: Boolean = false, right: Boolean = false) {
        reset(color); paint.textSize = size
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString(), x, y, paint)
    }
    private fun line(canvas: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float) {
        reset(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; canvas.drawLine(x, y, x2, y2, paint)
    }
    private fun outline(canvas: Canvas, bounds: RectF, radius: Float, color: Int, width: Float) {
        reset(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; canvas.drawRoundRect(bounds, radius, radius, paint)
    }
    private fun triangle(canvas: Canvas, x: Float, y: Float, x2: Float, y2: Float, x3: Float, y3: Float, color: Int) {
        reset(color); path.reset(); path.moveTo(x, y); path.lineTo(x2, y2); path.lineTo(x3, y3); path.close(); canvas.drawPath(path, paint)
    }
}

