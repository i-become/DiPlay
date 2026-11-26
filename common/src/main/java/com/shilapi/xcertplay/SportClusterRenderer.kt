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
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Reference-plane dimensions match the supplied image, with no density-dependent offsets. */
internal object SportClusterGeometry {
    const val WIDTH = 2048f
    const val HEIGHT = 768f
    // Overscan behind the dial so the map can meet the lower red arc with a soft join.
    // The visible opening is still clipped by cluster_sport_face.png; the extra 48 px are
    // intentionally hidden below the opaque tachometer and only become visible at the arc.
    const val MAP_LEFT = 1232f
    const val MAP_TOP = 48f
    const val MAP_WIDTH = 816f
    const val MAP_HEIGHT = 612f
    const val MAP_ASPECT = MAP_WIDTH / MAP_HEIGHT
    const val DIAL_X = 1024f
    const val DIAL_Y = 358f
    // Lower the top rim below the OEM mode label, retaining bottom icon clearance.
    // Keep the upper rim anchored while using the clear space beneath the lower vehicle icons.
    const val CONTENT_SCALE = .99f
    const val CONTENT_TOP = 24f
    fun scale(width: Int, height: Int) = min(width.coerceAtLeast(0) / WIDTH, height.coerceAtLeast(0) / HEIGHT) * CONTENT_SCALE
    fun horizontalScale(width: Int, height: Int) = scale(width, height) / CONTENT_SCALE
    fun offsetX(width: Int, height: Int) = (width - WIDTH * horizontalScale(width, height)) / 2
    fun offsetY(width: Int, height: Int): Float {
        val fit = min(width.coerceAtLeast(0) / WIDTH, height.coerceAtLeast(0) / HEIGHT)
        return (height - HEIGHT * fit) / 2 + CONTENT_TOP * fit
    }

    fun mapBounds(width: Int, height: Int, sourceAspect: Float = MAP_ASPECT): RectF {
        val scale = scale(width, height)
        val aspect = sourceAspect.takeIf { it.isFinite() && it > 0 } ?: MAP_ASPECT
        val w = min(MAP_WIDTH, MAP_HEIGHT * aspect)
        val h = w / aspect
        val xScale = horizontalScale(width, height)
        val x = offsetX(width, height) + (MAP_LEFT + (MAP_WIDTH - w) / 2) * xScale
        val y = offsetY(width, height) + (MAP_TOP + (MAP_HEIGHT - h) / 2) * scale
        return RectF(x, y, x + w * xScale, y + h * scale)
    }

