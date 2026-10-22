package com.shilapi.xcertplay.newui.settings

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.os.Looper
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateDiagnostics
import com.shilapi.xcertplay.update.UpdateInstallStatusReceiver
import com.shilapi.xcertplay.update.UpdateInstaller
import org.junit.After
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowPackageInstaller
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE, shadows = [NewUiUpdateControllerTest.WaitingPackageSession::class])
@LooperMode(LooperMode.Mode.PAUSED)
class NewUiUpdateControllerTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val app get() = RuntimeEnvironment.getApplication()
    private val activityController by lazy { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    private val activity get() = activityController.get()

    @Before fun clearHistory() {
        for (name in listOf("diplay_update_diagnostics", "diplay_update_install")) {
            app.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        shadowOf(app.packageManager).setCanRequestPackageInstalls(true)
    }

    @After fun cleanup() { activityController.pause().stop().destroy() }

    @Test fun missingDownloadedApkGivesFeedbackAndLeavesAnExportableReason() {
        val controller = NewUiUpdateController(activity) {}
        controller.installUpdate()
        assertEquals(NewUiUpdateStage.FAILED, controller.uiState.stage)
        assertEquals(activity.getString(R.string.update_install_missing), controller.uiState.message)
        assertTrue(UpdateDiagnostics.report(app).contains("reason=apk-missing"))
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun repeatedClicksStageOnlyOneSessionWithoutAFileProvider() {
        val controller = readyController()
        controller.installUpdate()
        assertTrue(controller.uiState.installPreparing)
        controller.installUpdate()
        waitUntil { UpdateInstaller.result(app)?.status == UpdateInstaller.COMMITTED }
        assertEquals(1, app.packageManager.packageInstaller.mySessions.size)
        assertEquals(1, UpdateDiagnostics.report(app).lines().count { it.contains("install-session-staged ") })
        assertTrue(controller.uiState.installPreparing)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun missingPermissionOpensTheActualGrantPageBeforeCreatingASession() {
        val controller = readyController()
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        controller.installUpdate()
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, intent.action)
        assertEquals("package:${app.packageName}", intent.data.toString())
        assertFalse(controller.uiState.installPreparing)
        assertEquals(activity.getString(R.string.update_install_permission), controller.uiState.message)
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
    }

    @Test fun signatureConflictIsShownAsFailureAndAllowsRetryOfTheDownloadedApk() {
        val controller = readyController()
        controller.installUpdate()
        waitUntil { UpdateInstaller.result(app)?.status == UpdateInstaller.COMMITTED }
        val id = UpdateInstaller.result(app)!!.sessionId
        UpdateInstallStatusReceiver().onReceive(app, Intent()
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
            .putExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE_CONFLICT)
            .putExtra(PackageInstaller.EXTRA_STATUS_MESSAGE, "INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match"))
        controller.refreshInstallResult()
        assertFalse(controller.uiState.installPreparing)
        assertEquals(NewUiUpdateStage.READY, controller.uiState.stage)
        assertEquals(activity.getString(R.string.update_install_conflict), controller.uiState.message)
        assertTrue(UpdateDiagnostics.report(app).contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE"))
    }

    @Test fun unreadableOrWrongPackageApkNeverReachesTheSystemInstaller() {
        val controller = readyController(packageName = "another.app")
        controller.installUpdate()
        waitUntil { !controller.uiState.installPreparing }
        assertEquals(NewUiUpdateStage.FAILED, controller.uiState.stage)
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
        assertTrue(UpdateDiagnostics.report(app).contains("APK package does not match"))
    }

    private fun readyController(packageName: String = app.packageName): NewUiUpdateController {
        val file = File(app.cacheDir, "install-test.apk").apply { writeText("verified-test-content") }
        shadowOf(app.packageManager).setPackageArchiveInfo(file.absolutePath,
            PackageInfo().apply { this.packageName = packageName; versionCode = 1201 })
        return NewUiUpdateController(activity) {}.apply {
            ReflectionHelpers.setField(this, "updateFile", file)
            ReflectionHelpers.setField(this, "uiState", NewUiUpdateUiState(stage = NewUiUpdateStage.READY))
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(10)
        }
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue("update worker did not finish", condition())
    }

    /** Robolectric otherwise emits a success broadcast with no PackageInstaller extras. */
    @Implements(PackageInstaller.Session::class)
    class WaitingPackageSession : ShadowPackageInstaller.ShadowSession() {
        @Implementation
        protected override fun commit(statusReceiver: IntentSender) = Unit
    }
}
