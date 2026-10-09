package com.shilapi.xcertplay.hud

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class BydOutputSettingsHongqiTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun stockGaodeMakesNavigationSettingsAvailableOnNonByd() {
        install(HongqiAmapAdapter.AMAP_AUTO.packageName)
        assertTrue(BydOutputSettings.available(context))
        assertTrue(HongqiHeadUnit.showsHongqiNavigationBranding(context))
    }

    @Test fun bydHeadUnitDoesNotUseHongqiBrandingWithAmap() {
        install(HongqiAmapAdapter.AMAP_AUTO.packageName)
        install("com.byd.carsettings", system = true)
        assertTrue(BydOutputSettings.available(context))
        assertFalse(HongqiHeadUnit.showsHongqiNavigationBranding(context))
    }

    private fun install(name: String, system: Boolean = false) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        })
    }
}