    fun streamConfig(width: Int, height: Int, scalePercent: Int, markerXPercent: Int, markerYPercent: Int): com.shilapi.xcertplay.airplay.AirPlayDisplayConfig {
        val viewport = mapBounds(width, height)
        // 4:3 in blocks of 32x24: both codec dimensions remain multiples of eight.
        val blocks = (viewport.height() * ClusterMapRenderPolicy.scalePercent(scalePercent) / 100 / 24).roundToInt().coerceAtLeast(1)
        return CarPlayClusterDisplay.config(blocks * 32, blocks * 24, scalePercent = 100,
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
    private val lyricScroll = ClusterLyricScroll()
    override val lyricAnimating: Boolean get() = lyricScroll.animating
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private val images = mutableMapOf<Int, Bitmap>()
    private val white = Color.rgb(245, 247, 250)
    private val muted = Color.rgb(177, 186, 197)
    private val red = Color.rgb(255, 36, 48)
    private val typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    private val guidanceCard = ClusterGuidanceCard(context, sport = true)

    override fun draw(canvas: Canvas, width: Int, height: Int, state: ClusterDashboardState, night: Boolean,
                      includeNeedles: Boolean) {
        val scale = SportClusterGeometry.scale(width, height)
        if (scale <= 0) return
        canvas.save()
        canvas.translate(SportClusterGeometry.offsetX(width, height), SportClusterGeometry.offsetY(width, height))
        canvas.scale(SportClusterGeometry.horizontalScale(width, height), scale)
        reset()
        canvas.drawBitmap(image(R.drawable.cluster_sport_face), 0f, 0f, paint)
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
        canvas.scale(SportClusterGeometry.horizontalScale(width, height), scale)
        needles(canvas, speedKph, rpm)
        canvas.restore()
    }

    private fun needles(canvas: Canvas, speed: Float?, rpm: Float?) {
        // The two needles occupy separate annuli, so neither obscures music metadata.
        speed?.takeIf { it.isFinite() && it in 0f..260f }?.let {
            needle(canvas, OemClusterGeometry.speedDegrees(it), 220f, 300f)
        }
        rpm?.takeIf { it.isFinite() && it in 0f..8000f }?.let {
            // Keep the outer tip fixed and extend the original 25-pixel needle inward by 1/3.
            needle(canvas, OemClusterGeometry.needleDegrees(it, 7000f), 201f - 25f * 4f / 3f, 201f)
        }
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
        for (v in marks) label(OemClusterGeometry.speedDegrees(v.toFloat()), 260f, v.toString(), 24f)
        for (v in 0..70) tick(OemClusterGeometry.needleDegrees(v * 100f, 7000f), 201f,
            if (v % 10 == 0) 15f else if (v % 10 == 5) 11f else 6f, v % 10 == 0 || v % 10 == 5, v >= 60)
        for (v in 0..7) label(OemClusterGeometry.needleDegrees(v * 1000f, 7000f), 169f, v.toString(), 20f, v >= 6)
        text(canvas, "x1000r/min", 1024f, 529f, 18f, 170f, white, center = true)
        text(canvas, "km/h", 1024f, 590f, 18f, 110f, white, center = true)
    }

    private fun assistance(canvas: Canvas, car: OemClusterVehicleState) {
        val data = car.assistance
        if (data?.leadVehicleDetected == true) {
            reset(); canvas.drawBitmap(image(R.drawable.cluster_sport_car_lead), 320f, 350f, paint)
            for (i in 0 until data.followingBars.coerceIn(0, 5)) {
                val y = 407f + i * 4f
                line(canvas, 334f - i * 4f, y, 382f + i * 4f, y, Color.rgb(39, 236, 142), 2f)
            }
        } else if (data != null && data.leadVehicleDetected == null) {
            text(canvas, "?", 360f, 369f, 18f, 20f, Color.rgb(166, 191, 222), center = true)
        }
        // White means detected, gray means clear, and dark gray means the frame is unknown.
        fun laneColor(value: Boolean?) = when (value) {
            true -> Color.WHITE
            false -> Color.rgb(125, 137, 150)
            null -> Color.rgb(47, 67, 84)
        }
        val leftColor = laneColor(data?.leftLaneDetected)
        val rightColor = laneColor(data?.rightLaneDetected)
        line(canvas, 333f, 354f, 211f, 604f, leftColor, 2f)
        line(canvas, 388f, 354f, 513f, 604f, rightColor, 2f)
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
        val content = ClusterMusicText.from(song)
        val cover = state.music.artwork?.takeUnless { it.isRecycled }
        val bounds = RectF(976f, 239f, 1072f, 335f)
        reset(Color.rgb(7, 13, 18)); canvas.drawRoundRect(bounds, 8f, 8f, paint)
        if (cover != null) {
            canvas.save(); path.reset(); path.addRoundRect(bounds, 8f, 8f, Path.Direction.CW); canvas.clipPath(path)
            val side = min(cover.width, cover.height)
            reset(); canvas.drawBitmap(cover, Rect((cover.width-side)/2, (cover.height-side)/2,
                (cover.width+side)/2, (cover.height+side)/2), bounds, paint); canvas.restore()
        } else text(canvas, "♪", 1024f, 310f, 63f, 85f, muted, center = true)
        text(canvas, listOfNotNull(content.title, content.artist).joinToString(" · ")
            .ifBlank { context.getString(R.string.cluster_dashboard_waiting_music) },
            1024f, 359f, 16f, 290f, muted, center = true)
        reset(white); paint.textSize = 34f
        lyricScroll.draw(canvas, content.lyric ?: song.album.orEmpty(),
            listOf(content.title, content.artist, song.album), paint,
            869.0f, 401f, 310f, centered = true, remainingMillis = content.lyricRemainingMillis)
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
        if (!nav.connected || !nav.navigating) return
        guidanceCard.draw(canvas, nav)
        val value = navigationSummary(nav)
        if (value.isEmpty()) return
        val lines = navigationSummaryLines(nav)
        val textSize = if (lines.size > 1) 20f else 23f
        reset(muted); paint.textSize = textSize
        val width = lines.maxOf { paint.measureText(it) } + 32f
        val lineHeight = paint.descent() - paint.ascent()
        val height = lineHeight * lines.size + 12f
        val bounds = RectF(1698f - width / 2, 624f - height, 1698f + width / 2, 624f)
        reset(Color.argb(240, 3, 15, 22)); canvas.drawRoundRect(bounds, 12f, 12f, paint)
        lines.forEachIndexed { index, line ->
            text(canvas, line, bounds.left + 16f,
                bounds.top + 6f - paint.ascent() + lineHeight * index, textSize, width - 32f, muted)
        }
    }

    internal fun navigationSummary(nav: com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot): String {
        if (nav.routeSummary.isNotBlank()) return nav.routeSummary
        // Keep route totals together, with remaining distance as the final value.
        val arrival = nav.arrivalEpochSeconds?.takeIf { it > 0 }?.let {
            context.getString(R.string.settings_original_cluster_arrival,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)))
        }
        val duration = nav.remainingSeconds?.takeIf { it >= 0 }?.let { seconds ->
            val minutes = ((seconds + 59) / 60).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (minutes < 60) context.getString(R.string.widget_minutes, minutes)
            else context.getString(R.string.original_cluster_hours_minutes, minutes / 60, minutes % 60)
        }
        val distance = nav.remainingMeters?.takeIf { it >= 0 }?.let {
            val kilometers = NumberFormat.getNumberInstance(Locale.getDefault()).apply {
                maximumFractionDigits = 1
                isGroupingUsed = false
            }.format(it / 1000.0)
            context.getString(R.string.original_cluster_kilometers, kilometers)
        }
        return listOfNotNull(arrival, duration, distance).joinToString("   ")
    }

