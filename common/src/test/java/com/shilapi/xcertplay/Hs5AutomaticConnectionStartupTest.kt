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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5AutomaticConnectionStartupTest {
    private val app get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        ShadowBuild.setManufacturer("HONG QI")
        ShadowBuild.setModel("HS5")
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.savePhone(app, "02:00:00:00:00:01", "iPhone")
        AirPlayPersistence.saveMfiTarget(app, MfiTarget.USB_CH341)
        AirPlayPersistence.saveWirelessHotspotMode(app, WirelessHotspotMode.WIFI_P2P)
    }

    @After fun cleanup() { CarPlayBackgroundSession.clear() }

    @Test fun bootRoutesStraightToConnectionAndUsesTheSelectedDefaultTransport() {
        AirPlayPersistence.saveWirelessEnabled(app, false)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        DiPlayBootStartup.launchUi(app)
        val launch = shadowOf(app).nextStartedActivity!!
        assertEquals(CarPlayHostActivity::class.java.name, launch.component?.className)
        assertTrue(launch.getBooleanExtra(EXTRA_BOOT_AUTO_START, false))
        assertTrue(launch.getBooleanExtra(DiPlayLauncher.EXTRA_NEW_UI_CONNECTION_HUB, false))
        assertTrue(AirPlayPersistence.loadWirelessEnabled(app))
        assertNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun configuredColdLauncherStartsConnectionWithoutConstructingTheHomeUi() {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val activity = Robolectric.buildActivity(NewUiActivity::class.java, launcher).create()
        try {
            assertTrue(activity.get().isFinishing)
            assertEquals(CarPlayHostActivity::class.java.name,
                shadowOf(activity.get()).nextStartedActivity?.component?.className)
            assertNull(activity.get().findViewById<android.view.ViewGroup>(android.R.id.content)?.getChildAt(0))
        } finally { activity.destroy() }
    }

    @Test fun bootWithAutoConnectOffOpensHomeWithoutChangingTheTransport() {
        DiPlayPreferences.saveAutoConnect(app, false)
        AirPlayPersistence.saveWirelessEnabled(app, false)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        DiPlayBootStartup.launchUi(app)
        assertEquals(NewUiActivity::class.java.name, shadowOf(app).nextStartedActivity?.component?.className)
        assertFalse(AirPlayPersistence.loadWirelessEnabled(app))
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
