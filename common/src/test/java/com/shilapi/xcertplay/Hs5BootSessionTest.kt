package com.shilapi.xcertplay

import android.Manifest
import android.content.Context
import android.os.Looper
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.media.AndroidMediaSink
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.MockedConstruction
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5BootSessionTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var controllers: MockedConstruction<CarPlayController>
    private lateinit var sinks: MockedConstruction<AndroidMediaSink>
    private lateinit var listener: AirPlaySessionListener

    @Before fun setup() {
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        Hs5BootSession.cancelPending()
        ReflectionHelpers.getField<AtomicBoolean>(Hs5BootSession, "scheduled").set(false)
        for (name in listOf("diplay", "xcertplay_airplay", "diplay_carplay_dock", "diplay_carplay_rotation", "diplay_side_panel", "diplay_split_screen")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        ShadowBuild.setManufacturer("HONG QI")
        ShadowBuild.setModel("HS5")
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        DiPlayPreferences.savePhone(app, "02:11:22:33:44:66", "Saved phone")
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.REMOTE)
        AirPlayPersistence.saveRemoteMfiServer(app, "http://127.0.0.1:9000")
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.WIFI_P2P)
        val identity = AirPlayPersistence.loadIdentity(app)
        assertTrue(Hs5BootProfileCache.save(app, Hs5BootProfileCache.Profile(Hs5BootProfileCache.stamp(app),
            AirPlayConfig("DiPlay", DiPlayBootstrap.deviceId(identity), "", "1", AirPlayDisplayConfig(1920, 720)),
            CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720), 1920, 720)))
        sinks = mockConstruction(AndroidMediaSink::class.java)
        controllers = mockConstruction(CarPlayController::class.java) { controller, construction ->
            listener = construction.arguments()[5] as AirPlaySessionListener
            `when`(controller.nowPlayingSnapshot()).thenReturn(CarPlayNowPlaying())
            `when`(controller.awaitClosed(anyLong())).thenReturn(true)
        }
    }

    @After fun cleanup() {
        ReflectionHelpers.setField(HongqiScreenStatus, "screenOff", false)
        ReflectionHelpers.setField(HongqiScreenStatus, "connection", null)
        ReflectionHelpers.setField(HongqiScreenStatus, "raw", null)
        Hs5BootSession.cancelPending()
        CarPlayMediaKeys.detach(CarPlayBackgroundSession.snapshot()?.controller)
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        shadowOf(Looper.getMainLooper()).idle()
        controllers.close()
        sinks.close()
    }

    @Test fun bootStartsTheFullControllerWithNoActivityAndKeepsItForUiAdoption() {
        Hs5BootSession.schedule(app)
        await { CarPlayBackgroundSession.hasSession() }
        val snapshot = CarPlayBackgroundSession.snapshot()!!
        assertTrue(CarPlayBackgroundSession.isOwner(Hs5BootSession))
        assertEquals(1920, snapshot.display.width)
        assertEquals(720, snapshot.display.height)
        verify(snapshot.controller).start()
        assertNull(shadowOf(app).nextStartedActivity)
        listener.onSessionActive(mock(AirPlaySession::class.java))
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(CarPlayBackgroundSession.active)
        assertEquals(CarPlayConnectionProgress.STEP_COUNT, ConnectionProgressUiState.litCount())
    }

    @Test fun disconnectBeforeUiAdoptionReleasesTheOwnerAndAllowsTheNextConnection() {
        Hs5BootSession.schedule(app)
        await { CarPlayBackgroundSession.snapshot() != null }
        val snapshot = CarPlayBackgroundSession.snapshot()!!
        val session = mock(AirPlaySession::class.java)
        listener.onSessionActive(session)
        shadowOf(Looper.getMainLooper()).idle()
        listener.onSessionEnded(session)
        await { !CarPlayBackgroundSession.hasSession() }
        assertNull(CarPlayBackgroundSession.snapshot())
        assertFalse(CarPlayBackgroundSession.isOwner(Hs5BootSession))
        assertTrue(ConnectionProgressUiState.disconnected())
        assertFalse(DiPlayLauncher.startAutomaticConnection(app, fromBoot = true))
        verify(snapshot.controller).close()
        verify(snapshot.sink).close()
    }

    @Test fun delayedBootActivityAfterPhoneDisconnectReturnsHomeWithoutStartingAnotherController() {
        ConnectionProgressUiState.markDisconnected()
        val activity = Robolectric.buildActivity(CarPlayHostActivity::class.java).create().get()
        assertTrue(activity.isFinishing)
        assertEquals("home", shadowOf(activity).nextStartedActivity.getStringExtra(DiPlayLauncher.EXTRA_PAGE))
        assertTrue(controllers.constructed().isEmpty())
        assertTrue(ConnectionProgressUiState.disconnected())
    }

    @Test fun manualDisconnectCancelsAnInFlightBootPreparation() {
        Hs5BootSession.schedule(app)
        CarPlayBackgroundSession.stop()
        await { ReflectionHelpers.getField<AtomicBoolean>(Hs5BootSession, "scheduled").get() }
        assertFalse(CarPlayBackgroundSession.hasSession())
        assertTrue(controllers.constructed().isEmpty())
    }

    @Test fun vendorScreenOffStopsTheSessionAndRejectsStaleOrUnknownWakeups() {
        Hs5BootSession.schedule(app)
        await { CarPlayBackgroundSession.snapshot() != null }
        val snapshot = CarPlayBackgroundSession.snapshot()!!
        val type = Class.forName("com.shilapi.xcertplay.HongqiScreenStatus\$Connection")
        val source = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        ReflectionHelpers.setField(HongqiScreenStatus, "connection", source)
        val accept = HongqiScreenStatus::class.java.getDeclaredMethod("accept", type, Int::class.javaPrimitiveType).apply { isAccessible = true }
        accept.invoke(HongqiScreenStatus, source, 2)
        await { !CarPlayBackgroundSession.hasSession() }
        assertTrue(HongqiScreenStatus.screenOff)
        assertTrue(ConnectionProgressUiState.disconnected())
        verify(snapshot.controller).close()
        verify(snapshot.sink).close()
        accept.invoke(HongqiScreenStatus, source, 99)
        assertTrue(HongqiScreenStatus.screenOff)
        val stale = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        accept.invoke(HongqiScreenStatus, stale, 1)
        assertTrue(HongqiScreenStatus.screenOff)
        accept.invoke(HongqiScreenStatus, source, 6)
        assertFalse(HongqiScreenStatus.screenOff)
        assertTrue(ConnectionProgressUiState.disconnected())
        assertFalse(DiPlayLauncher.startAutomaticConnection(app, fromBoot = true))
    }

    @Test fun disabledBootOrMissingDisplayProfileDoesNotInventDimensionsOrStartAConnection() {
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        Hs5BootSession.schedule(app)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(controllers.constructed().isEmpty())
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        File(app.filesDir, "hs5-boot-display-v1").delete()
        Hs5BootSession.schedule(app)
        assertFalse(CarPlayBackgroundSession.hasSession())
        assertTrue(controllers.constructed().isEmpty())
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("Boot operation did not complete", condition())
    }
}
