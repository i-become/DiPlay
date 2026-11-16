package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.io.File
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33], qualifiers = "zh-rCN-w2048dp-h768dp-land-mdpi")
class ClusterMusicPresentationTest {
    @Test fun playerLyricTitleIsPromotedWithoutChangingRegularOrExplicitMetadata() {
        val embedded = CarPlayNowPlaying(title = "期待的未来", artist = "男孩 - Live · 梁博")
        assertEquals(ClusterMusicText("男孩 - Live", "梁博", "期待的未来"), ClusterMusicText.from(embedded))
        val regular = CarPlayNowPlaying(title = "男孩 - Live", artist = "梁博")
        assertEquals(ClusterMusicText("男孩 - Live", "梁博", null), ClusterMusicText.from(regular))
        val explicit = regular.copy(lyrics = "[00:00.00]期待的未来", elapsedMillis = 140000)
        assertEquals(ClusterMusicText("男孩 - Live", "梁博", "期待的未来"), ClusterMusicText.from(explicit))
        // An explicit lyric source always wins, even if artist has a middle dot.
        assertEquals("歌名", ClusterMusicText.from(explicit.copy(title = "歌名", artist = "歌手 · 乐团")).title)
    }

    @Test fun originalArtworkIsOpaqueAboveTheTranslucentCard() {
        val context = RuntimeEnvironment.getApplication()
        val cover = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.YELLOW) }
        val frame = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        val renderer = OriginalClusterRenderer(context)
        try {
            val layout = OriginalClusterLayout()
            renderer.draw(Canvas(frame), 2048, 768, layout, OemClusterVehicleState(), CarPlayGlance.Snapshot(),
                musicState = ClusterDashboardMusic(artwork = cover))
            val card = layout.bounds(OriginalClusterComponent.MUSIC)
            assertEquals(Color.YELLOW, frame.getPixel((card.left + 60).toInt(), card.centerY().toInt()))
            assertTrue(Color.alpha(frame.getPixel((card.right - 20).toInt(), (card.top + 10).toInt())) in 1..254)
        } finally { renderer.releaseNavigationIcon(); frame.recycle(); cover.recycle() }
    }

    @Test fun longLyricsRevealTheirEndWithoutPaintingOutsideTheWindow() {
        var now = 0L
        val scroll = ClusterLyricScroll { now }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 32f }
        val value = "让所有漫长的等待都变成值得期待的未来最后一个字"
        val folder = File("build/reports/cluster-music").apply { mkdirs() }
        fun draw(): Bitmap = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888).also {
            scroll.draw(Canvas(it), value, "track", paint, 40f, 60f, 320f)
        }
        val start = draw()
        now = 60000
        val end = draw()
        val stopped = draw()
        try {
            assertFalse(start.sameAs(end))
            assertTrue(end.sameAs(stopped))
            assertFalse(scroll.animating)
            for (frame in listOf(start, end)) {
                for (y in 0 until 100) for (x in 0 until 400) {
                    if (x < 40 || x >= 360) assertEquals(0, Color.alpha(frame.getPixel(x, y)))
                }
            }
            // End placement is exactly the right edge, with no repeated copy or ellipsis.
            val expected = Bitmap.createBitmap(400, 100, Bitmap.Config.ARGB_8888)
            try {
                val c = Canvas(expected)
                c.clipRect(40f, 0f, 360f, 100f)
                c.drawText(value, 360f - paint.measureText(value), 60f, paint)
                assertTrue(expected.sameAs(end))
            } finally { expected.recycle() }
            File(folder, "lyric-scroll-start.png").outputStream().use { start.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(folder, "lyric-scroll-end.png").outputStream().use { end.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { start.recycle(); end.recycle(); stopped.recycle() }
    }

    @Test fun actualRenderersShowBothLyricFormatsAtFullAndCompactSizes() {
        val context = RuntimeEnvironment.getApplication()
        context.resources.configuration.fontScale = 1.4f
        val cover = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
            val c = Canvas(this)
            c.drawColor(Color.rgb(240, 230, 201))
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(104, 63, 28); textSize = 34f; textAlign = Paint.Align.CENTER }
            c.drawText("歌手", 60f, 70f, p)
        }
        val embedded = CarPlayNowPlaying(title = "期待的未来", artist = "男孩 - Live · 梁博",
            elapsedMillis = 140000, durationMillis = 311000, sourceApp = "汽水音乐", playing = true)
        val explicit = embedded.copy(title = "男孩 - Live", artist = "梁博", lyrics = "[00:00.00]期待的未来")
        val folder = File("build/reports/cluster-music").apply { mkdirs() }
        fun save(bitmap: Bitmap, name: String) {
            File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        try {
            for (style in listOf("urban", "sport", "original")) {
                for ((w, h) in listOf(2048 to 768, 1280 to 480)) {
                    fun render(song: CarPlayNowPlaying): Bitmap {
                        val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        val c = Canvas(frame)
                        c.drawColor(Color.rgb(3, 9, 17))
                        val state = ClusterDashboardState(music = ClusterDashboardMusic(song, cover))
                        when (style) {
                            "urban" -> OemClusterRenderer(context).let { it.draw(c, w, h, state, true); it.release() }
                            "sport" -> SportClusterRenderer(context).let { it.draw(c, w, h, state, true); it.release() }
                            else -> OriginalClusterRenderer(context).let {
                                it.draw(c, w, h, OriginalClusterLayout(), state.vehicle, state.guidance, musicState = state.music)
                                it.releaseNavigationIcon()
                            }
                        }
                        return frame
                    }
                    val a = render(embedded)
                    val b = render(explicit)
                    try {
                        assertTrue("$style must render the same content regardless of lyric transport", a.sameAs(b))
                        save(a, "$style-$w")
                        val area = when (style) {
                            "urban" -> RectF(1410f, 200f, 1770f, 584f)
                            "sport" -> {
                                val sx = SportClusterGeometry.horizontalScale(w, h)
                                val sy = SportClusterGeometry.scale(w, h)
                                RectF(SportClusterGeometry.offsetX(w, h) + 850f * sx,
                                    SportClusterGeometry.offsetY(w, h) + 210f * sy,
                                    SportClusterGeometry.offsetX(w, h) + 1198f * sx,
                                    SportClusterGeometry.offsetY(w, h) + 490f * sy)
                            }
                            else -> OriginalClusterLayout().bounds(OriginalClusterComponent.MUSIC)
                        }
                        if (style != "sport") { area.left *= w / 2048f; area.right *= w / 2048f; area.top *= h / 768f; area.bottom *= h / 768f }
                        val crop = Bitmap.createBitmap(a, area.left.toInt(), area.top.toInt(), area.width().toInt(), area.height().toInt())
                        save(crop, "$style-music-$w"); crop.recycle()
                    } finally { a.recycle(); b.recycle() }
                }
            }
        } finally { cover.recycle() }
    }
}
