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
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class HongqiHeadUnitTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun hs5ModelIsRecognized() {
        ShadowBuild.setModel("HS5")
        ShadowBuild.setManufacturer("FAW")
        assertTrue(HongqiHeadUnit.isHeadUnit(context))
        assertTrue(HongqiHeadUnit.skipsBackgroundAdb(context))
    }

    @Test fun bydSkipsBackgroundAdbOnlyOnHongqi() {
        ShadowBuild.setFingerprint("BYD-AUTO/IVI/IVI:13/release-keys")
        install("com.byd.carsettings", system = true)
        assertFalse(HongqiHeadUnit.skipsBackgroundAdb(context))
    }

    @Test fun bydFingerprintIsNotHongqi() {
        ShadowBuild.setFingerprint("BYD-AUTO/IVI/IVI:13/release-keys")
        install("com.byd.carsettings", system = true)
        assertFalse(HongqiHeadUnit.isHeadUnit(context))
    }

    private fun install(name: String, system: Boolean) {
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = name
            applicationInfo = ApplicationInfo().apply {
                packageName = name
                flags = if (system) ApplicationInfo.FLAG_SYSTEM else 0
            }
        })
    }
}
