package com.shilapi.xcertplay

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class StartupDiagnosticSnapshotTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun reset() {
        app.getSharedPreferences("diplay_startup_diagnostics", Context.MODE_PRIVATE).edit().clear().commit()
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
    }

    @Test fun disabledBootIsRecordedWithoutLaunchingOrChangingTheSetting() {
        var launched = false
        val context = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) { launched = true }
        }
        BootReceiver().onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertFalse(launched)
        assertFalse(AirPlayPersistence.loadAutoStartOnBoot(app))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchEnabledAtBoot=false launchResult=disabled"))
    }

    @Test fun successfulLaunchMeansAnActivityRequestAndRetainsTheExistingFlags() {
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=startActivity-returned"))
    }

    @Test fun aSecondBootReplacesOldLaunchEvidence() {
        AirPlayPersistence.saveAutoStartOnBoot(app, true)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=startActivity-returned"))
        AirPlayPersistence.saveAutoStartOnBoot(app, false)
        BootReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("launchResult=disabled failureClass=none"))
    }

    @Test fun unrelatedBroadcastsDoNotCreateOrOverwriteBootEvidence() {
        BootReceiver().onReceive(app, Intent("custom.intent.token=secret"))
        assertTrue(StartupDiagnosticSnapshot.report(app).contains("received=false"))
    }
}
