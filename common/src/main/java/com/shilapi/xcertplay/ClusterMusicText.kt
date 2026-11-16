package com.shilapi.xcertplay

import com.shilapi.xcertplay.media.CarPlayNowPlaying

/** Presentation shared by every cluster and phone transport. */
internal data class ClusterMusicText(val title: String?, val artist: String?, val lyric: String?, val lyricRemainingMillis: Long? = null) {
    companion object {
        // Some players' car lyric mode sends the current line as title and
        // "track · performer" as artist instead of a dedicated lyrics field.
        private val trackAndPerformer = Regex("^(.+?)\\s*[·•]\\s*(.+)$")

        fun from(song: CarPlayNowPlaying): ClusterMusicText {
            val title = song.title?.trim()?.takeIf(String::isNotEmpty)
            val artist = song.artist?.trim()?.takeIf(String::isNotEmpty)
            val lyric = ClusterLyrics.line(song.lyrics, song.elapsedMillis)
            if (song.lyrics.isNullOrBlank() && title != null && artist != null) {
                val combined = trackAndPerformer.matchEntire(artist)
                if (combined != null) {
                    val track = combined.groupValues[1].trim()
                    val performer = combined.groupValues[2].trim()
                    if (title != track) return ClusterMusicText(track, performer, title)
                }
            }
            return ClusterMusicText(title, artist, lyric.text, lyric.remainingMillis)
        }
    }
}
