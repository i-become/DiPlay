package com.shilapi.xcertplay

import android.database.MatrixCursor
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.shilapi.xcertplay.media.CarPlayNowPlaying
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class Hs5SystemMediaTest {
    private val context get() = RuntimeEnvironment.getApplication()
    @After fun cleanup() { AirPlayPersistence.saveClusterOnlyMode(context, false) }

    @Test fun readsWideColumnsAndPreservesTheFirmwareAlbumTypo() {
        val result = MatrixCursor(arrayOf("media_name", "media_artist", "media_ablum", "media_source", "media_playstate"))
            .apply { addRow(arrayOf("歌曲", "歌手", "专辑", "蓝牙", "playing")) }
            .use(Hs5SystemMediaColumns::read)
        assertEquals(CarPlayNowPlaying(title = "歌曲", artist = "歌手", album = "专辑", sourceApp = "蓝牙", playing = true), result.metadata)
        assertFalse(result.status.contains("歌曲"))
    }

    @Test fun readsOnlyKnownMediaKeysFromDomainValueRows() {
        val result = MatrixCursor(arrayOf("_id", "domainKey", "domainValue")).apply {
            addRow(arrayOf<Any>(1, "media_name", "歌名"))
            addRow(arrayOf<Any>(2, "media_ablum", "专辑"))
            addRow(arrayOf<Any>(3, "unknown_navigation_field", "123"))
            addRow(arrayOf<Any>(4, "media_playstate", "paused"))
        }.use(Hs5SystemMediaColumns::read)
        assertEquals("歌名", result.metadata.title)
        assertEquals("专辑", result.metadata.album)
        assertFalse(result.metadata.playing)
        assertTrue(result.status.startsWith("read:3-fields"))
        assertTrue(result.status.contains("keys=media_ablum,media_name,media_playstate"))
        assertFalse(result.status.contains("歌名"))
    }

    @Test fun readsDocumentedDisplayZeroKeysAndFormattedNavigationWithoutInventingTurnData() {
        val result = MatrixCursor(arrayOf("domainKey", "domainValue")).apply {
            addRow(arrayOf("media_name0", "系统歌曲"))
            addRow(arrayOf("media_playstate0", "true"))
            addRow(arrayOf("navi_target0", "人民广场"))
            addRow(arrayOf("navi_info_distance0", "3.2公里"))
            addRow(arrayOf("navi_info_time0", "8分钟"))
            addRow(arrayOf("navi_arrive_time0", "14:30"))
        }.use(Hs5SystemMediaColumns::read)
        assertEquals("系统歌曲", result.metadata.title)
        assertTrue(result.metadata.playing)
        assertTrue(result.guidance.connected)
        assertTrue(result.guidance.navigating)
        assertEquals("目的地 人民广场 · 距离 3.2公里 · 用时 8分钟 · 到达 14:30", result.guidance.routeSummary)
        assertNull(result.guidance.maneuverType)
        assertTrue(result.guidance.lanes.isEmpty())
    }

    @Test fun translatesKnownMediaSourceEnumsAndPreservesUnknownOnes() {
        val known = MatrixCursor(arrayOf("media_source0")).apply { addRow(arrayOf("4")) }
            .use(Hs5SystemMediaColumns::read)
        assertEquals("蓝牙", known.metadata.sourceApp)
        val unknown = MatrixCursor(arrayOf("media_source")).apply { addRow(arrayOf("987")) }
            .use(Hs5SystemMediaColumns::read)
        assertEquals("987", unknown.metadata.sourceApp)
    }

    @Test fun decodesDemoDataSyncValuesIntoStandaloneMusicAndRouteSummary() {
        val result = Hs5SystemMediaColumns.readDataSync(mapOf(
            "media_name" to "不灭莲（DJ默涵版）",
            "media_artist" to "定海神针&喵叽喵姬",
            "media_ablum" to "不灭莲（DJ默涵版）",
            "media_source" to "7",
            "media_playstate" to "false",
            "navigation_status" to "1",
            "navi_current_state" to "2",
            "navi_start" to "{\"name\":\"当前位置\"}",
            "navi_target" to "{\"name\":\"万家丽中路99号(万家丽广场地铁站2号线2号口步行50米)\"}",
            "navi_info_distance" to "10.8公里",
            "navi_info_time" to "13分钟",
            "navi_arrive_time" to "18:01",
        ))
        assertEquals("不灭莲（DJ默涵版）", result.metadata.title)
        assertEquals("定海神针&喵叽喵姬", result.metadata.artist)
        assertEquals("酷我", result.metadata.sourceApp)
        assertFalse(result.metadata.playing)
        assertTrue(result.guidance.connected)
        assertTrue(result.guidance.navigating)
        assertTrue(result.guidance.routeSummary.contains("万家丽中路99号"))
        assertTrue(result.guidance.routeSummary.contains("10.8公里"))
        assertTrue(result.guidance.routeSummary.contains("13分钟"))
        assertTrue(result.guidance.routeSummary.contains("18:01"))
    }

    @Test fun longDataSyncDestinationIsShortenedBeforeDistanceAndTimeAreAdded() {
        val result = Hs5SystemMediaColumns.readDataSync(mapOf(
            "navigation_status" to "1",
            "navi_target" to "{\"name\":\"万家丽中路99号万家丽国际购物广场地铁站2号线2号口步行50米\"}",
            "navi_info_distance" to "10.8公里",
            "navi_info_time" to "13分钟",
            "navi_arrive_time" to "18:01",
        ))
        assertTrue(result.guidance.routeSummary.contains("目的地 万家丽中路99号万家…"))
        assertTrue(result.guidance.routeSummary.contains("距离 10.8公里"))
        assertTrue(result.guidance.routeSummary.contains("用时 13分钟"))
        assertTrue(result.guidance.routeSummary.contains("到达 18:01"))
    }

    @Test fun conflictingSourcesAndUnknownSchemaDoNotProduceArbitraryMusic() {
        val conflict = MatrixCursor(arrayOf("key", "value")).apply {
            addRow(arrayOf("media_name", "A")); addRow(arrayOf("media_name", "B"))
        }.use(Hs5SystemMediaColumns::read)
        assertEquals("ambiguous-media-rows", conflict.status)
        assertEquals(CarPlayNowPlaying(), conflict.metadata)
        val unknown = MatrixCursor(arrayOf("opaque", "payload")).apply {
            addRow(arrayOf("media_name", "song"))
        }.use(Hs5SystemMediaColumns::read)
        assertTrue(unknown.status.startsWith("unsupported-columns:"))
        assertNull(unknown.metadata.title)
    }

    @Test fun numericPlaybackStateDoesNotInventAnEnumMapping() {
        val result = MatrixCursor(arrayOf("media_playstate")).apply { addRow(arrayOf(1)) }
            .use(Hs5SystemMediaColumns::read)
        assertFalse(result.metadata.playing)
    }

    @Test fun excessiveRowsAreRejectedInsteadOfBlockingAnInstrumentIndefinitely() {
        val result = MatrixCursor(arrayOf("key", "value")).apply {
            repeat(513) { addRow(arrayOf("other", "ignored")) }
        }.use(Hs5SystemMediaColumns::read)
        assertEquals("row-limit", result.status)
    }

    @Test fun standaloneMusicDoesNotRequireAFakePhoneConnectionAndCarPlayStillDoes() {
        val music = ClusterDashboardMusic(CarPlayNowPlaying(title = "系统音乐"))
        assertEquals(music, ClusterDashboardState(music = music, standalone = true).current().music)
        assertEquals(ClusterDashboardMusic(), ClusterDashboardState(music = music).current().music)
        AirPlayPersistence.saveClusterOnlyMode(context, true)
        assertFalse(ClusterInstrumentData.guidance(context).connected)
        assertFalse(ClusterInstrumentData.guidance(context).navigating)
    }

    @Test fun providerRunsOffMainOnlyInStandaloneAndLastSubscriberClearsTheCache() {
        val queries = AtomicInteger()
        var queriedOffMain = false
        val changed = CountDownLatch(1)
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                queries.incrementAndGet()
                queriedOffMain = android.os.Looper.myLooper() != android.os.Looper.getMainLooper()
                return MatrixCursor(arrayOf("media_name")).apply { addRow(arrayOf("原车音乐")) }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = error("must not write")
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = error("must not write")
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = error("must not write")
        }
        ShadowContentResolver.registerProviderInternal(Hs5SystemMedia.uri.authority!!, provider)
        AirPlayPersistence.saveClusterOnlyMode(context, false)
        val stopCarPlay = ClusterInstrumentData.subscribe(context) {}
        stopCarPlay()
        assertEquals(0, queries.get())
        AirPlayPersistence.saveClusterOnlyMode(context, true)
        val stop = ClusterInstrumentData.subscribe(context) { changed.countDown() }
        try {
            assertTrue("provider state was not delivered", changed.await(5, TimeUnit.SECONDS))
            assertTrue(queriedOffMain)
            assertEquals("原车音乐", ClusterInstrumentData.music(context).metadata.title)
        } finally { stop() }
        assertEquals(ClusterDashboardMusic(), Hs5SystemMedia.snapshot())
    }
}
