package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent
import com.shilapi.xcertplay.newui.NewUiActivity
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.orchestration.WirelessHotspotMode
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5AutomaticConnectionStartupTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        ReflectionHelpers.setStaticField(HongqiScreenStatus::class.java, "screenOff", false)
        ShadowBuild.setManufacturer("HONG QI")
        ShadowBuild.setModel("HS5")
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.savePhone(app, "02:00:00:00:00:01", "iPhone")
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.WIFI_P2P)
    }

    @After fun cleanup() {
        CarPlayBackgroundSession.clear()
        ConnectionProgressUiState.reset()
        ReflectionHelpers.setStaticField(HongqiScreenStatus::class.java, "screenOff", false)
    }

    private fun launchBootUiAndWait() {
        val finished = CountDownLatch(1)
        DiPlayBootStartup.launchUi(app) { finished.countDown() }
        assertTrue("boot activity launch worker should finish", finished.await(5, TimeUnit.SECONDS))
    }

    @Test fun localAuthenticationPreparationDoesNotBlockUiOrReviveAfterDisconnect() {
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.LOCAL)
        val wasReady = ReflectionHelpers.getField<Boolean>(DiPlayBootstrap, "ready")
        try {
            synchronized(DiPlayBootstrap) {
                ReflectionHelpers.setField(DiPlayBootstrap, "ready", false)
                // The worker cannot enter ensure while this lock is held. The launch request
                // must still return, allowing the caller to draw the home screen immediately.
                assertTrue(DiPlayLauncher.startAutomaticConnection(app, fromBoot = true))
                assertNull(shadowOf(app).nextStartedActivity)
                ConnectionProgressUiState.markDisconnected()
                // Simulate completion without using any local authentication materials.
                ReflectionHelpers.setField(DiPlayBootstrap, "ready", true)
            }
            val pending = ReflectionHelpers.getField<java.util.concurrent.atomic.AtomicBoolean>(DiPlayLauncher, "authenticationPreparing")
            val deadline = System.nanoTime() + 3_000_000_000L
            while (pending.get() && System.nanoTime() < deadline) {
                shadowOf(android.os.Looper.getMainLooper()).idle()
                Thread.sleep(5)
            }
            assertFalse(pending.get())
            assertNull(shadowOf(app).nextStartedActivity)
            assertFalse(CarPlayBackgroundSession.hasSession())
        } finally {
            ReflectionHelpers.setField(DiPlayBootstrap, "ready", wasReady)
        }
    }

    @Test fun bootShowsHomeWithoutCreatingAnActivityBoundConnection() {
        ReflectionHelpers.setField(Hs5BootSession, "preparing", false)
        AirPlayPersistence.saveWirelessEnabled(app, false)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        launchBootUiAndWait()
        val home = shadowOf(app).nextStartedActivity!!
        assertEquals(NewUiActivity::class.java.name, home.component?.className)
        assertNull(shadowOf(app).nextStartedActivity)
        assertFalse(CarPlayBackgroundSession.hasSession())
    }

    @Test fun configuredColdLauncherShowsHomeWhileConnectionIsPending() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activity = Robolectric.buildActivity(NewUiActivity::class.java, launcher).create()
        try {
            assertFalse(activity.get().isFinishing)
            assertNull(shadowOf(activity.get()).nextStartedActivity)
            assertNotNull(activity.get().findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0))
        } finally { activity.destroy() }
    }

    @Test fun bootWithAutoConnectOffOpensHomeWithoutChangingTheTransport() {
        DiPlayPreferences.saveAutoConnect(app, false)
        AirPlayPersistence.saveWirelessEnabled(app, false)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        launchBootUiAndWait()
        assertEquals(NewUiActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(app))
    }

    @Test fun bootUsesTheSavedClusterOnlyModeWithoutStartingCarPlay() {
        AirPlayPersistence.saveClusterOnlyMode(app, true)

        launchBootUiAndWait()

        val launch = shadowOf(app).nextStartedActivity!!
        assertEquals(com.shilapi.xcertplay.newui.ClusterOnlyBootActivity::class.java.name,
            launch.component?.className)
        assertTrue(launch.getBooleanExtra(NewUiActivity.EXTRA_CLUSTER_ONLY_BOOT, false))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun manualHotspotRetainsTheExistingSetupAndHotspotChecks() {
        AirPlayPersistence.saveWirelessEnabled(app, true)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.MANUAL)
        assertFalse(DiPlayLauncher.startAutomaticConnection(app, fromBoot = true))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun repeatedStartupDoesNotReplaceOrStopAnExistingConnection() {
        var stopped = false
        val stop: ((() -> Unit) -> Unit) = { done -> stopped = true; done() }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
        assertFalse(DiPlayLauncher.startAutomaticConnection(app, fromBoot = true))
        assertTrue(CarPlayBackgroundSession.hasSession())
        assertFalse(stopped)
        assertNull(shadowOf(app).nextStartedActivity)
    }
}
