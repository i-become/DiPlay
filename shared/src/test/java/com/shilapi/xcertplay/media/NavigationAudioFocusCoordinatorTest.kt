package com.shilapi.xcertplay.media

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Looper
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5NavigationAudioFocusTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val main get() = shadowOf(Looper.getMainLooper())
    private val owners = mutableListOf<Hs5NavigationAudioFocus>()

    @Before fun setup() {
        DiagnosticLogging.setEnabled(false)
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
    }

    @After fun cleanup() { owners.forEach { it.close() } }

    @Test fun actualNavigationUsesOemFocusAndReleasesAfterBufferedAudioAndTailWithoutChangingVolumes() {
        val owner = owner()
        val token = NavigationPlaybackTracker().open()
        val music = manager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val notification = manager.getStreamVolume(AudioManager.STREAM_NOTIFICATION)
        assertNull(shadowOf(manager).lastAudioFocusRequest)
        owner.played(token, 14, 500, false)
        main.idle()
        val sent = shadowOf(manager).lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, sent.focusGain)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, sent.audioAttributes.usage)
        assertEquals(AudioAttributes.CONTENT_TYPE_SPEECH, sent.audioAttributes.contentType)
        assertTrue(owner.held)
        main.idleFor(Duration.ofMillis(1300))
        assertTrue(owner.held)
        main.idleFor(Duration.ofMillis(100))
        assertFalse(owner.held)
        assertSame(sent, shadowOf(manager).lastAbandonedAudioFocusRequest)
        assertEquals(music, manager.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(notification, manager.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
    }

    @Test fun navigationFocusIsRequestedSynchronouslyBeforeAudioTrackStarts() {
        val owner = owner()
        val token = NavigationPlaybackTracker().open()
        owner.prepare(token, 14, 500)

        val sent = shadowOf(manager).lastAudioFocusRequest.audioFocusRequest
        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, sent.focusGain)
        assertEquals(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE, sent.audioAttributes.usage)
        assertTrue(owner.held)
        assertNull(shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test fun diagnosticVolumeLevelsAreReadOnlyAndRecordStandardStreamChanges() {
        val diagnostics = mutableListOf<String>()
        val stream = AudioManager.STREAM_NOTIFICATION
        val original = manager.getStreamVolume(stream)
        val maximum = manager.getStreamMaxVolume(stream)
        val target = if (original == maximum) original - 1 else original + 1
        val owner = Hs5NavigationAudioFocus(context, true, diagnostics::add).also(owners::add)
        val token = NavigationPlaybackTracker().open()
        try {
            DiagnosticLogging.setEnabled(true)
            owner.prepare(token, 14, 500)
            main.idle()
            assertTrue(diagnostics.any { it.contains("volume levels navigation=14:") })
            manager.setStreamVolume(stream, target, 0)
            main.idleFor(Duration.ofMillis(100))
            assertTrue(diagnostics.any { it.contains("stream$stream=$target/") })
            assertEquals(target, manager.getStreamVolume(stream))
        } finally {
            manager.setStreamVolume(stream, original, 0)
            DiagnosticLogging.setEnabled(false)
        }
    }

    @Test fun genericDevicesFallbackMediaAndConflictingRoutesDoNotClaimNavigationFocus() {
        val disabled = owner(false)
        val token = NavigationPlaybackTracker().open()
        disabled.played(token, 14, 0, false)
        val owner = owner()
        owner.played(token, null, 0, false)
        main.idle()
        assertNull(shadowOf(manager).lastAudioFocusRequest)
        owner.played(token, AudioManager.STREAM_MUSIC, 0, false)
        main.idleFor(Duration.ofMillis(100))
        assertNull(shadowOf(manager).lastAudioFocusRequest)
        owner.played(token, 14, 0, false)
        owner.played(NavigationPlaybackTracker().open(), 13, 0, false)
        main.idleFor(Duration.ofMillis(100))
        assertNull(shadowOf(manager).lastAudioFocusRequest)
    }

    @Test fun callOrSiriReleasesNavigationFocusAndRemainingGuidanceCanResume() {
        val owner = owner()
        val navigation = NavigationPlaybackTracker().open()
        val voice = NavigationPlaybackTracker().open()
        owner.played(navigation, 14, 3000, false)
        main.idle()
        val sent = shadowOf(manager).lastAudioFocusRequest.audioFocusRequest
        owner.played(voice, null, 0, true)
        main.idleFor(Duration.ofMillis(100))
        assertFalse(owner.held)
        assertSame(sent, shadowOf(manager).lastAbandonedAudioFocusRequest)
        owner.release(voice)
        main.idle()
        assertTrue(owner.held)
        assertNotSame(sent, shadowOf(manager).lastAudioFocusRequest.audioFocusRequest)
    }

    @Test fun overlappingRenderersAndSinkCloseRespectOwnershipAndRejectLateCallbacks() {
        val owner = owner()
        val first = NavigationPlaybackTracker().open()
        val second = NavigationPlaybackTracker().open()
        owner.played(first, 14, 0, false)
        owner.played(second, 14, 0, false)
        main.idle()
        val sent = shadowOf(manager).lastAudioFocusRequest
        owner.release(first)
        main.idle()
        assertTrue(owner.held)
        assertSame(sent, shadowOf(manager).lastAudioFocusRequest)
        owner.close()
        assertFalse(owner.held)
        owner.played(second, 14, 10000, false)
        sent.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN)
        main.idleFor(Duration.ofSeconds(2))
        assertFalse(owner.held)
        assertSame(sent.audioFocusRequest, shadowOf(manager).lastAbandonedAudioFocusRequest)
    }

    @Test fun deniedAndLostFocusAreNotRetriedOnEveryPacketAndNextUtteranceCanTryAgain() {
        val owner = owner()
        val token = NavigationPlaybackTracker().open()
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_FAILED)
        owner.played(token, 14, 0, false)
        main.idle()
        val denied = shadowOf(manager).lastAudioFocusRequest
        assertFalse(owner.held)
        shadowOf(manager).setNextFocusRequestResponse(AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        owner.played(token, 14, 0, false)
        main.idleFor(Duration.ofMillis(100))
        assertSame(denied, shadowOf(manager).lastAudioFocusRequest)
        main.idleFor(Duration.ofSeconds(1))
        owner.played(token, 14, 0, false)
        main.idle()
        assertTrue(owner.held)
        val granted = shadowOf(manager).lastAudioFocusRequest
        granted.listener.onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS_TRANSIENT)
        owner.played(token, 14, 0, false)
        main.idleFor(Duration.ofMillis(100))
        assertFalse(owner.held)
        assertSame(granted, shadowOf(manager).lastAudioFocusRequest)
    }

    private fun owner(enabled: Boolean = true) = Hs5NavigationAudioFocus(context, enabled).also(owners::add)
}
