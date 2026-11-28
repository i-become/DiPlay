package com.shilapi.xcertplay

import android.content.Context
import android.graphics.*
import android.text.TextPaint
import android.text.TextUtils
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.host.R
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.text.NumberFormat

/** Used unchanged by the live overlay and the editor. The OEM speed/status UI is never painted here. */
internal class OriginalClusterRenderer(private val context: Context) {
    private var rotation = 0f
    private var flipHorizontal = false
    private var flipVertical = false
    private var backgroundOpacity = 1f
    private val lyricScroll = ClusterLyricScroll()
    val lyricAnimating: Boolean get() = lyricScroll.animating
    private val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
    private val lanes = ClusterLaneStrip()
    private val car = BitmapFactory.decodeResource(context.resources, R.drawable.cluster_original_tire_car)
    // The supplied car artwork includes transparent margins. Use its visible body bounds.
    private val carSource = Rect(50, 63, 494, 1060)
    private val arrows = mutableMapOf<Int, android.graphics.drawable.Drawable>()
    private val phoneIcon = ClusterNavigationIcon()

    fun releaseNavigationIcon() = phoneIcon.release()

    fun draw(canvas: Canvas, width: Int, height: Int, layout: OriginalClusterLayout,
        vehicle: OemClusterVehicleState, nav: CarPlayGlance.Snapshot, editing: OriginalClusterComponent? = null,
        musicState: ClusterDashboardMusic = CarPlayMediaKeys.dashboardMusic(), only: OriginalClusterComponent? = null) {
        canvas.save()
        canvas.scale(width / OriginalClusterLayout.WIDTH, height / OriginalClusterLayout.HEIGHT)
        OriginalClusterComponent.entries.forEach { component ->
            if (only != null && component != only) return@forEach
            if (!layout[component].visible) return@forEach
            val bounds = layout.bounds(component)
            val placement = layout[component]
            rotation = placement.rotation
            flipHorizontal = placement.flipHorizontal; flipVertical = placement.flipVertical
            backgroundOpacity = placement.backgroundOpacity
            if (placement.opacity < 1f) canvas.saveLayerAlpha(null, (placement.opacity * 255).toInt()) else canvas.save()
            canvas.concat(originalTransform(bounds, placement))
            when (component) {
                OriginalClusterComponent.MAP -> Unit
                OriginalClusterComponent.TIRES -> tires(canvas, bounds, vehicle)
                OriginalClusterComponent.DRIVING -> driving(canvas, bounds, vehicle)
                OriginalClusterComponent.TURN -> if (nav.connected && nav.navigating &&
                    (nav.maneuverType != null || nav.directionIcon != null || lanes.fits(nav.lanes, bounds.width() - 30))) turn(canvas, bounds, nav)
                OriginalClusterComponent.TIME -> if (nav.connected && nav.navigating &&
                    (nav.arrivalEpochSeconds != null || nav.remainingSeconds != null || nav.routeSummary.isNotBlank())) time(canvas, bounds, nav)
                OriginalClusterComponent.MUSIC -> music(canvas, bounds, musicState, layout.musicStyle)
            }
            if (editing == component) {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
                paint.color = Color.argb(180, 103, 200, 255)
                canvas.drawRoundRect(bounds, 16f, 16f, paint)
                paint.style = Paint.Style.FILL
            }
            canvas.restore()
        }
        rotation = 0f
        canvas.restore()
    }

    private fun panel(canvas: Canvas, r: RectF, color: Int, radius: Float, card: Boolean = false) {
        paint.style = Paint.Style.FILL
        paint.color = color
        paint.alpha = (if (card) 255f * backgroundOpacity else Color.alpha(color).toFloat()).toInt()
        canvas.drawRoundRect(r, radius, radius, paint)
    }

