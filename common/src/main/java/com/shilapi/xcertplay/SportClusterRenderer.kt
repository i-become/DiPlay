package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.host.R
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Reference-plane dimensions match the supplied image, with no density-dependent offsets. */
internal object SportClusterGeometry {
    const val WIDTH = 2048f
    const val HEIGHT = 768f
    const val MAP_LEFT = 1310f
    const val MAP_TOP = 140f
    const val MAP_WIDTH = 738f
    const val MAP_HEIGHT = 492f
    const val MAP_ASPECT = MAP_WIDTH / MAP_HEIGHT
    const val DIAL_X = 1024f
    const val DIAL_Y = 358f
    // Lower the top rim below the OEM mode label, retaining bottom icon clearance.
    const val CONTENT_SCALE = .96f
    const val CONTENT_TOP = 24f
    fun scale(width: Int, height: Int) = min(width.coerceAtLeast(0) / WIDTH, height.coerceAtLeast(0) / HEIGHT) * CONTENT_SCALE
    fun offsetX(width: Int, height: Int) = (width - WIDTH * scale(width, height)) / 2
    fun offsetY(width: Int, height: Int): Float {
        val fit = min(width.coerceAtLeast(0) / WIDTH, height.coerceAtLeast(0) / HEIGHT)
        return (height - HEIGHT * fit) / 2 + CONTENT_TOP * fit
    }

    fun mapBounds(width: Int, height: Int, sourceAspect: Float = MAP_ASPECT): RectF {
        val scale = scale(width, height)
        val aspect = sourceAspect.takeIf { it.isFinite() && it > 0 } ?: MAP_ASPECT
        val w = min(MAP_WIDTH, MAP_HEIGHT * aspect)
        val h = w / aspect
        val x = offsetX(width, height) + (MAP_LEFT + (MAP_WIDTH - w) / 2) * scale
        val y = offsetY(width, height) + (MAP_TOP + (MAP_HEIGHT - h) / 2) * scale
        return RectF(x, y, x + w * scale, y + h * scale)
    }

    fun streamConfig(width: Int, height: Int, scalePercent: Int, markerXPercent: Int, markerYPercent: Int): com.shilapi.xcertplay.airplay.AirPlayDisplayConfig {
        val viewport = mapBounds(width, height)
        // 3:2 in blocks of 24x16: both codec dimensions remain multiples of eight.
        val blocks = (viewport.height() * ClusterMapRenderPolicy.scalePercent(scalePercent) / 100 / 16).roundToInt().coerceAtLeast(1)
        return CarPlayClusterDisplay.config(blocks * 24, blocks * 16, scalePercent = 100,
            content = CarPlayClusterDisplay.Content.MAP, markerXPercent = markerXPercent,
            markerYPercent = markerYPercent,
            baseSafeArea = AirPlayInsets(top = 8, bottom = 8, left = 48, right = 48))
    }
}

/** Red Sport composition. The reference contributes framing and road artwork only.
 * Vehicle/phone values are always live, with missing values left unknown.
 * No top-left speed/gear card is drawn: that rectangle belongs to the OEM overlay.
 */
internal class SportClusterRenderer(private val context: Context) : ClusterInstrumentRenderer {
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val images = mutableMapOf<Int, Bitmap>()
    private val white = Color.rgb(245, 247, 250)
    private val muted = Color.rgb(177, 186, 197)
    private val red = Color.rgb(255, 36, 48)
    private val typeface = Typeface.create("sans-serif", Typeface.NORMAL)

    override fun draw(canvas: Canvas, width: Int, height: Int, state: ClusterDashboardState, night: Boolean,
                      includeNeedles: Boolean) {
        val scale = SportClusterGeometry.scale(width, height)
        if (scale <= 0) return
        canvas.save()
        canvas.translate(SportClusterGeometry.offsetX(width, height), SportClusterGeometry.offsetY(width, height))
        canvas.scale(scale, scale)
        reset()
        canvas.drawBitmap(image(R.drawable.cluster_sport_face), 0f, 0f, paint)
        mapBlend(canvas)
        val car = state.vehicle.validated()
        assistance(canvas, car)
        music(canvas, state)
        navigation(canvas, state)
        tires(canvas, car)
        if (includeNeedles) needles(canvas, car.speedKph, car.rpm)
        canvas.restore()
    }

    override fun drawNeedles(canvas: Canvas, width: Int, height: Int, speedKph: Float?, rpm: Float?) {
        val scale = SportClusterGeometry.scale(width, height)
        if (scale <= 0) return
        canvas.save()
        canvas.translate(SportClusterGeometry.offsetX(width, height), SportClusterGeometry.offsetY(width, height))
        canvas.scale(scale, scale)
        needles(canvas, speedKph, rpm)
        canvas.restore()
    }

