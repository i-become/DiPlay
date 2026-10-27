package com.shilapi.xcertplay.newui.settings

import android.content.Context
import android.os.Looper
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateChecksums
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
import org.robolectric.util.ReflectionHelpers
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class NewUiUpdateControllerTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val activityController by lazy { Robolectric.buildActivity(ComponentActivity::class.java).setup() }
    private val activity get() = activityController.get()

    @Before fun clearHistory() {
        DiagnosticLogging.setEnabled(false)
        app.getSharedPreferences(UpdateInstaller.PREFERENCES, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun cleanup() { activityController.pause().stop().destroy() }

    @Test fun missingDownloadedApkGivesFeedbackWithoutOpeningAnInstaller() {
        val controller = NewUiUpdateController(activity) {}
        controller.installUpdate()
        assertEquals(NewUiUpdateStage.FAILED, controller.uiState.stage)
        assertEquals(activity.getString(R.string.update_install_missing), controller.uiState.message)
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun aFileWithoutItsVerifiedDownloadDigestMustBeDownloadedAgain() {
        val controller = readyController(withDigest = false)
        controller.installUpdate()
        assertEquals(activity.getString(R.string.update_install_missing), controller.uiState.message)
        assertNull(UpdateInstaller.result(app))
    }

    @Test fun unavailableSotaDoesNotAskForUnknownSourcesOrFallBackToStandardInstall() {
        val controller = readyController()
        shadowOf(app.packageManager).setCanRequestPackageInstalls(false)
        controller.installUpdate()
        assertTrue(controller.uiState.installPreparing)
        controller.installUpdate()
        waitUntil { !controller.uiState.installPreparing }
        assertEquals(NewUiUpdateStage.READY, controller.uiState.stage)
        assertEquals(activity.getString(R.string.update_sota_failure, "SOTA_UNSUPPORTED_DEVICE"), controller.uiState.message)
        assertTrue(app.packageManager.packageInstaller.mySessions.isEmpty())
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test fun resultIsVisibleAfterTheSettingsScreenIsRecreated() {
        app.getSharedPreferences(UpdateInstaller.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("request", "saved-request").putString("status", "TIMED_OUT")
            .putLong("revision", 1).putString("reason", "SOTA_RESULT_TIMEOUT").commit()
        val controller = NewUiUpdateController(activity) {}
        controller.refreshInstallResult()
        assertFalse(controller.uiState.installPreparing)
        assertEquals(activity.getString(R.string.update_sota_timeout), controller.uiState.message)
    }

    @Test fun onlyVersionConfirmedSuccessShowsInstalled() {
        app.getSharedPreferences(UpdateInstaller.PREFERENCES, Context.MODE_PRIVATE).edit()
            .putString("request", "saved-request").putString("status", "SUCCEEDED")
            .putLong("revision", 1).putString("reason", "VERSION_CONFIRMED").commit()
        val controller = NewUiUpdateController(activity) {}
        controller.refreshInstallResult()
        assertEquals(NewUiUpdateStage.IDLE, controller.uiState.stage)
        assertEquals(activity.getString(R.string.update_install_success), controller.uiState.message)
    }

    private fun readyController(withDigest: Boolean = true): NewUiUpdateController {
        val file = File(app.cacheDir, "install-test.apk").apply { writeText("verified-test-content") }
        return NewUiUpdateController(activity) {}.apply {
            ReflectionHelpers.setField(this, "updateFile", file)
            if (withDigest) ReflectionHelpers.setField(this, "verifiedDigest", UpdateChecksums.sha256Hex(file))
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
}
