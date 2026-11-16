package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import java.io.ByteArrayOutputStream
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [33])
class CarlinkDashboardStateTest {
    @After fun clear() = CarlinkDashboardState.clear()

    @Test fun lyricUpdatesReachDashboardAndAreClearedWithTheSession() {
        val track = CarPlayNowPlaying(title = "Song", lyrics = "第一句")
        CarlinkDashboardState.update(track, null)
        assertEquals("第一句", CarlinkDashboardState.music().metadata.lyrics)
        CarlinkDashboardState.update(track.copy(lyrics = "第二句", elapsedMillis = 2000), null)
        assertEquals("第二句", CarlinkDashboardState.music().metadata.lyrics)
        CarlinkDashboardState.clear()
        assertNull(CarlinkDashboardState.music().metadata.lyrics)
    }

    @Test fun progressUpdatesReuseCoverAndOldDrawingSnapshotsRemainValid() {
        val bytes = ByteArrayOutputStream().also {
            Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it)
        }.toByteArray()
        val track = CarPlayNowPlaying(title = "Song", artist = "Artist")
        CarlinkDashboardState.update(track, bytes)
        val first = CarlinkDashboardState.music()
        assertNotNull(first.artwork)
        CarlinkDashboardState.update(track.copy(elapsedMillis = 1000), bytes.copyOf())
        assertSame(first.artwork, CarlinkDashboardState.music().artwork)
        CarlinkDashboardState.update(track.copy(elapsedMillis = 2000), null)
        assertSame(first.artwork, CarlinkDashboardState.music().artwork)
        CarlinkDashboardState.update(track.copy(title = "Next song"), null)
        assertNull(CarlinkDashboardState.music().artwork)
        assertFalse(first.artwork!!.isRecycled)
        CarlinkDashboardState.clear()
        assertNull(CarlinkDashboardState.music().metadata.title)
        assertFalse(first.artwork.isRecycled)
    }
}