    private fun needles(canvas: Canvas, speed: Float?, rpm: Float?) {
        // The two needles occupy separate annuli, so neither obscures music metadata.
        speed?.takeIf { it.isFinite() && it in 0f..260f }?.let {
            needle(canvas, OemClusterGeometry.speedDegrees(it), 220f, 300f)
        }
        rpm?.takeIf { it.isFinite() && it in 0f..8000f }?.let {
            needle(canvas, OemClusterGeometry.needleDegrees(it, 7000f), 176f, 201f)
        }
        text(canvas, speed?.takeIf { it.isFinite() && it in 0f..260f }?.roundToInt()?.toString() ?: "—",
            1024f, 115f, 34f, 92f, white, center = true)
        text(canvas, "km/h", 1024f, 139f, 18f, 80f, white, center = true)
        if (rpm == null) text(canvas, "—", 1024f, 497f, 18f, 80f, muted, center = true)
    }

    private fun scales(canvas: Canvas) {
        fun tick(angle: Float, radius: Float, length: Float, major: Boolean, warning: Boolean = false) {
            val a = Math.toRadians(angle.toDouble())
            line(canvas, 1024 + cos(a).toFloat() * (radius - length), 358 + sin(a).toFloat() * (radius - length),
                1024 + cos(a).toFloat() * radius, 358 + sin(a).toFloat() * radius,
                if (warning) red else white, if (major) 2.7f else 1.2f)
        }
        fun label(angle: Float, radius: Float, value: String, size: Float, warning: Boolean = false) {
            val a = Math.toRadians(angle.toDouble())
            text(canvas, value, 1024 + cos(a).toFloat() * radius,
                358 + sin(a).toFloat() * radius + size * .34f, size, 68f,
                if (warning) red else white, center = true)
        }
        val marks = (0..200 step 20).toList() + listOf(230, 260)
        for (v in 0..260 step 5) tick(OemClusterGeometry.speedDegrees(v.toFloat()), 299f,
            if (v in marks) 17f else if (v % 10 == 0) 10f else 6f, v in marks)
        // The top-centre slot shows current speed, as in the reference.
        for (v in marks.filter { it != 120 }) label(OemClusterGeometry.speedDegrees(v.toFloat()), 260f, v.toString(), 24f)
        for (v in 0..70) tick(OemClusterGeometry.needleDegrees(v * 100f, 7000f), 201f,
            if (v % 10 == 0) 15f else if (v % 10 == 5) 11f else 6f, v % 10 == 0 || v % 10 == 5, v >= 60)
        for (v in 0..7) label(OemClusterGeometry.needleDegrees(v * 1000f, 7000f), 169f, v.toString(), 20f, v >= 6)
        text(canvas, "x1000r/min", 1024f, 529f, 18f, 170f, white, center = true)
        text(canvas, "km/h", 1024f, 590f, 18f, 110f, white, center = true)
    }

