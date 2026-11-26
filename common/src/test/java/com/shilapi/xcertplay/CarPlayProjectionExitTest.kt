package com.shilapi.xcertplay

import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import android.os.Looper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.view.KeyEvent
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayStatus
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayProjectionExitTest {
    private lateinit var host: CarPlayHostActivity
    private val controller = mock(CarPlayController::class.java)
    private val sink = mock(AndroidMediaSink::class.java)
    private val session = mock(AirPlaySession::class.java)
    private lateinit var retainedListener: AirPlaySessionListener
    private lateinit var retainedStatus: (CarPlayStatus) -> Unit

    @Before fun setUp() {
        DiagnosticLogging.setEnabled(false)
        ConnectionProgressUiState.reset()
        host = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        AirPlayPersistence.saveClusterMapEnabled(host, false)
        ReflectionHelpers.setField(host, "controller", controller)
        ReflectionHelpers.setField(host, "sink", sink)
        ReflectionHelpers.setField(host, "isActivityStarted", true)
        ReflectionHelpers.setField(host, "activeAirPlaySession", session)
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        CarPlayBackgroundSession.active = true
        CarPlayBackgroundSession.store(controller, sink, 1920, 720, host,
            mock(CarPlaySessionDisplay::class.java)) { it() }
        doAnswer {
            retainedListener = it.getArgument(0)
            retainedStatus = it.getArgument(1)
            null
        }.`when`(controller).attachUi(
            any(AirPlaySessionListener::class.java) ?: object : AirPlaySessionListener {},
            any() ?: {},
        )
        CarPlayHostSettingsBridge.register(host)
    }

    @After fun tearDown() {
        CarPlayHostSettingsBridge.unregister(host)
        CarPlayBackgroundSession.active = false
        CarPlayBackgroundSession.clear(controller)
        shadowOf(Looper.getMainLooper()).idle()
        ConnectionProgressUiState.reset()
    }

    @Test fun closingProjectionFinishesActivityAndKeepsTheLiveSessionAndRenderer() {
        assertTrue(host.projectionShown())
        assertTrue(host.exitProjection("test wheel back"))
        assertTrue(host.isFinishing)
        assertFalse(host.projectionShown())
        assertFalse(host.exitProjection("duplicate back"))
        assertSame(controller, ReflectionHelpers.getField(host, "controller"))
        assertSame(sink, ReflectionHelpers.getField(host, "sink"))
        assertTrue(CarPlayBackgroundSession.active)
        assertFalse(CarPlayBackgroundSession.isOwner(host))
        assertSame(controller, CarPlayBackgroundSession.snapshot()!!.controller)
        verify(sink).setScreenStreamActiveChangedListener(null)
        verify(controller, never()).close()
        verify(sink, never()).close()
    }

    @Test fun aBackgroundHostAndTheConnectionHubAreNotProjectionExitTargets() {
        ReflectionHelpers.setField(host, "isActivityStarted", false)
        assertFalse(host.exitProjection("background key"))
        ReflectionHelpers.setField(host, "isActivityStarted", true)
        ReflectionHelpers.setField(host, "newUiConnectionHub", true)
        assertFalse(host.exitProjection("connection hub key"))
        assertFalse(host.isFinishing)
        verify(controller, never()).close()
    }

    @Test fun explicitColdBootProjectionDoesNotWaitForAnActiveSessionToLeaveHubMode() {
        CarPlayBackgroundSession.active = false
        val bootIntent = android.content.Intent(host, CarPlayHostActivity::class.java)
            .putExtra(DiPlayLauncher.EXTRA_ENTER_CARPLAY_PROJECTION, true)

        ReflectionHelpers.callInstanceMethod<Unit>(host, "readNewUiLaunchMode",
            ReflectionHelpers.ClassParameter.from(android.content.Intent::class.java, bootIntent))

        assertFalse(ReflectionHelpers.getField(host, "newUiConnectionHub"))
    }

    @Test fun connectionHubIntentStillUsesHubMode() {
        val hubIntent = android.content.Intent(host, CarPlayHostActivity::class.java)
            .putExtra(DiPlayLauncher.EXTRA_NEW_UI_CONNECTION_HUB, true)

        ReflectionHelpers.callInstanceMethod<Unit>(host, "readNewUiLaunchMode",
            ReflectionHelpers.ClassParameter.from(android.content.Intent::class.java, hubIntent))

        assertTrue(ReflectionHelpers.getField(host, "newUiConnectionHub"))
    }

    @Test fun oemFullScreenNavigationExitClosesOnlyTheClusterAndKeepsCarPlayRunning() {
        shadowOf(host.packageManager).installPackage(PackageInfo().apply {
            packageName = "ltd.qisi.navi"
            applicationInfo = ApplicationInfo().apply { packageName = "ltd.qisi.navi" }
        })
        val presentation = mock(ClusterMapPresentation::class.java)
        ReflectionHelpers.setField(host, "clusterPresentation", presentation)
        AirPlayPersistence.saveClusterMapEnabled(host, true)
        ReflectionHelpers.setField(host, "clusterFlyScreenVisible", true)
        assertTrue(CarPlayHostSettingsBridge.closeFlyScreenFromVehicle(host))
        assertFalse(host.flyScreenShown())
        assertFalse(host.isFinishing)
        assertTrue(host.projectionShown())
        assertTrue(CarPlayBackgroundSession.active)
        assertFalse(CarPlayHostSettingsBridge.closeFlyScreenFromVehicle(host))
        verify(presentation).dismiss()
        verify(controller, never()).close()
        verify(sink, never()).close()
        val displayId = org.robolectric.shadows.ShadowDisplayManager.addDisplay("w1920dp-h720dp-mdpi", 5)
        try {
            val display = host.getSystemService(android.hardware.display.DisplayManager::class.java).getDisplay(displayId)
            shadowOf(display).setName("cluster")
            shadowOf(display).setFlags(android.view.Display.FLAG_PRESENTATION)
            assertNotNull(ClusterMapPresentation.findDisplay(host))
            ReflectionHelpers.callInstanceMethod<Unit>(host, "ensureClusterPresentation")
            assertNull(ReflectionHelpers.getField<ClusterMapPresentation?>(host, "clusterPresentation"))
            assertFalse(host.flyScreenShown())
        } finally {
            org.robolectric.shadows.ShadowDisplayManager.removeDisplay(displayId)
        }
    }

    @Test fun aStaleVehicleExitCallbackCannotCloseTheReplacementHost() {
        val replacement = Robolectric.buildActivity(CarPlayHostActivity::class.java).get()
        CarPlayHostSettingsBridge.register(replacement)
        try {
            AirPlayPersistence.saveClusterMapEnabled(host, true)
            ReflectionHelpers.setField(host, "clusterFlyScreenVisible", true)
            assertFalse(CarPlayHostSettingsBridge.closeFlyScreenFromVehicle(host))
            assertTrue(host.flyScreenShown())
            assertFalse(host.isFinishing)
        } finally {
            CarPlayHostSettingsBridge.unregister(replacement)
            CarPlayHostSettingsBridge.register(host)
        }
    }

    @Test fun aDisconnectedScreenIsNotAProjectionExitTarget() {
        `when`(controller.activeAirPlaySessionToken()).thenReturn(null)
        assertFalse(host.exitProjection("disconnected key"))
        assertFalse(host.isFinishing)
    }

    @Test fun androidBackDeliveredToTheClusterStillExitsTheFullProjectionWithoutDisconnecting() {
        val cluster = Robolectric.buildActivity(Hs5ClusterActivity::class.java).get()
        assertTrue(cluster.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK)))
        assertFalse(host.isFinishing)
        assertTrue(cluster.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_BACK)))
        assertTrue(host.isFinishing)
        assertTrue(CarPlayBackgroundSession.active)
        verify(controller, never()).close()
    }

    @Test fun callbacksFromTheRetainedOwnerCannotDisconnectAnAdoptedSession() {
        assertTrue(host.exitProjection("wheel"))
        CarPlayBackgroundSession.store(controller, sink, 1920, 720, Any(),
            mock(CarPlaySessionDisplay::class.java)) { fail("stale callback stopped the adopted session") }
        retainedListener.onSessionEnded(session)
        retainedListener.onTransportError("old error")
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(CarPlayBackgroundSession.active)
        verify(controller, never()).close()
    }

    @Test fun aControlWindowTimeoutDoesNotDisconnectAnOtherwiseLiveMediaSession() {
        assertTrue(host.exitProjection("wheel"))
        retainedStatus(CarPlayStatus.ControlEnded)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(CarPlayBackgroundSession.active)
        assertNotNull(CarPlayBackgroundSession.snapshot())
        verify(controller, never()).close()
    }

    @Test fun callbacksQueuedByTheFinishedActivityCannotStopTheRetainedConnection() {
        val oldListener = ReflectionHelpers.callInstanceMethod<AirPlaySessionListener>(host,
            "createSessionListener", ReflectionHelpers.ClassParameter.from(Int::class.javaPrimitiveType!!, 0))
        Thread { oldListener.onTransportError("queued before UI exit") }.apply { start(); join(3_000) }
        assertTrue(host.exitProjection("wheel"))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(CarPlayBackgroundSession.active)
        assertNotNull(CarPlayBackgroundSession.snapshot())
        verify(controller, never()).close()
    }

    @Test fun aPhoneDisconnectAfterUiExitStopsTheRetainedSessionAndReleasesTheRenderer() {
        val closed = CountDownLatch(1)
        var closingThread: Thread? = null
        doAnswer { closingThread = Thread.currentThread(); closed.countDown(); null }.`when`(sink).close()
        assertTrue(host.exitProjection("wheel"))
        verify(controller, never()).close()
        retainedListener.onSessionEnded(session)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("background renderer was not released", closed.await(3, TimeUnit.SECONDS))
        closingThread!!.join(3_000)
        shadowOf(Looper.getMainLooper()).idle()
        verify(controller).close()
        assertNull(CarPlayBackgroundSession.snapshot())
        assertFalse(CarPlayBackgroundSession.active)
    }
}
