package com.shilapi.xcertplay.newui.settings

import android.content.Context
import android.content.pm.PackageInfo
import android.os.Looper
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateChecksums
import com.shilapi.xcertplay.update.UpdateInstaller
import com.shilapi.xcertplay.update.UpdateRelease
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
        app.getSharedPreferences("diplay_update_availability", Context.MODE_PRIVATE).edit().clear().commit()
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

    @Test fun downloadKeepsTheDisplayedReleaseAndCannotBeReplacedByCachedMetadata() {
        val selected = UpdateRelease("v1.4.4", "DiPlay-1.4.4.apk", "https://example.com/1.4.4.apk", "https://example.com/1.4.4.txt",
            title = "更新标题", description = "更新说明")
        var lookups = 0
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val controller = NewUiUpdateController(activity,
            lookup = { _, _ -> lookups++; selected },
            download = { url, file, progress ->
                requests += url
                file.parentFile!!.mkdirs()
                if (url.endsWith(".txt")) {
                    val digest = UpdateChecksums.sha256Hex("abc".byteInputStream())
                    file.writeText("\uFEFF$digest *./${selected.apkName}")
                } else {
                    file.writeText("abc")
                    shadowOf(app.packageManager).setPackageArchiveInfo(file.absolutePath, PackageInfo().apply {
                        packageName = activity.packageName
                        versionName = "1.4.4"
                        longVersionCode = Long.MAX_VALUE
                    })
                    progress(3, 3)
                }
            }) {}
        controller.checkForUpdates()
        waitUntil { controller.uiState.stage == NewUiUpdateStage.AVAILABLE }
        controller.bindCachedRelease(selected.copy(tagName = "v9.0.0", apkUrl = "https://example.com/wrong.apk"))
        controller.downloadUpdate()
        controller.checkForUpdates()
        controller.downloadUpdate()
        waitUntil { controller.uiState.stage in setOf(NewUiUpdateStage.READY, NewUiUpdateStage.FAILED) }
        assertEquals(controller.uiState.message, NewUiUpdateStage.READY, controller.uiState.stage)
        assertEquals("v1.4.4", controller.uiState.releaseTag)
        assertEquals("更新标题", controller.uiState.releaseTitle)
        assertEquals("更新说明", controller.uiState.releaseDescription)
        assertEquals(1, lookups)
        assertEquals(listOf(selected.checksumsUrl, selected.apkUrl), requests)
    }

    @Test fun missingChecksumEntryStopsBeforeDownloadingApk() {
        val selected = UpdateRelease("v1.4.4", "DiPlay-1.4.4.apk", "https://example.com/app.apk", "https://example.com/sums.txt")
        val requests = java.util.Collections.synchronizedList(mutableListOf<String>())
        val controller = NewUiUpdateController(activity,
            lookup = { _, _ -> selected },
            download = { url, file, _ ->
                requests += url
                file.parentFile!!.mkdirs()
                file.writeText("${"0".repeat(64)}  other.apk")
            }) {}
        controller.checkForUpdates()
        waitUntil { controller.uiState.stage == NewUiUpdateStage.AVAILABLE }
        controller.downloadUpdate()
        waitUntil { controller.uiState.stage == NewUiUpdateStage.FAILED }
        assertTrue(controller.uiState.message!!.contains("缺少"))
        assertEquals(listOf(selected.checksumsUrl), requests)
    }

    @Test fun installedLatestVersionRemainsVisibleWithoutOfferingDownload() {
        val installed = activity.packageManager.getPackageInfo(activity.packageName, 0)
        installed.versionName = "1.4.4"
        shadowOf(app.packageManager).installPackage(installed)
        val published = UpdateRelease("v1.4.4", "DiPlay-1.4.4.apk", "https://example.com/app.apk", "https://example.com/sums.txt",
            title = "版本标题", description = "更新详情")
        val controller = NewUiUpdateController(activity, lookup = { _, _ -> published }) {}
        controller.checkForUpdates()
        waitUntil { controller.uiState.stage != NewUiUpdateStage.CHECKING }
        assertEquals(NewUiUpdateStage.IDLE, controller.uiState.stage)
        assertEquals("v1.4.4", controller.uiState.releaseTag)
        assertEquals("版本标题", controller.uiState.releaseTitle)
        assertEquals("更新详情", controller.uiState.releaseDescription)
        assertEquals(activity.getString(R.string.update_up_to_date), controller.uiState.message)
        controller.bindCachedRelease(published.copy(tagName = "v9.0.0"))
        assertEquals("v1.4.4", controller.uiState.releaseTag)
    }

    @Test fun emptyCatalogDoesNotInventLatestVersionOrReportUpToDate() {
        val controller = NewUiUpdateController(activity, lookup = { _, _ -> null }) {}
        controller.checkForUpdates()
        waitUntil { controller.uiState.stage != NewUiUpdateStage.CHECKING }
        assertNull(controller.uiState.releaseTag)
        assertEquals(activity.getString(R.string.new_ui_update_no_release), controller.uiState.message)
    }

    @Test fun cachedVersionIsDisplayedAndPreservedWhenReturningToPage() {
        val published = UpdateRelease("v1.4.4", "DiPlay-1.4.4.apk", "https://example.com/app.apk", "https://example.com/sums.txt")
        val controller = NewUiUpdateController(activity) {}
        controller.bindCachedRelease(published)
        assertEquals(NewUiUpdateStage.AVAILABLE, controller.uiState.stage)
        assertEquals("v1.4.4", controller.uiState.releaseTag)
        controller.bindCachedRelease(null)
        assertEquals("v1.4.4", controller.uiState.releaseTag)
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
