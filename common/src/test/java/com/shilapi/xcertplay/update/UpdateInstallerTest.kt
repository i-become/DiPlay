package com.shilapi.xcertplay.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Looper
import java.time.Duration
import com.shilapi.xcertplay.newui.settings.NewUiUpdateController
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import androidx.activity.ComponentActivity
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class UpdateInstallerTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val app get() = RuntimeEnvironment.getApplication()
    private val receiver = UpdateInstallStatusReceiver()

    @Before fun reset() {
        for (name in listOf("diplay_update_install", "diplay_update_diagnostics")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @Test fun pendingUserActionOpensSystemConfirmationAndKeepsItsResultLink() {
        val id = prepare()
        receiver.onReceive(app, result(id, PackageInstaller.STATUS_PENDING_USER_ACTION)
            .putExtra(Intent.EXTRA_INTENT, Intent("android.content.pm.action.CONFIRM_INSTALL")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)))
        val launch = shadowOf(app).nextStartedActivity
        assertEquals(UpdateInstallActivity::class.java.name, launch.component?.className)
        val bridge = Robolectric.buildActivity(UpdateInstallActivity::class.java, launch).setup()
        val confirmation = shadowOf(bridge.get()).nextStartedActivityForResult
        assertEquals("android.content.pm.action.CONFIRM_INSTALL", confirmation.intent.action)
        assertEquals(0, confirmation.intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        assertTrue(confirmation.intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false))

        // Android 9 also returns this after APPROVAL. Do not abandon the approved session.
        shadowOf(bridge.get()).receiveResult(confirmation.intent, Activity.RESULT_CANCELED, null)
        assertEquals(UpdateInstaller.INSTALLING, UpdateInstaller.result(app)?.status)
        assertNotNull(app.packageManager.packageInstaller.getSessionInfo(id))
        receiver.onReceive(app, result(id, PackageInstaller.STATUS_SUCCESS))
        assertEquals(PackageInstaller.STATUS_SUCCESS, UpdateInstaller.result(app)?.status)
        assertTrue(bridge.get().isFinishing)
        bridge.pause().stop().destroy()
    }

    @Test fun cancellationComesFromTheSystemStatusAndIsDisplayedAfterUiRecreation() {
        val id = prepare()
        receiver.onReceive(app, result(id, PackageInstaller.STATUS_FAILURE_ABORTED)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "User rejected permissions"))
        val screen = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val controller = NewUiUpdateController(screen.get()) {}
        controller.refreshInstallResult()
        assertFalse(controller.uiState.installPreparing)
        assertEquals(screen.get().getString(R.string.update_install_cancelled), controller.uiState.message)
        assertTrue(UpdateDiagnostics.report(app).contains("User rejected permissions"))
        assertNull(shadowOf(app).nextStartedActivity)
        screen.pause().stop().destroy()
    }

    @Test fun missingConfirmationIntentIsAVisibleFailureAndAbandonsTheStagedSession() {
        val id = prepare()
        receiver.onReceive(app, result(id, PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(UpdateInstaller.LAUNCH_FAILED, UpdateInstaller.result(app)?.status)
        assertNull(app.packageManager.packageInstaller.getSessionInfo(id))
        assertTrue(UpdateDiagnostics.report(app).contains("missing system confirmation intent"))
    }

    @Test fun oldSessionResultsDoNotOverrideANewerAttempt() {
        val old = prepare()
        val current = prepare()
        receiver.onReceive(app, result(old, PackageInstaller.STATUS_FAILURE_CONFLICT))
        assertEquals(current, UpdateInstaller.result(app)?.sessionId)
        assertEquals(UpdateInstaller.STAGED, UpdateInstaller.result(app)?.status)
    }

    @Test fun processReplacementCanRecoverSuccessWithoutItsFinalBroadcast() {
        val id = prepare()
        val info = app.packageManager.getPackageInfo(app.packageName, 0).apply {
            versionCode = 1201
            lastUpdateTime = System.currentTimeMillis() + 1_000
        }
        shadowOf(app.packageManager).installPackage(info)
        UpdateInstaller.reconcile(app)
        assertEquals(PackageInstaller.STATUS_SUCCESS, UpdateInstaller.result(app)?.status)
        assertTrue(UpdateDiagnostics.report(app).contains("verified installed version=1201"))
        assertEquals(id, UpdateInstaller.result(app)?.sessionId)
    }

    @Test fun disappearedSessionCannotLeaveTheInstallButtonBusyForever() {
        val id = prepare()
        UpdateInstaller.abandon(app, id)
        UpdateInstaller.reconcile(app)
        assertFalse(UpdateInstaller.result(app)!!.busy)
        assertEquals(UpdateInstaller.LAUNCH_FAILED, UpdateInstaller.result(app)?.status)
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun anInstallerThatExitsWithoutAStatusCannotLeaveTheButtonBusyForever() {
        val id = prepare()
        UpdateInstaller.record(app, id, UpdateInstaller.INSTALLING, "confirmation returned")
        // Robolectric's setSessionActiveState only dispatches callbacks; it does
        // not update the SessionInfo returned by getSessionInfo().
        ReflectionHelpers.setField(app.packageManager.packageInstaller.getSessionInfo(id), "active", false)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(121))
        UpdateInstaller.reconcile(app)
        assertEquals(UpdateInstaller.LAUNCH_FAILED, UpdateInstaller.result(app)?.status)
        assertNull(app.packageManager.packageInstaller.getSessionInfo(id))
        assertTrue(UpdateDiagnostics.report(app).contains("no installation result"))
    }

    @Test
    @LooperMode(LooperMode.Mode.PAUSED)
    fun anActiveSystemInstallIsNotAbandonedWhenTheResultIsDelayed() {
        val id = prepare()
        UpdateInstaller.record(app, id, UpdateInstaller.INSTALLING, "confirmation returned")
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(121))
        UpdateInstaller.reconcile(app)
        assertEquals(UpdateInstaller.INSTALLING, UpdateInstaller.result(app)?.status)
        assertNotNull(app.packageManager.packageInstaller.getSessionInfo(id))
    }

    private fun prepare(): Int {
        val file = File(app.cacheDir, "update-test.apk").apply { writeText("verified bytes") }
        shadowOf(app.packageManager).setPackageArchiveInfo(file.absolutePath,
            PackageInfo().apply { packageName = app.packageName; versionCode = 1201 })
        return UpdateInstaller.prepare(app, file)
    }

    private fun result(id: Int, status: Int) = Intent()
        .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
        .putExtra(PackageInstaller.EXTRA_STATUS, status)
}