    private fun mapBlend(canvas: Canvas) {
        canvas.save()
        canvas.clipRect(1310f, 140f, 2048f, 632f)
        path.reset(); path.addRect(1310f, 140f, 2048f, 632f, Path.Direction.CW)
        path.op(Path().apply { addCircle(1024f, 358f, 328f, Path.Direction.CW) }, Path.Op.DIFFERENCE)
        canvas.clipPath(path)
        reset()
        paint.shader = RadialGradient(1024f, 358f, 352f,
            intArrayOf(Color.BLACK, Color.argb(230, 0, 0, 0), Color.TRANSPARENT),
            floatArrayOf(0f, 328f / 352f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(1024f, 358f, 352f, paint)
        reset(); canvas.restore()
    }

    private fun assistance(canvas: Canvas, car: OemClusterVehicleState) {
        val data = car.assistance
        if (data?.leadVehicleDetected == true) {
            reset(); canvas.drawBitmap(image(R.drawable.cluster_sport_car_lead), 320f, 350f, paint)
            for (i in 0 until data.followingBars.coerceIn(0, 5)) {
                val y = 407f + i * 4f
                line(canvas, 334f - i * 4f, y, 382f + i * 4f, y, Color.rgb(39, 236, 142), 2f)
            }
        }
        // Preserve the reference's road perspective; highlight only detected lane sides.
        if (data?.leftLaneDetected == true) line(canvas, 333f, 354f, 211f, 604f, red, 2f)
        if (data?.rightLaneDetected == true) line(canvas, 388f, 354f, 513f, 604f, red, 2f)
        val warning = car.assistanceStatus?.fcwWarning
        if (warning == 1 || warning == 2) {
            val color = if (warning == 2) red else Color.rgb(255, 192, 61)
            reset(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = 3f
            path.reset(); path.moveTo(360f, 283f); path.lineTo(380f, 316f); path.lineTo(340f, 316f); path.close()
            canvas.drawPath(path, paint)
            text(canvas, "!", 360f, 310f, 23f, 20f, color, center = true)
        }
    }

    private fun music(canvas: Canvas, state: ClusterDashboardState) {
        val song = state.music.metadata
        val cover = state.music.artwork?.takeUnless { it.isRecycled }
        val bounds = RectF(976f, 239f, 1072f, 335f)
        reset(Color.rgb(7, 13, 18)); canvas.drawRoundRect(bounds, 8f, 8f, paint)
        if (cover != null) {
            canvas.save(); path.reset(); path.addRoundRect(bounds, 8f, 8f, Path.Direction.CW); canvas.clipPath(path)
            val side = min(cover.width, cover.height)
            reset(); canvas.drawBitmap(cover, Rect((cover.width-side)/2, (cover.height-side)/2,
                (cover.width+side)/2, (cover.height+side)/2), bounds, paint); canvas.restore()
        } else text(canvas, "♪", 1024f, 310f, 63f, 85f, muted, center = true)
        text(canvas, song.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.cluster_dashboard_waiting_music),
            1024f, 366f, 23f, 245f, white, center = true)
        text(canvas, song.artist.orEmpty(), 1024f, 394f, 19f, 245f, muted, center = true)
        line(canvas, 920f, 414f, 1129f, 414f, Color.rgb(43, 43, 46), 4f)
        state.progress()?.let {
            val end = 920f + 209 * it
            line(canvas, 920f, 414f, end, 414f, Color.rgb(255, 76, 102), 4f)
            reset(white); canvas.drawCircle(end, 414f, 3.5f, paint)
        }
        text(canvas, time(song.elapsedMillis), 918f, 434f, 15f, 70f, muted)
        text(canvas, time(song.durationMillis), 1131f, 434f, 15f, 70f, muted, right = true)
        // Transparent controls: no opaque button rectangle over the circular media well.
        if (song.playing) {
            line(canvas, 1019f, 451f, 1019f, 470f, white, 4f)
            line(canvas, 1029f, 451f, 1029f, 470f, white, 4f)
        } else {
            reset(); path.reset(); path.moveTo(1017f, 450f); path.lineTo(1034f, 460f); path.lineTo(1017f, 471f); path.close(); canvas.drawPath(path, paint)
        }
        for (left in listOf(true, false)) {
            val cx = if (left) 960f else 1089f
            val dir = if (left) -1 else 1
            line(canvas, cx + dir * 7, 454f, cx + dir * 7, 467f, white, 2f)
            reset(); paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
            path.reset(); path.moveTo(cx + dir * 4, 460f); path.lineTo(cx - dir * 7, 454f)
            path.lineTo(cx - dir * 7, 467f); path.close(); canvas.drawPath(path, paint)
        }
    }

    private fun navigation(canvas: Canvas, state: ClusterDashboardState) {
        val nav = state.guidance
        if (!nav.connected) return
        val maneuver = nav.maneuverType ?: return
        val routeDetails = nav.remainingMeters != null || nav.remainingSeconds != null
        val bounds = RectF(1428f, 156f, 1813f, if (routeDetails) 315f else 267f)
        reset(); paint.shader = LinearGradient(1428f, 156f, 1813f, 315f,
            Color.rgb(3, 15, 22), Color.rgb(12, 23, 32), Shader.TileMode.CLAMP)
        canvas.drawRoundRect(bounds, 15f, 15f, paint)
        reset(Color.rgb(43, 58, 72)); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f
        canvas.drawRoundRect(bounds, 15f, 15f, paint)
        context.getDrawable(NavigationWidgetUpdater.arrow(maneuver, nav.drivingSide))?.mutate()?.let {
            it.setTint(white); it.setBounds(1458, 179, 1525, 246); it.draw(canvas)
        }
        val distance = if (nav.distanceMeters < 1000) context.getString(R.string.cluster_dashboard_meters, nav.distanceMeters)
            else context.getString(R.string.cluster_dashboard_kilometers, nav.distanceMeters / 1000.0)
        text(canvas, distance, 1537f, 209f, 35f, 252f)
        text(canvas, nav.road, 1537f, 245f, 23f, 252f)
        if (routeDetails) {
            line(canvas, 1429f, 267f, 1812f, 267f, Color.rgb(42, 54, 65), 1f)
            nav.remainingMeters?.takeIf { it >= 0 }?.let {
                val value = context.getString(R.string.cluster_dashboard_remaining) + " " +
                    context.getString(R.string.cluster_dashboard_kilometers, it / 1000.0)
                text(canvas, value, 1456f, 300f, 23f, 221f, muted)
            }
            nav.remainingSeconds?.takeIf { it >= 0 }?.let {
                val value = context.getString(R.string.cluster_dashboard_minutes, (it / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                text(canvas, value, 1789f, 300f, 23f, 119f, muted, right = true)
            }
        }
    }

    private fun tires(canvas: Canvas, vehicle: OemClusterVehicleState) {
        for (i in 0..3) {
            val left = i % 2 == 0
            val y = if (i < 2) 646f else 690f
            val pressure = vehicle.tirePressureBar.getOrNull(i)
            val warning = vehicle.tireWarning.getOrNull(i) == true
            val color = if (warning) red else white
            text(canvas, pressure?.let { String.format(java.util.Locale.getDefault(), "%.1f", it) } ?: "—",
                if (left) 893f else 1087f, y, 27f, 64f, color)
            text(canvas, "bar", if (left) 940f else 1134f, y, 16f, 36f, if (warning) red else muted)
            val x = if (left) 998f else 1050f
            val wheelY = if (i < 2) 641f else 685f
            reset(if (warning) red else Color.rgb(173, 185, 193))
            canvas.drawRoundRect(RectF(x - 3f, wheelY - 10f, x + 3f, wheelY + 10f), 2.5f, 2.5f, paint)
            if (warning) text(canvas, "!", if (left) 876f else 1180f, y - 1, 18f, 14f, red, center = true)
        }
    }

    private fun needle(canvas: Canvas, degrees: Float, start: Float, end: Float) {
        canvas.save(); canvas.rotate(degrees, 1024f, 358f)
        val glowRadius = if (end > 250f) 72f else 25f
        val glowX = 1024 + start + (end - start) * .7f
        reset(); paint.shader = RadialGradient(glowX, 358f, glowRadius,
            intArrayOf(Color.argb(165, 255, 12, 20), Color.argb(65, 255, 12, 20), Color.TRANSPARENT),
            floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(glowX, 358f, glowRadius, paint)
        reset(); paint.shader = LinearGradient(1024 + start, 358f, 1024 + end, 358f,
            Color.argb(25, 255, 12, 20), red, Shader.TileMode.CLAMP)
        path.reset(); path.moveTo(1024 + start, 353f); path.lineTo(1024 + end, 358f)
        path.lineTo(1024 + start, 363f); path.close(); canvas.drawPath(path, paint)
        line(canvas, 1024 + start, 358f, 1024 + end, 358f, white, 2.8f)
        canvas.restore()
    }

    override fun release() { images.values.forEach { it.recycle() }; images.clear() }
    private fun image(id: Int): Bitmap = images.getOrPut(id) {
        val source = BitmapFactory.decodeResource(context.resources, id, BitmapFactory.Options().apply { inScaled = false })
        if (id == R.drawable.cluster_sport_face) {
            val cached = source.copy(Bitmap.Config.ARGB_8888, true)
            source.recycle(); scales(Canvas(cached)); reset(); cached
        } else source
    }
    private fun reset(color: Int = white) {
        paint.shader = null; paint.alpha = 255; paint.color = color; paint.style = Paint.Style.FILL
        paint.strokeWidth = 1f; paint.typeface = typeface; paint.textAlign = Paint.Align.LEFT; paint.strokeCap = Paint.Cap.ROUND
    }
    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, maxWidth: Float,
                     color: Int = white, center: Boolean = false, right: Boolean = false) {
        reset(color); paint.textSize = size
        paint.textAlign = if (center) Paint.Align.CENTER else if (right) Paint.Align.RIGHT else Paint.Align.LEFT
        canvas.drawText(TextUtils.ellipsize(value, paint, maxWidth, TextUtils.TruncateAt.END).toString(), x, y, paint)
    }
    private fun line(canvas: Canvas, x: Float, y: Float, x2: Float, y2: Float, color: Int, width: Float) {
        reset(color); paint.style = Paint.Style.STROKE; paint.strokeWidth = width; canvas.drawLine(x, y, x2, y2, paint)
    }
    private fun time(ms: Long?): String = ms?.takeIf { it >= 0 }?.let {
        String.format(java.util.Locale.getDefault(), "%02d:%02d", it / 60000, it / 1000 % 60)
    } ?: "—:—"
}

