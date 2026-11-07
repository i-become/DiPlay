package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.shilapi.xcertplay.compat.AudioFocusRequestCompat
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

/**
 * Matches Hongqi's OEM Carlink focus request so its audio policy can select navigation volume:
 * navigation usage with GAIN_TRANSIENT_MAY_DUCK for the TTS stream.
 * Hold that focus only through actual PCM playback, never for an idle AirPlay audio connection.
 */
internal class NavigationAudioFocusCoordinator(
    context: Context?,
    private val enabled: Boolean,
    private val report: (String) -> Unit = {},
    handler: Handler? = null,
    val navigationAttributes: AudioAttributes? = HongqiAudioAttributes.navigationOrNull(context),
) {
    private val manager = context?.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val callbackHandler by lazy { handler ?: Handler(Looper.getMainLooper()) }
    private val playback = NavigationPlaybackTracker(nowMillis = SystemClock::uptimeMillis)
    private var request: AudioFocusRequestCompat? = null
    private var generation = 0L
    private var scheduled = false
    private var closed = false
    private var lastVolumeLevels: String? = null
    @Volatile var held = false
        private set

    private val refresh = Runnable { synchronized(this) { refreshLocked() } }

    @Synchronized
    fun played(token: NavigationPlaybackTracker.Token, stream: Int?, bufferedMillis: Long, priorityVoice: Boolean) {
        if (!enabled || closed || token.closed) return
        playback.played(token, stream, bufferedMillis, priorityVoice)
        if (!scheduled) {
            scheduled = true
            callbackHandler.post(refresh)
        }
    }

    /** Select the navigation focus before AudioTrack starts rendering the first PCM frames. */
    @Synchronized
    fun prepare(token: NavigationPlaybackTracker.Token, stream: Int?, bufferedMillis: Long) {
        if (!enabled || closed || token.closed) return
        playback.played(token, stream, bufferedMillis, priorityVoice = false)
        if (scheduled) callbackHandler.removeCallbacks(refresh)
        scheduled = false
        refreshLocked()
    }

    @Synchronized
    fun release(token: NavigationPlaybackTracker.Token) {
        playback.close(token)
        if (!enabled || closed) return
        callbackHandler.removeCallbacks(refresh)
        scheduled = true
        callbackHandler.post(refresh)
    }

    @Synchronized
    fun close() {
        if (closed) return
        closed = true
        if (scheduled) callbackHandler.removeCallbacks(refresh)
        scheduled = false
        abandonLocked()
    }

    private fun refreshLocked() {
        scheduled = false
        if (closed) return
        val state = playback.snapshot()
        val eligible = state.active && !state.priorityVoiceActive &&
            (state.legacyStreamType == AudioChannelMapper.DEFAULT_NAVIGATION_STREAM_TYPE ||
                (state.legacyStreamType == null && navigationAttributes != null))
        state.legacyStreamType?.takeIf { eligible }?.let(::logVolumeLevels)
        val audio = manager
        if (eligible && request == null && audio != null) {
            val currentGeneration = ++generation
            val next = AudioFocusRequestCompat(
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                navigationAttributes ?: AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
                { change -> synchronized(this) {
                    if (!closed && generation == currentGeneration && request != null) {
                        held = change == AudioManager.AUDIOFOCUS_GAIN
                        log("change=$change held=$held")
                    }
                } },
                callbackHandler,
            )
            request = next
            // Mark the pending request before Android can notify this app's media focus listener.
            held = true
            val result = runCatching { next.request(audio) }.getOrNull()
            held = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            log("requested stream=${state.legacyStreamType} granted=$result held=$held")
            // A denied request is not retried every PCM packet. The next utterance may try again.
        } else if (!eligible) {
            abandonLocked()
        }
        if (state.active || state.priorityVoiceActive) {
            scheduled = true
            callbackHandler.postDelayed(refresh, REFRESH_MS)
        }
    }

    private fun abandonLocked() {
        val previous = request ?: return
        generation++
        request = null
        held = false
        runCatching { manager?.let(previous::abandon) }
        log("released")
    }

    private fun log(message: String) {
        runCatching { DiagnosticLogging.record { report("Audio: navigation volume focus $message") } }
    }

    private fun logVolumeLevels(navigationStream: Int) {
        if (!DiagnosticLogging.enabled) return
        val audio = manager ?: return
        fun level(stream: Int): String = runCatching {
            "${audio.getStreamVolume(stream)}/${audio.getStreamMaxVolume(stream)}"
        }.getOrDefault("unavailable")

        val standardStreams = listOf(
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_DTMF,
            AudioManager.STREAM_ACCESSIBILITY,
        )
        val levels = buildString {
            append("navigation=$navigationStream:${level(navigationStream)}")
            standardStreams.forEach { append(" stream$it=${level(it)}") }
        }
        if (levels != lastVolumeLevels) {
            lastVolumeLevels = levels
            log("volume levels $levels")
        }
    }

    private companion object {
        const val REFRESH_MS = 100L
    }
}
