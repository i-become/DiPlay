package com.shilapi.xcertplay

import android.content.Intent
import android.os.Looper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarLifeInstrumentDataTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @After fun cleanup() {
        unsubscribe?.invoke()
        unsubscribe = null
        AirPlayPersistence.saveClusterOnlyMode(context, false)
        AirPlayPersistence.saveClusterOnlyDataSource(context, AirPlayPersistence.ClusterOnlyDataSource.VEHICLE)
        com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false)
    }

    private var unsubscribe: (() -> Unit)? = null

    @Test fun defaultStandaloneDataSourceRemainsVehicleAndSelectionPersists() {
        assertEquals(AirPlayPersistence.ClusterOnlyDataSource.VEHICLE,
            AirPlayPersistence.loadClusterOnlyDataSource(context))
        AirPlayPersistence.saveClusterOnlyDataSource(context, AirPlayPersistence.ClusterOnlyDataSource.CARLIFE)
        assertEquals(AirPlayPersistence.ClusterOnlyDataSource.CARLIFE,
            AirPlayPersistence.loadClusterOnlyDataSource(context))
    }

    @Test fun metadataAndPlaybackBroadcastsMergeAndNavigationUsesOnlyDocumentedFields() {
        var changes = 0
        unsubscribe = CarLifeInstrumentData.subscribe(context) { changes++ }
        context.sendBroadcast(Intent(CarLifeInstrumentData.MEDIA_ACTION)
            .putExtra("sourcePackage", CarLifeInstrumentData.SOURCE_PACKAGE)
            .putExtra("event", "metadata")
            .putExtra("title", "歌曲")
            .putExtra("artist", "歌手")
            .putExtra("album", "专辑")
            .putExtra("source", "百度音乐")
            .putExtra("durationMs", 200_000))
        shadowOf(Looper.getMainLooper()).idle()
        val metadata = CarLifeInstrumentData.snapshot().music.metadata
        assertEquals("歌曲", metadata.title)
        assertEquals("歌手", metadata.artist)
        assertEquals("专辑", metadata.album)
        assertEquals(200_000L, metadata.durationMillis)

        context.sendBroadcast(Intent(CarLifeInstrumentData.MEDIA_ACTION)
            .putExtra("sourcePackage", CarLifeInstrumentData.SOURCE_PACKAGE)
            .putExtra("event", "playback")
            .putExtra("isPlaying", true)
            .putExtra("positionMs", 12_345L))
        shadowOf(Looper.getMainLooper()).idle()
        val playback = CarLifeInstrumentData.snapshot().music.metadata
        assertEquals("歌曲", playback.title)
        assertTrue(playback.playing)
        assertEquals(12_345L, playback.elapsedMillis)

        val iconBytes = byteArrayOf(1, 2, 3)
        context.sendBroadcast(Intent(CarLifeInstrumentData.NAVIGATION_ACTION)
            .putExtra("sourcePackage", CarLifeInstrumentData.SOURCE_PACKAGE)
            .putExtra("event", "update")
            .putExtra("actionCode", 1)
            .putExtra("nextTurn", 77)
            .putExtra("remainDistanceMeters", 850)
            .putExtra("totalDistanceMeters", 12_000)
            .putExtra("roadName", "测试路")
            .putExtra("turnIconBytes", iconBytes))
        shadowOf(Looper.getMainLooper()).idle()
        val guidance = CarLifeInstrumentData.snapshot().guidance
        assertTrue(guidance.navigating)
        assertEquals(850, guidance.distanceMeters)
        assertEquals("测试路", guidance.road)
        assertTrue(guidance.directionIcon!!.contentEquals(iconBytes))
        assertNull("CarLife enum must not be guessed as CarPlay maneuver", guidance.maneuverType)
        assertTrue(guidance.routeSummary.contains("转向 77"))
        assertTrue(changes >= 3)
    }

    @Test fun unexpectedSourcePackageIsIgnored() {
        unsubscribe = CarLifeInstrumentData.subscribe(context) {}
        context.sendBroadcast(Intent(CarLifeInstrumentData.MEDIA_ACTION)
            .putExtra("sourcePackage", "untrusted.sender")
            .putExtra("event", "metadata")
            .putExtra("title", "ignored"))
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(CarLifeInstrumentData.snapshot().music.metadata.title == "ignored")
    }
}