    private fun tires(canvas: Canvas, r: RectF, v: OemClusterVehicleState) {
        panel(canvas, r, INFO_SURFACE, 15f, card = true)
        text(canvas, context.getString(R.string.settings_original_cluster_tires), r.left + 25, r.top + 36, 22f, r.width() - 50, bold = true)
        val carY = r.top + 108
        val carX = r.centerX() + 4
        val carArea = RectF(carX - 45, carY - 105, carX + 45, carY + 105)
        canvas.save(); canvas.rotate(-90f, carArea.centerX(), carArea.centerY())
        paint.color = Color.WHITE; paint.isFilterBitmap = true
        canvas.drawBitmap(car, carSource, carArea, paint); canvas.restore()
        // Rotating the car left puts its right wheels above and its front wheels on the left.
        val order = listOf(1, 3, 0, 2)
        val labels = listOf(R.string.settings_original_cluster_fr, R.string.settings_original_cluster_rr,
            R.string.settings_original_cluster_fl, R.string.settings_original_cluster_rl)
        order.forEachIndexed { slot, wheel ->
            val x = if (slot % 2 == 0) r.left + 92 else r.right - 92
            val y = r.top + if (slot < 2) 69 else 143
            val color = if (v.tireWarning.getOrNull(wheel) == true) Color.rgb(255, 77, 84) else Color.WHITE
            text(canvas, context.getString(labels[slot]), x, y, 18f, 100f, color, align = Paint.Align.CENTER)
            val value = v.tirePressureBar.getOrNull(wheel)?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "—"
            text(canvas, value, x, y + 28, 24f, 90f, color, align = Paint.Align.CENTER)
            if (slot < 2) { paint.color = color; paint.strokeWidth = 1.5f
                canvas.drawLine(x - 34, y + 41, x + 34, y + 41, paint) }
        }
    }

    private fun driving(canvas: Canvas, r: RectF, v: OemClusterVehicleState) {
        panel(canvas, r, INFO_SURFACE, 15f, card = true)
        text(canvas, context.getString(R.string.settings_original_cluster_driving), r.left + 25, r.top + 37, 22f, r.width() - 50, bold = true)
        val labels = listOf(R.string.settings_original_cluster_consumption, R.string.settings_original_cluster_odometer, R.string.settings_original_cluster_range)
        val values = listOf(v.averageFuelConsumption?.let { String.format(Locale.getDefault(), "%.1f", it) },
            v.totalMileageKm?.let { String.format(Locale.getDefault(), "%.1f", it) }, v.remainingMileageKm?.toString())
        labels.forEachIndexed { i, label ->
            val x = r.left + 77 + i * 170
            val width = 150f
            text(canvas, context.getString(label), x, r.top + 78, 22f, width, align = Paint.Align.CENTER)
            text(canvas, values[i] ?: "—", x, r.top + 124, 29f, width, align = Paint.Align.CENTER)
            text(canvas, if (i == 0) "L/100km" else "km", x, r.top + 154, 18f, width, Color.LTGRAY, align = Paint.Align.CENTER)
        }
    }

    private fun turn(canvas: Canvas, r: RectF, nav: CarPlayGlance.Snapshot) {
        val showLanes = lanes.fits(nav.lanes, r.width() - 30)
        val maneuver = nav.maneuverType
        // Keep the reference card silhouette; lanes share the lower part of the same card.
        val contentHeight = if (maneuver != null) r.height() else 74f
        val card = RectF(r.left, r.top, r.right, r.top + contentHeight)
        panel(canvas, card, TURN_SURFACE, if (maneuver != null) 44f else 30f, card = true)
        if (maneuver != null || nav.directionIcon != null) {
            val iconTop = if (showLanes) 20 else 36
            val iconBounds = RectF(r.left + 42, r.top + iconTop, r.left + 120, r.top + iconTop + 82)
            val iconDrawn = phoneIcon.draw(canvas, nav.directionIcon, iconBounds)
            if (!iconDrawn && maneuver != null) {
                val icon = NavigationWidgetUpdater.arrow(maneuver, nav.drivingSide)
                val straight = icon == R.drawable.ic_maneuver_straight
                val resource = if (straight) R.drawable.ic_original_cluster_straight else icon
                arrows.getOrPut(resource) { requireNotNull(context.getDrawable(resource)).mutate().apply { setTint(Color.WHITE) } }.let {
                    val left = if (straight) 58 else 42
                    val right = if (straight) 102 else 120
                    val top = if (showLanes) 20 else 36
                    it.setBounds((r.left + left).toInt(), (r.top + top).toInt(), (r.left + right).toInt(), (r.top + top + 82).toInt()); it.draw(canvas)
                }
            }
            val distance = nav.distanceMeters?.let { if (it >= 1000) context.getString(R.string.original_cluster_kilometers,
                NumberFormat.getNumberInstance(Locale.getDefault()).apply { maximumFractionDigits = 1; isGroupingUsed = false }.format(it / 1000.0))
                else context.getString(R.string.cluster_dashboard_meters, it) } ?: "—"
            text(canvas, distance, r.left + 144, r.top + 90, 40f, r.width() - 174, bold = true)
            text(canvas, nav.road, r.left + 34, r.top + if (showLanes) 133f else 157f, 28f, r.width() - 68, bold = true)
        }
        if (showLanes) lanes.draw(canvas, RectF(r.left + 20, card.bottom - 58, r.right - 20, card.bottom - 4), nav.lanes, Color.rgb(80, 222, 247))
    }

