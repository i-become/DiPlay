package com.shilapi.xcertplay.carlink

import android.graphics.BitmapFactory
import com.shilapi.xcertplay.ClusterDashboardMusic
import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** Latest Carlink media metadata for the existing instrument dashboard renderer. */
internal object CarlinkDashboardState {
    @Volatile private var current = ClusterDashboardMusic()
    private var coverBytes: ByteArray? = null

    fun music(): ClusterDashboardMusic = current

    @Synchronized fun update(next: CarPlayNowPlaying, cover: ByteArray?) {
        val sameTrack = next.title == current.metadata.title && next.artist == current.metadata.artist &&
            next.album == current.metadata.album
        val artwork = when {
            cover == null -> if (sameTrack) current.artwork else null
            cover.contentEquals(coverBytes) -> current.artwork
            else -> runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(cover, 0, cover.size, bounds)
                val options = BitmapFactory.Options().apply { inSampleSize = 1 }
                while (maxOf(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 512) options.inSampleSize *= 2
                BitmapFactory.decodeByteArray(cover, 0, cover.size, options)
            }.getOrNull()
        }
        if (cover != null && !cover.contentEquals(coverBytes)) coverBytes = cover.copyOf()
        else if (cover == null && !sameTrack) coverBytes = null
        // Renderers can still hold the previous snapshot. Let GC release its bitmap.
        current = ClusterDashboardMusic(next, artwork)
    }

    @Synchronized fun clear() { current = ClusterDashboardMusic(); coverBytes = null }
}