    /** Gives the destination its own line so a long name cannot hide distance, duration, or ETA. */
    internal fun navigationSummaryLines(nav: com.shilapi.xcertplay.glance.CarPlayGlance.Snapshot): List<String> {
        val summary = navigationSummary(nav)
        val parts = summary.split(" · ").filter(String::isNotBlank)
        val destination = parts.firstOrNull { it.startsWith("目的地 ") }
        if (destination == null) {
            reset(); paint.textSize = 23f
            return listOf(TextUtils.ellipsize(summary, paint, 488f, TextUtils.TruncateAt.END).toString())
        }
        val destinationIndex = parts.indexOf(destination)
        val details = parts.filterIndexed { index, _ -> index != destinationIndex }
        val orderedDetails = details.sortedBy { part ->
            when {
                part.startsWith("距离 ") -> 0
                part.startsWith("用时 ") -> 1
                part.startsWith("到达 ") -> 2
                else -> 3
            }
        }.joinToString("   ")
        val maxWidth = 620f
        reset(); paint.textSize = 20f
        val fittedDestination = TextUtils.ellipsize(destination, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        if (orderedDetails.isBlank()) return listOf(fittedDestination)
        return listOf(fittedDestination,
            TextUtils.ellipsize(orderedDetails, paint, maxWidth, TextUtils.TruncateAt.END).toString())
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

    override fun release() { guidanceCard.release(); images.values.forEach { it.recycle() }; images.clear() }
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

