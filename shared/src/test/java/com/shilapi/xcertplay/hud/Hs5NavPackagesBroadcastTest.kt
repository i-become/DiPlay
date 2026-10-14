package com.shilapi.xcertplay.hud

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [30], manifest = Config.NONE)
class Hs5NavPackagesBroadcastTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun resolvesGaodeFromAutonaviBroadcastReceiver() {
        val receiver = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply { packageName = "com.vendor.amapauto" }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent("AUTONAVI_STANDARD_BROADCAST_SEND"),
            receiver,
        )
        assertEquals("com.vendor.amapauto", Hs5NavPackages.resolvedPackage(context))
    }
}
