package com.shilapi.xcertplay

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class AppAppearancePersistenceTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val prefs get() = context.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE)

    @Before fun clearPreferences() = prefs.edit().clear().apply()

    @Test fun freshInstallAndUnknownValuesFollowTheAutomaticTheme() {
        assertEquals(AppAppearance.AUTO, AirPlayPersistence.loadAppAppearance(context))
        prefs.edit().putString("app_appearance", "future-value").apply()
        assertEquals(AppAppearance.AUTO, AirPlayPersistence.loadAppAppearance(context))
    }

    @Test fun allAppearancesRoundTripWithoutChangingOtherPreferences() {
        AirPlayPersistence.saveFps(context, 60)
        AppAppearance.entries.forEach { appearance ->
            AirPlayPersistence.saveAppAppearance(context, appearance)
            assertEquals(appearance, AirPlayPersistence.loadAppAppearance(context))
            assertEquals(60, AirPlayPersistence.loadFps(context))
        }
    }

    @Test fun displaySelectorReplacesLegacyAppearanceAndImmediatelyResolvesTheChosenMode() {
        AirPlayPersistence.saveAppAppearance(context, AppAppearance.DARK)
        com.shilapi.xcertplay.newui.display.NewUiDisplayActions.saveCarPlayNightMode(context, 1)
        assertEquals(AppAppearance.AUTO, AirPlayPersistence.loadAppAppearance(context))
        assertEquals(false, context.resolveAppNightNow(hostNight = true))

        AirPlayPersistence.saveAppAppearance(context, AppAppearance.LIGHT)
        com.shilapi.xcertplay.newui.display.NewUiDisplayActions.saveCarPlayNightMode(context, 2)
        assertEquals(true, context.resolveAppNightNow(hostNight = false))
    }
}
