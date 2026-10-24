package com.shilapi.xcertplay

import android.content.Context
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIcon
import com.shilapi.xcertplay.airplay.AirPlayInsets
import com.shilapi.xcertplay.airplay.AirPlayViewArea
import java.io.File
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5BootProfileCacheTest {
    private val app get() = RuntimeEnvironment.getApplication() as Context

    @Before fun clean() {
        for (name in listOf("xcertplay_airplay", "diplay_carplay_dock", "diplay_carplay_rotation", "diplay_side_panel", "diplay_split_screen")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        File(app.filesDir, "hs5-boot-display-v1").delete()
    }

    @Test fun roundTripPreservesNegotiatedCanvasPhysicalSizeClusterAndInsets() {
        val config = AirPlayConfig("DiPlay", "02:11:22:33:44:66", "", "1",
            AirPlayDisplayConfig(1920, 720, 260, 98, fps = 30, safeArea = AirPlayInsets(bottom = 20),
                viewAreas = listOf(AirPlayViewArea(1920, 720), AirPlayViewArea(1280, 720)), initialViewArea = 0),
            cluster = AirPlayDisplayConfig(1280, 480), hevc = true, microphone = true,
            icons = listOf(AirPlayIcon(1, 1, byteArrayOf(1, 2, 3))))
        val profile = Hs5BootProfileCache.Profile(Hs5BootProfileCache.stamp(app), config,
            CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720), 1920, 720)
        assertTrue(Hs5BootProfileCache.save(app, profile))
        val restored = Hs5BootProfileCache.load(app)!!
        assertEquals(config.main, restored.config.main)
        assertEquals(config.cluster, restored.config.cluster)
        assertArrayEquals(config.icons.single().data, restored.config.icons.single().data)
        assertEquals(profile.display, restored.display)
        assertEquals(1920, restored.width)
    }

    @Test fun changingDisplayOrDockSettingsInvalidatesTheOldProfile() {
        save()
        AirPlayPersistence.saveDisplayScalePercent(app, 80)
        assertNull(Hs5BootProfileCache.load(app))
        save()
        app.getSharedPreferences("diplay_carplay_dock", Context.MODE_PRIVATE).edit().putString("dock", "BOTTOM").commit()
        assertNull(Hs5BootProfileCache.load(app))
    }

    @Test fun cachedViewAreasKeepDockAndPanelMappingsButRestartAtTheAdvertisedFullScreen() {
        val areas = CarPlayViewAreas.build(1920, 720, CarPlayDock.BOTTOM, 0.5f to 1f, sidePanel = true)!!
        val initial = areas.current
        val panel = areas.sidePanel()!!
        areas.use(panel)
        val startup = areas.startupCopy(initial)
        val config = AirPlayConfig("DiPlay", "id", "", "1", AirPlayDisplayConfig(1920, 720,
            viewAreas = areas.areas, initialViewArea = initial))
        assertTrue(Hs5BootProfileCache.save(app, Hs5BootProfileCache.Profile(Hs5BootProfileCache.stamp(app), config,
            CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720, startup), 1920, 720)))
        val restored = Hs5BootProfileCache.load(app)!!.display.viewAreas!!
        assertEquals(initial, restored.current)
        assertEquals(CarPlayViewAreas.Kind.FULL_SCREEN, restored.kindOf(restored.current))
        assertEquals(panel, restored.sidePanel())
        assertEquals(startup.withDock(CarPlayDock.DRIVER_SIDE.edge!!), restored.withDock(CarPlayDock.DRIVER_SIDE.edge!!))
        assertEquals(startup.indexFor(960, 720, true), restored.indexFor(960, 720, true))
        assertEquals(areas.areas, restored.areas)
        assertEquals(panel, areas.current)
    }

    @Test fun logsPairingAndRememberedMeasurementsDoNotInvalidateTheDisplayOffer() {
        save()
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit()
            .putBoolean("session_log_recording_enabled", true).putString("pairing.test", "changed")
            .putString("lockdown_host_id", "changed").putInt("startup_display_width", 1920).commit()
        assertNotNull(Hs5BootProfileCache.load(app))
    }

    @Test fun missingCorruptOrMismatchedSizeSafelyFallsBackToTheUi() {
        assertNull(Hs5BootProfileCache.load(app))
        assertTrue(Hs5BootProfileCache.save(app, Hs5BootProfileCache.Profile(Hs5BootProfileCache.stamp(app),
            AirPlayConfig("DiPlay", "id", "", "1", AirPlayDisplayConfig(1920, 720)),
            CarPlaySessionDisplay(960, 720, 0, true, true, 1920, 720), 1920, 720)))
        assertNull(Hs5BootProfileCache.load(app))
        File(app.filesDir, "hs5-boot-display-v1").writeBytes(byteArrayOf(0, 1, 2))
        assertNull(Hs5BootProfileCache.load(app))
    }

    private fun save() {
        assertTrue(Hs5BootProfileCache.save(app, Hs5BootProfileCache.Profile(Hs5BootProfileCache.stamp(app),
            AirPlayConfig("DiPlay", "id", "", "1", AirPlayDisplayConfig(1920, 720)),
            CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720), 1920, 720)))
    }
}