    private fun time(canvas: Canvas, r: RectF, nav: CarPlayGlance.Snapshot) {
        panel(canvas, r, TIME_SURFACE, r.height() / 2, card = true)
        val arrival = nav.arrivalEpochSeconds?.let { DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(it * 1000)) }
        val duration = nav.remainingSeconds?.let { seconds ->
            val minutes = ((seconds + 59) / 60).toInt()
            if (minutes < 60) context.getString(R.string.widget_minutes, minutes)
            else context.getString(R.string.original_cluster_hours_minutes, minutes / 60, minutes % 60)
        }
        val value = nav.routeSummary.takeIf { it.isNotBlank() }
            ?: listOfNotNull(arrival?.let { context.getString(R.string.settings_original_cluster_arrival, it) }, duration).joinToString("   ")
        paint.textSize = 28f; paint.typeface = Typeface.DEFAULT_BOLD
        val actual = TextUtils.ellipsize(value, paint, r.width() - 32, TextUtils.TruncateAt.END).toString()
        text(canvas, actual, r.centerX(),
            r.centerY() - (paint.ascent() + paint.descent()) / 2, 28f, r.width() - 32, bold = true, align = Paint.Align.CENTER)
    }

    private fun music(canvas: Canvas, r: RectF, state: ClusterDashboardMusic, style: OriginalMusicStyle) {
        val song = state.metadata
        val content = ClusterMusicText.from(song)
        val padding = OriginalMusicStyle.PADDING
        val artistSize = style.artistSize
        val lyricSize = style.lyricSize
        val coverSize = style.coverSize
        val songTitle = content.title ?: context.getString(R.string.cluster_dashboard_waiting_music)
        val lyric = content.lyric ?: song.album.orEmpty()
        panel(canvas, r, INFO_SURFACE, 18f, card = true)
        val coverTop = r.centerY() - coverSize / 2f
        val cover = RectF(r.left + padding, coverTop, r.left + padding + coverSize, coverTop + coverSize)
        val bitmap = state.artwork?.takeUnless { it.isRecycled }
        if (bitmap != null) {
            paint.reset()
            paint.isAntiAlias = true
            paint.isFilterBitmap = true
            val side = minOf(bitmap.width, bitmap.height)
            val src = Rect((bitmap.width - side) / 2, (bitmap.height - side) / 2,
                (bitmap.width + side) / 2, (bitmap.height + side) / 2)
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(cover, 10f, 10f, Path.Direction.CW) })
            canvas.drawBitmap(bitmap, src, cover, paint)
            canvas.restore()
            paint.isFilterBitmap = false
        } else {
            panel(canvas, cover, INFO_SURFACE, 12f)
            text(canvas, "♪", cover.centerX(), cover.centerY() + 12f, 42f, cover.width(), Color.LTGRAY, align = Paint.Align.CENTER)
        }
        val textLeft = cover.right + OriginalMusicStyle.COLUMN_GAP
        val textWidth = style.textWidth
        val titleSize = style.titleSize
        val titleRow = style.titleRow
        val rowsHeight = titleRow + style.artistRow + style.lyricRow + style.footerHeight
        val titleTop = r.centerY() - rowsHeight / 2f
        val artistTop = titleTop + titleRow
        val lyricTop = artistTop + style.artistRow
        val footerTop = lyricTop + style.lyricRow
        text(canvas, songTitle,
            textLeft, musicBaseline(titleTop + titleRow / 2f, titleSize), titleSize, textWidth, style.titleColor)
        text(canvas, content.artist.orEmpty(), textLeft, musicBaseline(artistTop + style.artistRow / 2f, artistSize),
            artistSize, textWidth, style.artistColor)
        paint.style = Paint.Style.FILL; paint.color = style.lyricColor; paint.textSize = lyricSize
        paint.typeface = Typeface.DEFAULT_BOLD
        lyricScroll.draw(canvas, lyric,
            listOf(content.title, content.artist, song.album), paint, textLeft,
            musicBaseline(lyricTop + style.lyricRow / 2f, lyricSize, bold = true), textWidth,
            remainingMillis = content.lyricRemainingMillis, counterRotation = rotation, flipHorizontal = flipHorizontal, flipVertical = flipVertical)
        val duration = song.durationMillis ?: 0L
        val elapsed = song.elapsedMillis ?: 0L
        val progress = if (duration > 0) (elapsed.toFloat() / duration).coerceIn(0f, 1f) else 0f
        val bar = RectF(textLeft + 4f, footerTop + 3f, textLeft + 4f + style.progressWidth, footerTop + 8f)
        panel(canvas, bar, Color.rgb(48, 58, 72), 4f)
        if (progress > 0f) panel(canvas, RectF(bar.left, bar.top, bar.left + bar.width() * progress, bar.bottom),
            style.progressColor, 4f)
        paint.color = style.progressColor
        canvas.drawCircle(bar.left + bar.width() * progress, bar.centerY(), 4f, paint)
        // Keep transport and time labels on the same lower row, below the lyric.
        val cy = footerTop + 22f
        val timeBaseline = musicBaseline(cy, 13f)
        if (style.showTime) {
        text(canvas, musicTime(song.elapsedMillis), textLeft, timeBaseline, 13f, 62f, Color.LTGRAY)
        text(canvas, musicTime(song.durationMillis), textLeft + 68f, timeBaseline, 13f, 62f, Color.LTGRAY)
        }
        if (style.showPlaybackControls) {
        val cx = r.right - padding - 43f
        paint.color = Color.WHITE; paint.strokeWidth = 2f
        for (direction in listOf(-1, 1)) {
            val x = cx + direction * 35f
            canvas.drawLine(x + direction * 6f, cy - 7f, x + direction * 6f, cy + 7f, paint)
            canvas.drawPath(Path().apply {
                moveTo(x + direction * 4f, cy)
                lineTo(x - direction * 5f, cy - 7f)
                lineTo(x - direction * 5f, cy + 7f)
                close()
            }, paint)
        }
        if (song.playing) {
            paint.strokeWidth = 3.5f
            canvas.drawLine(cx - 4f, cy - 8f, cx - 4f, cy + 8f, paint)
            canvas.drawLine(cx + 4f, cy - 8f, cx + 4f, cy + 8f, paint)
        } else canvas.drawPath(Path().apply {
            moveTo(cx - 5f, cy - 9f); lineTo(cx + 8f, cy); lineTo(cx - 5f, cy + 9f); close()
        }, paint)
        }
    }

    private fun musicBaseline(center: Float, size: Float, bold: Boolean = false): Float {
        paint.textSize = size
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        return center - (paint.ascent() + paint.descent()) / 2f
    }

    private fun musicTime(milliseconds: Long?): String = milliseconds?.takeIf { it >= 0 }?.let {
        String.format(Locale.getDefault(), "%02d:%02d", it / 60000, it / 1000 % 60)
    } ?: "--:--"

    private fun text(canvas: Canvas, value: String, x: Float, y: Float, size: Float, width: Float,
        color: Int = Color.WHITE, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) {
        paint.style = Paint.Style.FILL; paint.color = color; paint.textSize = size
        paint.textAlign = align
        paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        val line = TextUtils.ellipsize(value, paint, width, TextUtils.TruncateAt.END).toString()
        drawUprightGlyphs(canvas, line, x, y, paint, rotation, align, fitWidth = width,
            flipHorizontal = flipHorizontal, flipVertical = flipVertical)
    }

    companion object {
        // Calibrated against the provided 2048 × 768 reference. These are independent
        // surfaces: map details stay visible through the two information panels.
        private val INFO_SURFACE = Color.argb(92, 11, 11, 14)
        private val TURN_SURFACE = Color.argb(252, 1, 5, 16)
        private val TIME_SURFACE = Color.argb(232, 24, 28, 36)
    }
}
