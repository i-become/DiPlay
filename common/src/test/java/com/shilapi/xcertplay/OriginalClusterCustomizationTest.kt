package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import com.shilapi.xcertplay.glance.CarPlayGlance
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OriginalClusterCustomizationTest {
    private val context = RuntimeEnvironment.getApplication()
    @Test fun legacyLayoutsKeepTheirPositionsAndGainAFullSizeMap() {
        val layout = OriginalClusterLayout.decode("""{"version":1,"tires":{"x":0.2,"y":0.3,"visible":false}}""")
        assertEquals(.2f, layout[OriginalClusterComponent.TIRES].x, .0001f)
        assertFalse(layout[OriginalClusterComponent.TIRES].visible)
        assertEquals(RectF(0f, 0f, 2048f, 768f), layout.bounds(OriginalClusterComponent.MAP))
        assertEquals(1f, layout[OriginalClusterComponent.MAP].opacity)
    }
    @Test fun independentMediaAndTransformsRoundTripWithoutSharingParameters() {
        val image = OriginalClusterMedia("image1", OriginalMediaType.IMAGE, "content://images/1")
        val secondImage = image.copy(id = "image2")
        val video = OriginalClusterMedia("video1", OriginalMediaType.VIDEO, "content://videos/1")
        val layout = OriginalClusterLayout(media = listOf(image, secondImage, video))
            .updateMedia("image1", image.placement.copy(rotation = 35f, scale = 1.6f, opacity = .7f, flipHorizontal = true))
            .update(OriginalClusterComponent.MUSIC, OriginalClusterPlacement(x = .1f, y = .2f,
                backgroundOpacity = .4f, opacity = .8f, rotation = -90f, scale = 1.2f, flipVertical = true))
        assertEquals(layout, OriginalClusterLayout.decode(layout.encode()))
        assertEquals(secondImage, layout.media[1])
        assertEquals(video, layout.media[2])
        val damaged = image.placement.copy(scale = Float.NaN, rotation = Float.POSITIVE_INFINITY, opacity = -1f).sanitized()
        assertEquals(1f, damaged.scale); assertEquals(0f, damaged.rotation); assertEquals(0f, damaged.opacity)
    }
    @Test fun mediaScalingAndStretchAreIndependentAndPersist() {
        for (type in OriginalMediaType.entries) {
            val item = OriginalClusterMedia("one", type, "content://media/1")
            val other = item.copy(id = "two")
            var layout = OriginalClusterLayout(media = listOf(item, other))
            for (scale in listOf(.05f, 10f)) {
                layout = layout.updateMedia(item.id, item.placement.copy(scale = scale))
                assertEquals(scale, OriginalClusterLayout.decode(layout.encode()).media.first().placement.scale, .001f)
            }
            layout = layout.stretchMediaFullScreen(item.id)
            val restored = OriginalClusterLayout.decode(layout.encode())
            assertEquals(layout, restored)
            assertEquals(RectF(0f, 0f, 2048f, 768f), restored.mediaBounds(restored.media.first()))
            assertEquals(1f, restored.media.first().placement.scale, 0f)
            assertEquals(other, restored.media.last())
        }
    }
    @Test fun layersReorderAcrossComponentsAndMediaAndSurviveEncoding() {
        val media = OriginalClusterMedia("video-1", OriginalMediaType.VIDEO, "content://videos/1")
        val layout = OriginalClusterLayout(media = listOf(media))
        assertEquals(0, layout.layerIndex(OriginalClusterComponent.MAP.key))
        assertTrue(layout.layerIndex(media.id) < layout.layerIndex(OriginalClusterComponent.MUSIC.key))
        val reordered = layout.moveLayerToEdge(media.id, front = true)
        assertEquals(reordered.orderedLayers.lastIndex, reordered.layerIndex(media.id))
        assertEquals(reordered, OriginalClusterLayout.decode(reordered.encode()))
        assertEquals(0, reordered.moveLayerToEdge(media.id, front = false).layerIndex(media.id))
    }
    @Test fun musicStylePersistsCustomSizesColorsAndProgressLength() {
        val style = OriginalMusicStyle(titleSize = 30f, titleColor = 0xFFFF00FF.toInt(), showTime = false, showPlaybackControls = false, artistSize = 22f, artistColor = 0xFFFFD166.toInt(),
            lyricSize = 36f, lyricColor = 0xFF76E4F7.toInt(), progressLength = .47f,
            progressColor = 0xFF7EE0A3.toInt(), coverSize = 138f)
        val layout = OriginalClusterLayout().updateMusicStyle(style)
        assertEquals(style, OriginalClusterLayout.decode(layout.encode()).musicStyle)
    }
    @Test fun musicStyleClampsExtremesAndKeepsAdaptiveCardInsideItsComponent() {
        val style = OriginalMusicStyle(artistSize = 100f, lyricSize = 100f,
            progressLength = 0f, coverSize = 500f).sanitized()
        assertEquals(24f, style.artistSize, 0f)
        assertEquals(38f, style.lyricSize, 0f)
        assertEquals(.35f, style.progressLength, 0f)
        assertEquals(288f, style.coverSize, 0f)
        val renderer = OriginalClusterRenderer(context)
        val bitmap = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        try {
            for (cover in listOf(64f, 116f, 288f)) for (lyric in listOf(18f, 32f, 38f)) {
                bitmap.eraseColor(Color.TRANSPARENT)
                var layout = OriginalClusterLayout()
                OriginalClusterComponent.entries.filterNot { it == OriginalClusterComponent.MUSIC }
                    .forEach { layout = layout.show(it, false) }
                layout = layout.updateMusicStyle(OriginalMusicStyle(coverSize = cover, lyricSize = lyric))
                renderer.draw(Canvas(bitmap), 2048, 768, layout, OriginalClusterOverlayView.previewVehicle,
                    OriginalClusterOverlayView.previewNav,
                    musicState = ClusterDashboardMusic(CarPlayNowPlaying(title = "Title", artist = "Artist",
                        lyrics = "A long lyric line that may overflow the available area", durationMillis = 120000)),
                )
                val bounds = layout.bounds(OriginalClusterComponent.MUSIC)
                assertEquals("no card spill at $cover/$lyric", Color.TRANSPARENT,
                    bitmap.getPixel((bounds.right + 3).toInt(), (bounds.top + 30).toInt()))
                assertTrue("card rendered at $cover/$lyric", Color.alpha(bitmap.getPixel(
                    bounds.left.toInt() + 100, bounds.top.toInt() + 80)) > 0)
            }
        } finally { bitmap.recycle(); renderer.releaseNavigationIcon() }
    }
    @Test fun previousOpacityMultipliersMigrateWithoutChangingTheExistingAppearance() {
        val previous = OriginalClusterLayout.decode("""{"version":3,"tires":{"backgroundOpacity":0.5}}""")
        assertEquals(46f / 255f, previous[OriginalClusterComponent.TIRES].backgroundOpacity, .0001f)
        assertEquals(previous, OriginalClusterLayout.decode(previous.encode()))
    }
    @Test fun hitTestingUsesTheRotatedScaledShapeRatherThanItsOldRectangle() {
        val r = RectF(0f, 0f, 200f, 80f)
        val p = OriginalClusterPlacement(x = 0f, y = 0f, rotation = 90f, scale = 2f)
        assertTrue(originalHit(r, p, 100f, 200f))
        assertFalse(originalHit(r, p, 0f, 0f))
        assertFalse(originalHit(r, p.copy(visible = false), 100f, 40f))
    }
    @Test fun rotatingCardsKeepsTextHorizontalAndCardOpacityIndependent() {
        var layout = OriginalClusterLayout()
        OriginalClusterComponent.entries.forEach { layout = layout.show(it, false) }
        val component = OriginalClusterComponent.TIME
        layout = layout.update(component, layout[component].copy(visible = true, x = .4f, y = .4f,
            rotation = 90f, backgroundOpacity = 0f))
        val bitmap = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        val renderer = OriginalClusterRenderer(context)
        try {
            val snapshot = CarPlayGlance.Snapshot(connected = true, navigating = true, routeSummary = "MMMMMMMMMMMM")
            renderer.draw(Canvas(bitmap), 2048, 768, layout, OemClusterVehicleState(), snapshot)
            fun textBounds(image: Bitmap): IntArray {
                var minX = 2048; var maxX = 0; var minY = 768; var maxY = 0
                for (y in 0 until 768) for (x in 0 until 2048) {
                    val color = image.getPixel(x, y)
                    if (Color.alpha(color) > 128 && Color.red(color) > 220) {
                        minX = minOf(minX, x); maxX = maxOf(maxX, x); minY = minOf(minY, y); maxY = maxOf(maxY, y)
                    }
                }
                return intArrayOf(minX, maxX, minY, maxY)
            }
            val rotatedText = textBounds(bitmap)
            assertTrue("Glyph anchors follow the card's rotated baseline",
                rotatedText[3] - rotatedText[2] > rotatedText[1] - rotatedText[0])
            assertTrue("Rotated text remains inside the instrument plane", rotatedText[0] >= 0 && rotatedText[1] < 2048 &&
                rotatedText[2] >= 0 && rotatedText[3] < 768)
        } finally { bitmap.recycle(); renderer.releaseNavigationIcon() }
    }
    @Test fun mapAndMediaGeometryChangesKeepTheSameVideoView() {
        val scene = OriginalClusterScene(context, preview = true)
        val layers = scene.getChildAt(0) as android.widget.FrameLayout
        val map = layers.getChildAt(0)
        val media = OriginalClusterMedia("a", OriginalMediaType.IMAGE, "content://images/1")
        scene.sceneLayout = OriginalClusterLayout(media = listOf(media, media.copy(id = "b")))
            .update(OriginalClusterComponent.MAP, OriginalClusterPlacement(x = .1f, y = .1f, scale = .5f, rotation = 20f, opacity = .4f))
        scene.measure(android.view.View.MeasureSpec.makeMeasureSpec(2048, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(768, android.view.View.MeasureSpec.EXACTLY))
        scene.layout(0, 0, 2048, 768)
        assertSame(map, layers.getChildAt(0))
        assertEquals(.5f, map.scaleX); assertEquals(20f, map.rotation); assertEquals(.4f, map.alpha)
        assertEquals(2, scene.childCount) // composited layers and touch overlay
        val first = layers.getChildAt(1)
        scene.sceneLayout = scene.sceneLayout.updateMedia("a", media.placement.copy(opacity = .2f))
        assertSame(first, layers.getChildAt(1))
        scene.sceneLayout = scene.sceneLayout.copy(media = emptyList())
        assertEquals(2, scene.childCount)
    }

    @Test fun previewDraggingMovesImmediatelyWithoutWaitingForALongPress() {
        val scene = OriginalClusterScene(context, preview = true)
        scene.measure(android.view.View.MeasureSpec.makeMeasureSpec(2048, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(768, android.view.View.MeasureSpec.EXACTLY))
        scene.layout(0, 0, 2048, 768)
        val component = OriginalClusterComponent.TIRES
        val before = scene.sceneLayout[component]
        val bounds = scene.sceneLayout.bounds(component)
        fun touch(action: Int, dx: Float, dy: Float, time: Long) {
            val event = android.view.MotionEvent.obtain(0, time, action, bounds.centerX() + dx, bounds.centerY() + dy, 0)
            try { assertTrue(scene.dispatchTouchEvent(event)) } finally { event.recycle() }
        }
        touch(android.view.MotionEvent.ACTION_DOWN, 0f, 0f, 0)
        touch(android.view.MotionEvent.ACTION_MOVE, 100f, -40f, 16)
        touch(android.view.MotionEvent.ACTION_UP, 100f, -40f, 32)
        assertEquals(before.x + 100f / 2048, scene.sceneLayout[component].x, .0001f)
        assertEquals(before.y - 40f / 768, scene.sceneLayout[component].y, .0001f)
        assertEquals(before, scene.sceneLayout[OriginalClusterComponent.TIRES].copy(x = before.x, y = before.y))
    }

    @Test fun everyCardBackgroundCoversTheFullOpacityRangeWithoutHidingContent() {
        val renderer = OriginalClusterRenderer(context)
        val bitmap = Bitmap.createBitmap(2048, 768, Bitmap.Config.ARGB_8888)
        try {
            for (component in OriginalClusterComponent.entries.filter { it != OriginalClusterComponent.MAP }) {
                var layout = OriginalClusterLayout()
                OriginalClusterComponent.entries.forEach { layout = layout.show(it, false) }
                val r = layout.bounds(component)
                for (opacity in listOf(0f, .5f, 1f)) {
                    bitmap.eraseColor(Color.TRANSPARENT)
                    layout = layout.update(component, layout[component].copy(visible = true, backgroundOpacity = opacity))
                    renderer.draw(Canvas(bitmap), 2048, 768, layout, OriginalClusterOverlayView.previewVehicle,
                        OriginalClusterOverlayView.previewNav)
                    val alpha = Color.alpha(bitmap.getPixel((r.left + 5).toInt(), r.centerY().toInt()))
                    assertEquals("$component at $opacity", (255 * opacity).toInt(), alpha)
                }
            }
        } finally { bitmap.recycle(); renderer.releaseNavigationIcon() }
    }

    @Test fun unavailableVideoDecoderDoesNotCrashInstrumentSettingsOrRetainSurface() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val scene = OriginalClusterScene(controller.get(), preview = true)
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { _, _ ->
            throw RuntimeException("native decoder unavailable")
        }
        try {
            scene.sceneLayout = OriginalClusterLayout(media = listOf(
                OriginalClusterMedia("unavailable", OriginalMediaType.VIDEO, "content://videos/unavailable")))
            controller.get().setContentView(scene)
            val layers = scene.getChildAt(0) as android.view.ViewGroup
            val mediaView = layers.getChildAt(1) as android.view.ViewGroup
            val view = mediaView.getChildAt(0) as android.view.TextureView
            if (view.surfaceTexture == null) view.setSurfaceTexture(android.graphics.SurfaceTexture(0))
            view.surfaceTextureListener!!.onSurfaceTextureAvailable(view.surfaceTexture!!, 300, 200)
            assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(mediaView, "player"))
            assertNull(org.robolectric.util.ReflectionHelpers.getField<Any?>(mediaView, "output"))
        } finally {
            controller.pause().stop().destroy()
            org.robolectric.shadows.ShadowMediaPlayer.setCreateListener(null)
        }
    }

    @Test fun multipleVideosLoopMutedPauseWhenHiddenAndReleaseWhenRemoved() {
        val controller = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().visible()
        val scene = OriginalClusterScene(controller.get(), preview = true)
        val uri = android.net.Uri.parse("content://videos/loop")
        val players = mutableListOf<android.media.MediaPlayer>()
        org.robolectric.shadows.ShadowMediaPlayer.addMediaInfo(
            org.robolectric.shadows.util.DataSource.toDataSource(controller.get(), uri),
            org.robolectric.shadows.ShadowMediaPlayer.MediaInfo(10_000, 10))
        org.robolectric.shadows.ShadowMediaPlayer.setCreateListener { player, _ -> players += player }
        try {
            val item = OriginalClusterMedia("one", OriginalMediaType.VIDEO, uri.toString())
            scene.sceneLayout = OriginalClusterLayout(media = listOf(item, item.copy(id = "two")))
            controller.get().setContentView(scene)
            val layers = scene.getChildAt(0) as android.view.ViewGroup
            for (index in 1..2) {
                val mediaView = layers.getChildAt(index) as android.view.ViewGroup
                val view = mediaView.getChildAt(0) as android.view.TextureView
                if (view.surfaceTexture == null) view.setSurfaceTexture(android.graphics.SurfaceTexture(0))
                view.surfaceTextureListener!!.onSurfaceTextureAvailable(view.surfaceTexture!!, 300, 200)
            }
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(20))
            assertEquals(2, players.size)
            players.forEach {
                assertTrue(it.isLooping)
                assertTrue(it.isPlaying)
                assertEquals(0f, org.robolectric.Shadows.shadowOf(it).leftVolume)
            }
            scene.setSceneVisible(false)
            players.forEach { assertFalse(it.isPlaying) }
            scene.setSceneVisible(true)
            players.forEach { assertTrue(it.isPlaying) }
            scene.sceneLayout = scene.sceneLayout.copy(media = emptyList())
            players.forEach { assertEquals(org.robolectric.shadows.ShadowMediaPlayer.State.END,
                org.robolectric.Shadows.shadowOf(it).state) }
        } finally {
            controller.pause().stop().destroy()
            org.robolectric.shadows.ShadowMediaPlayer.setCreateListener(null)
        }
    }
}
