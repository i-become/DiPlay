package com.shilapi.xcertplay.hud

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5GuidanceBridgeTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun hs5QisiNaviIsPrimaryTarget() {
        install("ltd.qisi.navi")
        assertTrue(Hs5NavPackages.isQisiStack(context))
        assertEquals("ltd.qisi.navi", Hs5NavPackages.resolvedPackage(context))
        assertTrue(Hs5NavPackages.navigationOutputPackages(context).contains("ltd.qisi.navi"))
    }

    private fun install(name: String) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply { packageName = name }
        })
    }
}
