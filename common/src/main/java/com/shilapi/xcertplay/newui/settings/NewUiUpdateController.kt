package com.shilapi.xcertplay.newui.settings

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateApkExport
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateCatalog
import com.shilapi.xcertplay.update.UpdateChecksums
import com.shilapi.xcertplay.update.UpdateClient
import com.shilapi.xcertplay.update.UpdateDiagnostics
import com.shilapi.xcertplay.update.UpdateRelease
import com.shilapi.xcertplay.update.UpdateReleaseLookup
import com.shilapi.xcertplay.update.UpdateInstaller
import java.io.File
import java.io.IOException

internal class NewUiUpdateController(
    private val activity: ComponentActivity,
    private val onStateChanged: () -> Unit,
) {
    var uiState = NewUiUpdateUiState()
        private set

    private var generation = 0
    private var release: UpdateRelease? = null
    private var updateFile: File? = null
    private var installResultRevision = 0L

    fun checkForUpdates() {
        if (uiState.installPreparing) return
        diagnostic("check-clicked")
        uiState = uiState.copy(stage = NewUiUpdateStage.CHECKING, message = null, progress = null)
        onStateChanged()
        val gen = ++generation
        Thread({
            val outcome = runCatching { latestRelease() }
            activity.runOnUiThread {
                if (gen != generation || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                outcome.fold(
                    { found ->
                        UpdateAvailability.recordAttempt(activity, System.currentTimeMillis())
                        diagnostic("check-completed updateAvailable=${found != null}")
                        if (found == null) {
                            UpdateAvailability.clear(activity)
                            uiState = NewUiUpdateUiState(
                                stage = NewUiUpdateStage.IDLE,
                                message = activity.getString(R.string.update_up_to_date),
                            )
                            release = null
                        } else {
                            UpdateAvailability.save(activity, found)
                            release = found
                            uiState = NewUiUpdateUiState(
                                stage = NewUiUpdateStage.AVAILABLE,
                                releaseTag = found.tagName,
                            )
                        }
                        onStateChanged()
                    },
                    { failure ->
                        diagnostic("check-failed failureClass=${failure.javaClass.simpleName}")
                        DiagnosticLogging.logcat { Log.w(TAG, "update check failed", failure) }
                        uiState = NewUiUpdateUiState(
                            stage = NewUiUpdateStage.FAILED,
                            message = failure.message ?: activity.getString(R.string.update_failed),
                        )
                        onStateChanged()
                    },
                )
            }
        }, "new-ui-update-check").start()
    }

    fun bindCachedRelease(cached: UpdateRelease?) {
        if (cached != null) release = cached
    }

    fun downloadUpdate() {
        if (uiState.installPreparing) return
        if (release == null) {
            release = UpdateAvailability.available(activity, installedVersion())
        }
        if (release == null) {
            diagnostic("download-unavailable reason=no-release")
            return
        }
        diagnostic("download-started")
        uiState = uiState.copy(
            stage = NewUiUpdateStage.DOWNLOADING,
            message = null,
            progress = null,
            savedPath = null,
        )
        onStateChanged()
        val gen = ++generation
        Thread({
            val directory = File(activity.cacheDir, "update/${java.util.UUID.randomUUID()}")
            val outcome = runCatching {
                val latest = latestRelease() ?: throw IOException(activity.getString(R.string.update_up_to_date))
                val checksumsFile = File(directory, UpdateCatalog.CHECKSUMS_FILE)
                UpdateClient.download(latest.checksumsUrl, checksumsFile) { _, _ -> }
                val apkFile = File(directory, latest.apkName)
                UpdateClient.download(latest.apkUrl, apkFile) { written, total ->
                    val percent = total?.takeIf { it > 0 }?.let { (written * 100 / it).toInt() } ?: return@download
                    if (percent != uiState.progress) {
                        activity.runOnUiThread {
                            if (gen == generation && !activity.isFinishing && !activity.isDestroyed) {
                                uiState = uiState.copy(progress = percent)
                                onStateChanged()
                            }
                        }
                    }
                }
                activity.runOnUiThread {
                    if (gen == generation) {
                        uiState = uiState.copy(stage = NewUiUpdateStage.VERIFYING)
                        onStateChanged()
                    }
                }
                diagnostic("download-completed bytes=${apkFile.length()} verification-started")
                val expected = UpdateChecksums.parse(checksumsFile.readText())
                val actual = UpdateChecksums.sha256Hex(apkFile)
                if (!UpdateChecksums.matches(expected, latest.apkName, actual)) {
                    diagnostic("verification-failed reason=checksum-mismatch")
                    throw IOException("Checksum mismatch for ${latest.apkName}")
                }
                release = latest
                diagnostic("verification-passed")
                apkFile
            }.onFailure { directory.deleteRecursively() }
            val saved = outcome.getOrNull()?.let { UpdateApkExport.copy(activity.applicationContext, it) }
            if (outcome.isSuccess) diagnostic("apk-export completed=${saved != null} publicUri=${saved?.uri != null}")
            activity.runOnUiThread {
                if (gen != generation || activity.isFinishing || activity.isDestroyed) {
                    directory.deleteRecursively()
                    return@runOnUiThread
                }
                outcome.fold(
                    { file ->
                        diagnostic("download-ready bytes=${file.length()}")
                        updateFile = file
                        uiState = NewUiUpdateUiState(
                            stage = NewUiUpdateStage.READY,
                            releaseTag = release?.tagName,
                            savedPath = saved?.path,
                        )
                        onStateChanged()
                    },
                    { failure ->
                        diagnostic("download-failed failureClass=${failure.javaClass.simpleName}")
                        DiagnosticLogging.logcat { Log.w(TAG, "update download failed", failure) }
                        uiState = NewUiUpdateUiState(
                            stage = NewUiUpdateStage.FAILED,
                            message = failure.message ?: activity.getString(R.string.update_failed),
                        )
                        onStateChanged()
                    },
                )
            }
        }, "new-ui-update-download").start()
    }

    fun installUpdate() {
        diagnostic("install-clicked stage=${uiState.stage} preparing=${uiState.installPreparing} " +
            "apkPresent=${updateFile?.isFile == true}")
        if (uiState.installPreparing) return
        val file = updateFile
        if (file == null || !file.isFile || file.length() <= 0L) {
            diagnostic("install-failed reason=apk-missing")
            uiState = uiState.copy(stage = NewUiUpdateStage.FAILED, message = activity.getString(R.string.update_install_missing))
            onStateChanged()
            return
        }
        val allowed = Build.VERSION.SDK_INT < 26 || activity.packageManager.canRequestPackageInstalls()
        diagnostic("install-permission canRequest=$allowed")
        if (!allowed) {
            uiState = uiState.copy(message = activity.getString(R.string.update_install_permission))
            onStateChanged()
            runCatching {
                activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
            }.onFailure { diagnostic("install-permission-open-failed failureClass=${it.javaClass.simpleName}") }
            return
        }
        uiState = uiState.copy(installPreparing = true, message = activity.getString(R.string.update_install_preparing))
        installResultRevision = UpdateInstaller.result(activity)?.revision ?: 0L
        onStateChanged()
        val gen = generation
        val started = SystemClock.elapsedRealtime()
        Thread({
            val prepared = runCatching { UpdateInstaller.prepare(activity.applicationContext, file) }
            diagnostic("install-prepared success=${prepared.isSuccess} elapsedMs=${SystemClock.elapsedRealtime() - started}")
            activity.runOnUiThread {
                if (gen == generation && !activity.isFinishing && !activity.isDestroyed) {
                    prepared.fold(
                        { id ->
                            runCatching { UpdateInstaller.commit(activity, id) }
                                .onFailure { diagnostic("install-commit-failed failureClass=${it.javaClass.simpleName}") }
                            refreshInstallResult()
                        },
                        { failure ->
                            diagnostic("install-prepare-failed failureClass=${failure.javaClass.simpleName} detail=${failure.message}")
                            uiState = uiState.copy(stage = NewUiUpdateStage.FAILED, installPreparing = false,
                                message = activity.getString(R.string.update_install_failure))
                            onStateChanged()
                        },
                    )
                } else {
                    prepared.getOrNull()?.let { id ->
                        UpdateInstaller.abandon(activity, id)
                        UpdateInstaller.record(activity, id, UpdateInstaller.LAUNCH_FAILED, "UI closed before commit")
                    }
                    diagnostic("install-abandoned generationChanged=${gen != generation} " +
                        "finishing=${activity.isFinishing} destroyed=${activity.isDestroyed}")
                    uiState = uiState.copy(installPreparing = false)
                }
            }
        }, "new-ui-update-install-prep").start()
    }

    fun refreshInstallResult() {
        UpdateInstaller.reconcile(activity)
        val result = UpdateInstaller.result(activity) ?: return
        if (result.revision == installResultRevision) return
        installResultRevision = result.revision
        val message = when (result.status) {
            UpdateInstaller.STAGED -> R.string.update_install_preparing
            UpdateInstaller.COMMITTED, UpdateInstaller.INSTALLING -> R.string.update_install_requested
            PackageInstaller.STATUS_PENDING_USER_ACTION -> R.string.update_install_confirm
            PackageInstaller.STATUS_SUCCESS -> R.string.update_install_success
            PackageInstaller.STATUS_FAILURE_ABORTED -> R.string.update_install_cancelled
            PackageInstaller.STATUS_FAILURE_BLOCKED -> R.string.update_install_blocked
            PackageInstaller.STATUS_FAILURE_CONFLICT -> R.string.update_install_conflict
            PackageInstaller.STATUS_FAILURE_INVALID, PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> R.string.update_install_incompatible
            PackageInstaller.STATUS_FAILURE_STORAGE -> R.string.update_install_storage
            else -> R.string.update_install_failure
        }
        uiState = uiState.copy(
            stage = if (result.status == PackageInstaller.STATUS_SUCCESS) NewUiUpdateStage.IDLE
                else if (updateFile?.isFile == true) NewUiUpdateStage.READY else uiState.stage,
            installPreparing = result.busy,
            message = activity.getString(message),
        )
        onStateChanged()
    }

    private fun diagnostic(event: String) = DiagnosticLogging.record { UpdateDiagnostics.record(activity.applicationContext, event) }

    private fun latestRelease(): UpdateRelease? =
        UpdateReleaseLookup.latest(installedVersion(), "DiPlay/${installedVersion()}")

    private fun installedVersion(): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.1.0-beta.1"

    companion object {
        private const val TAG = "DiPlay-Update"
    }
}
