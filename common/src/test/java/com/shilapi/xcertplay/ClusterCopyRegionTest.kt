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
class ClusterCopyRegionTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun clearPreferences() {
        ClusterStyle.entries.forEach {
            context.getSharedPreferences("cluster_copy_region_${it.preferenceValue}", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }

    @Test fun configuringAnInactiveInstrumentDoesNotChangeTheActiveInstrument() {
        val regions = listOf(ClusterCopyRegion(0f, 0f, 1f, 1f),
            ClusterCopyRegion(.1f, .2f, .6f, .8f), ClusterCopyRegion(.4f, .3f, .9f, .7f))
        ClusterStyle.entries.forEachIndexed { index, style ->
            ClusterCopyRegion.save(context, regions[index], style)
        }
        ClusterStyle.entries.forEachIndexed { index, style ->
            AirPlayPersistence.saveClusterStyle(context, style)
            assertEquals(regions[index], ClusterCopyRegion.load(context))
        }
        ClusterCopyRegion.save(context, ClusterCopyRegion(), ClusterStyle.BASIC)
        assertEquals(regions[1], ClusterCopyRegion.load(context, ClusterStyle.SPORT))
        assertEquals(regions[2], ClusterCopyRegion.load(context, ClusterStyle.ORIGINAL))
    }
}
