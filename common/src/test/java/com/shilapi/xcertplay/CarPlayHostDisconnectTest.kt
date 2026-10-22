package com.shilapi.xcertplay

import android.os.Handler
import android.os.Looper
import android.app.Activity
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Surface
import android.view.View
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.newui.connection.NewUiConnectionPhase
import com.shilapi.xcertplay.newui.connection.NewUiConnectionSnapshotReader
import java.time.Duration
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayHostDisconnectTest {
    private lateinit var activity: CarPlayHostActivity
    private lateinit var session: AirPlaySession
    private lateinit var video: View
    private lateinit var gestures: View
    private var stops = 0

    @Before fun setUp() {
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        session = mock(AirPlaySession::class.java)
        video = View(activity)
        gestures = View(activity)
        field("videoView", video)
        field("gestureOverlay", gestures)
        field("activeAirPlaySession", session)
        field("callPopupSessionReady", true)
        field("hostAppearanceResumed", true)
        val controller = mock(CarPlayController::class.java)
        field("controller", controller)
        CarPlayBackgroundSession.store(controller, mock(AndroidMediaSink::class.java), 1920, 720,
            activity, mock(CarPlaySessionDisplay::class.java)) { completion ->
            stops++
            activity.javaClass.getDeclaredMethod("shutdown", Boolean::class.javaPrimitiveType,
                String::class.java, kotlin.jvm.functions.Function0::class.java).apply { isAccessible = true }
                .invoke(activity, false, "test disconnect", completion)
        }
        CarPlayBackgroundSession.active = true
        streams().addAll(listOf(110, 111))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
    }

    @After fun tearDown() {
        (field("teardownExecutor") as ExecutorService).apply { shutdown(); awaitTermination(3, TimeUnit.SECONDS) }
        shadowOf(Looper.getMainLooper()).idle()
        (field("mainHandler") as Handler).removeCallbacksAndMessages(null)
        (field("airPlayCommandExecutor") as ExecutorService).shutdownNow()
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        Hs5ClusterActivityOutput.close("test finished")
    }

    @Test fun phoneDisconnectClearsThePictureAndReturnsProjectionToTheConnectionPage() {
        listener().onSessionEnded(session)
        assertOutputCleared()
        assertEquals(true, field("newUiConnectionHub"))
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals("com.shilapi.xcertplay.newui.NewUiActivity", intent.component?.className)
        assertTrue(ConnectionProgressUiState.disconnected())
        assertEquals(NewUiConnectionPhase.DISCONNECTED, NewUiConnectionSnapshotReader.read(activity, null).phase)
        assertEquals(1, stops)
    }

    @Test fun openingSettingsDoesNotDeferRemovingDisconnectedVideo() {
        field("menuOpen", true)
        listener().onSessionEnded(session)
        assertOutputCleared()
        assertEquals(false, field("recoveryPendingAfterMenu"))
        assertNotNull(shadowOf(activity).nextStartedActivity)
        assertEquals(1, stops)
    }

    @Test fun exhaustedRetryBudgetDoesNotPreventDisconnectCleanup() {
        field("startupRetryStopped", true)
        listener().onSessionEnded(session)
        assertOutputCleared()
        assertEquals(false, field("reconnectScheduled"))
    }

    @Test fun transportFailureAlsoClearsThePictureWhileSettingsAreOpen() {
        field("menuOpen", true)
        listener().onTransportError("phone closed connection")
        assertOutputCleared()
        assertEquals(false, field("recoveryPendingAfterMenu"))
        assertTrue(ConnectionProgressUiState.disconnected())
    }

    @Test fun anOldSessionEndingCannotHideItsReplacement() {
        listener().onSessionEnded(mock(AirPlaySession::class.java))
        assertEquals(session, field("activeAirPlaySession"))
        assertEquals(View.VISIBLE, video.visibility)
        assertEquals(setOf(110, 111), streams())
        assertTrue(CarPlayBackgroundSession.active)
    }

    @Test fun anOldControllerEndingCannotHideTheCurrentSession() {
        listener(-1).onSessionEnded(session)
        assertEquals(session, field("activeAirPlaySession"))
        assertEquals(View.VISIBLE, video.visibility)
        assertTrue(CarPlayBackgroundSession.active)
    }

    @Test fun aBackgroundDisconnectDoesNotOpenDiPlayOverAnotherApp() {
        field("hostAppearanceResumed", false)
        listener().onSessionEnded(session)
        assertOutputCleared()
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun lateErrorsAndActiveCallbacksCannotReconnectOrReopenTheFlyScreen() {
        val current = listener()
        field("clusterFlyScreenVisible", true)
        current.onSessionEnded(session)
        current.onTransportError("iAP2 link ended")
        current.onSessionEnded(session)
        current.onSessionActive(session)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertOutputCleared()
        assertEquals(false, field("clusterFlyScreenVisible"))
        assertEquals(false, field("reconnectScheduled"))
        assertEquals(1, stops)
        assertTrue(ConnectionProgressUiState.disconnected())
    }

    @Test fun anAdoptedActiveSessionAlsoStopsWhenItEndsBeforeItsFirstUiCallback() {
        field("activeAirPlaySession", null)
        listener().onSessionEnded(session)
        assertOutputCleared()
        assertTrue(ConnectionProgressUiState.disconnected())
        assertEquals(1, stops)
    }

    @Test fun disconnectFinishesTheRealClusterWindowEvenWhileItOwnsFocus() {
        val launcher = Robolectric.buildActivity(Activity::class.java).setup().get()
        val display = launcher.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val host = object : Hs5ClusterActivityOutput.Host {
            override fun onClusterWindowSurface(surface: Surface?) = Unit
            override fun onClusterWindowClosed(reason: String) = Unit
        }
        assertTrue(Hs5ClusterActivityOutput.open(launcher, display, host))
        val window = Robolectric.buildActivity(Hs5ClusterActivity::class.java,
            shadowOf(launcher).nextStartedActivity).setup().get()
        field("hostAppearanceResumed", false)
        field("isActivityStarted", true)
        field("clusterFlyScreenVisible", true)
        listener().onSessionEnded(session)
        assertTrue(window.isFinishing)
        assertFalse(Hs5ClusterActivityOutput.isOpen())
        assertEquals(false, field("clusterFlyScreenVisible"))
        assertNotNull(shadowOf(activity).nextStartedActivity)
    }

    private fun assertOutputCleared() {
        assertNull(field("activeAirPlaySession"))
        assertFalse(CarPlayBackgroundSession.active)
        assertEquals(false, field("callPopupSessionReady"))
        assertTrue(streams().isEmpty())
        assertEquals(View.INVISIBLE, video.visibility)
        assertEquals(View.GONE, gestures.visibility)
    }

    private fun listener(generation: Int = 0): AirPlaySessionListener =
        activity.javaClass.getDeclaredMethod("createSessionListener", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(activity, generation) as AirPlaySessionListener

    @Suppress("UNCHECKED_CAST")
    private fun streams() = field("activeScreenStreamTypes") as MutableSet<Int>
    private fun field(name: String): Any? = activity.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }.get(activity)
    private fun field(name: String, value: Any?) {
        activity.javaClass.getDeclaredField(name).apply { isAccessible = true }.set(activity, value)
    }
}
