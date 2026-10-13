package com.shilapi.xcertplay

import android.content.Intent
import android.os.Bundle
import com.shilapi.xcertplay.newui.NewUiActivity
import com.shilapi.xcertplay.newui.NewUiDestination
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 33], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class LauncherIntentTest {
    private var controller: ActivityController<NewUiActivity>? = null
    private var stops = 0

    @Before fun setup() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("diplay", 0).edit().clear().commit()
        app.getSharedPreferences("xcertplay_airplay", 0).edit().clear().commit()
        CarPlayBackgroundSession.clear()
        val stop: ((() -> Unit) -> Unit) = { done -> stops++; done() }
        ReflectionHelpers.setField(CarPlayBackgroundSession, "stopAction", stop)
    }

    @After fun cleanup() {
        controller?.destroy()
        CarPlayBackgroundSession.clear()
    }
    private fun launcher() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)

    @Test
    fun onlyAPlainLauncherIntentReturnsToCarPlay() {
        assertTrue(NewUiActivity.isLauncherIntent(launcher()))
        assertFalse(NewUiActivity.isLauncherIntent(launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, "settings")))
        assertFalse(NewUiActivity.isLauncherIntent(launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, null as String?)))
        assertFalse(NewUiActivity.isLauncherIntent(launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, 123)))
        assertFalse(NewUiActivity.isLauncherIntent(Intent(Intent.ACTION_MAIN)))
        assertFalse(NewUiActivity.isLauncherIntent(Intent().putExtra(DiPlayLauncher.EXTRA_PAGE, "connection")))
    }

    @Test fun freshLauncherReturnsToTheExistingHostWithoutStoppingTheSession() {
        controller = Robolectric.buildActivity(NewUiActivity::class.java, launcher()).create()
        val activity = controller!!.get()
        assertTrue(activity.isFinishing)
        assertEquals(CarPlayHostActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className)
        assertTrue(CarPlayBackgroundSession.hasSession())
        assertEquals(0, stops)
    }

    @Test fun launcherDeliveredToExistingSettingsOpensProjectionAndClearsReturnNavigation() {
        controller = Robolectric.buildActivity(NewUiActivity::class.java,
            Intent().putExtra(DiPlayLauncher.EXTRA_PAGE, "settings")).create()
        val activity = controller!!.get()
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onNewIntent",
            ReflectionHelpers.ClassParameter(Intent::class.java, launcher()))
        assertEquals(NewUiDestination.CONNECTION, activity.currentDestinationForTests)
        assertEquals(CarPlayHostActivity::class.java.name,
            shadowOf(activity).nextStartedActivity?.component?.className)
        assertEquals(0, stops)
        ReflectionHelpers.callInstanceMethod<Unit>(activity, "onNewIntent",
            ReflectionHelpers.ClassParameter(Intent::class.java, launcher().putExtra(DiPlayLauncher.EXTRA_PAGE, "settings")))
        assertEquals(NewUiDestination.SETTINGS, activity.currentDestinationForTests)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun recreationRetainsSettingsInsteadOfRedirectingToProjection() {
        controller = Robolectric.buildActivity(NewUiActivity::class.java, launcher())
            .create(Bundle().apply { putString(DiPlayLauncher.EXTRA_PAGE, "settings") })
        val activity = controller!!.get()
        assertFalse(activity.isFinishing)
        assertEquals(NewUiDestination.SETTINGS, activity.currentDestinationForTests)
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(0, stops)
    }

}
