package com.shilapi.xcertplay.newui.settings

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateApkExport
import com.shilapi.xcertplay.update.UpdateArchive
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateCatalog
import com.shilapi.xcertplay.update.UpdateChecksums
import com.shilapi.xcertplay.update.UpdateClient
import com.shilapi.xcertplay.update.UpdateDiagnostics
import com.shilapi.xcertplay.update.UpdateRelease
import com.shilapi.xcertplay.update.UpdateReleaseLookup
import com.shilapi.xcertplay.update.UpdateVersion
import com.shilapi.xcertplay.update.UpdateInstaller
import java.io.File
import java.io.IOException

internal class NewUiUpdateController(
    private val activity: ComponentActivity,
    private val lookup: (String, String) -> UpdateRelease? = { _, agent -> UpdateReleaseLookup.latestPublished(agent) },
    private val download: (String, File, (Long, Long?) -> Unit) -> Unit = UpdateClient::download,
    private val onStateChanged: () -> Unit,
) {
    var uiState = NewUiUpdateUiState()
        private set

    private var generation = 0
    private var release: UpdateRelease? = null
    private var updateFile: File? = null
    private var verifiedDigest: String? = null
    private var installResultRevision = 0L

    fun checkForUpdates() {
        if (uiState.installPreparing || uiState.stage in setOf(NewUiUpdateStage.CHECKING,
                NewUiUpdateStage.DOWNLOADING, NewUiUpdateStage.VERIFYING)) return
        diagnostic("check-clicked")
        uiState = uiState.copy(stage = NewUiUpdateStage.CHECKING, message = null, progress = null,
            releaseTag = null, releaseTitle = null, releaseDescription = null)
        onStateChanged()
        val gen = ++generation
        release = null
        updateFile = null
        verifiedDigest = null
        Thread({
            val outcome = runCatching { latestRelease() }
            activity.runOnUiThread {
                if (gen != generation || activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                outcome.fold(
                    { found ->
                        UpdateAvailability.recordAttempt(activity, System.currentTimeMillis())
                        val newer = found != null && UpdateVersion.isNewer(found.tagName, installedVersion())
                        diagnostic("check-completed updateAvailable=$newer tag=${found?.tagName ?: "none"}")
                        if (!newer) {
                            UpdateAvailability.clear(activity)
                            uiState = NewUiUpdateUiState(
                                stage = NewUiUpdateStage.IDLE,
                                message = activity.getString(if (found == null) R.string.new_ui_update_no_release else R.string.update_up_to_date),
                                releaseTag = found?.tagName,
                                releaseTitle = found?.title?.takeIf { it.isNotBlank() },
                                releaseDescription = found?.description?.takeIf { it.isNotBlank() },
                            )
                            release = null
                        } else {
                            UpdateAvailability.save(activity, found!!)
                            release = found
                            uiState = NewUiUpdateUiState(
                                stage = NewUiUpdateStage.AVAILABLE,
                                releaseTag = found.tagName,
                                releaseTitle = found.title.takeIf { it.isNotBlank() },
                                releaseDescription = found.description.takeIf { it.isNotBlank() },
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
        if (cached != null && uiState == NewUiUpdateUiState() && release == null) {
            release = cached
            uiState = NewUiUpdateUiState(stage = NewUiUpdateStage.AVAILABLE, releaseTag = cached.tagName,
                releaseTitle = cached.title.takeIf { it.isNotBlank() },
                releaseDescription = cached.description.takeIf { it.isNotBlank() })
            onStateChanged()
        }
    }

    fun downloadUpdate() {
        if (uiState.installPreparing || uiState.stage in setOf(NewUiUpdateStage.CHECKING,
                NewUiUpdateStage.DOWNLOADING, NewUiUpdateStage.VERIFYING)) return
        if (release == null) {
            release = UpdateAvailability.available(activity, installedVersion())
        }
        if (release == null) {
            diagnostic("download-unavailable reason=no-release")
            return
        }
        // Pin both attachments to the release the user chose; never reselect latest mid-download.
        val selected = release!!
        diagnostic("download-started tag=${selected.tagName} apk=${selected.apkName}")
        updateFile = null
        verifiedDigest = null
        uiState = uiState.copy(
            stage = NewUiUpdateStage.DOWNLOADING,
            message = null,
            progress = null,
            savedPath = null,
            releaseTag = selected.tagName,
        )
        onStateChanged()
        val gen = ++generation
        Thread({
            val directory = File(activity.cacheDir, "update/${java.util.UUID.randomUUID()}")
            val outcome = runCatching {
                val latest = selected
                val checksumsFile = File(directory, UpdateCatalog.CHECKSUMS_FILE)
                download(latest.checksumsUrl, checksumsFile) { _, _ -> }
                val expected = UpdateChecksums.read(checksumsFile)
                if (!expected.containsKey(latest.apkName)) {
                    diagnostic("verification-failed reason=checksum-entry-missing tag=${latest.tagName}")
                    throw IOException("发布校验文件缺少 ${latest.apkName} 的 SHA-256，请检查发布附件")
                }
                val apkFile = File(directory, latest.apkName)
                download(latest.apkUrl, apkFile) progress@{ written, total ->
                    val percent = total?.takeIf { it > 0 }?.let { (written * 100 / it).toInt() } ?: return@progress
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
                val actual = UpdateChecksums.sha256Hex(apkFile)
                if (!UpdateChecksums.matches(expected, latest.apkName, actual)) {
                    diagnostic("verification-failed reason=checksum-mismatch")
                    throw IOException("安装包 SHA-256 校验不一致，请重新下载；若仍失败，请检查发布附件是否与校验文件匹配")
                }
                UpdateArchive.verify(activity.applicationContext, apkFile, latest)
                diagnostic("verification-passed")
                apkFile to actual
            }.onFailure { directory.deleteRecursively() }
            val saved = outcome.getOrNull()?.first?.let { UpdateApkExport.copy(activity.applicationContext, it) }
            if (outcome.isSuccess) diagnostic("apk-export completed=${saved != null} publicUri=${saved?.uri != null}")
            activity.runOnUiThread {
                if (gen != generation || activity.isFinishing || activity.isDestroyed) {
                    directory.deleteRecursively()
                    return@runOnUiThread
                }
                outcome.fold(
                    { (file, digest) ->
                        diagnostic("download-ready bytes=${file.length()}")
                        updateFile = file
                        verifiedDigest = digest
                        uiState = NewUiUpdateUiState(
                            stage = NewUiUpdateStage.READY,
                            releaseTag = release?.tagName,
                            releaseTitle = release?.title?.takeIf { it.isNotBlank() },
                            releaseDescription = release?.description?.takeIf { it.isNotBlank() },
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
                            releaseTag = selected.tagName,
                            releaseTitle = selected.title.takeIf { it.isNotBlank() },
                            releaseDescription = selected.description.takeIf { it.isNotBlank() },
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
        val digest = verifiedDigest
        if (file == null || !file.isFile || file.length() <= 0L || digest == null) {
            diagnostic("install-failed reason=apk-missing")
            uiState = uiState.copy(stage = NewUiUpdateStage.FAILED, message = activity.getString(R.string.update_install_missing))
            onStateChanged()
            return
        }
        uiState = uiState.copy(installPreparing = true, message = activity.getString(R.string.update_install_preparing))
        installResultRevision = UpdateInstaller.result(activity)?.revision ?: 0L
        onStateChanged()
        val gen = generation
        val started = SystemClock.elapsedRealtime()
        Thread({
            val prepared = runCatching { UpdateInstaller.install(activity.applicationContext, file, digest) }
            diagnostic("install-prepared success=${prepared.isSuccess} elapsedMs=${SystemClock.elapsedRealtime() - started}")
            activity.runOnUiThread {
                if (gen == generation && !activity.isFinishing && !activity.isDestroyed) {
                    prepared.fold(
                        { refreshInstallResult() },
                        { failure ->
                            diagnostic("install-prepare-failed failureClass=${failure.javaClass.simpleName} detail=${failure.message}")
                            uiState = uiState.copy(stage = NewUiUpdateStage.FAILED, installPreparing = false,
                                message = activity.getString(R.string.update_sota_failure, "SOTA_REQUEST_FAILED"))
                            onStateChanged()
                        },
                    )
                } else {
                    // SOTA owns a submitted request; destroying this UI must not revoke its files.
                    uiState = uiState.copy(installPreparing = false)
                }
            }
        }, "new-ui-update-install-prep").start()
    }

    fun refreshInstallResult() {
        UpdateInstaller.reconcileAsync(activity.applicationContext)
        val result = UpdateInstaller.result(activity) ?: return
        if (result.revision == installResultRevision) return
        installResultRevision = result.revision
        val message = when (result.status) {
            UpdateInstaller.Status.PREPARING -> R.string.update_install_preparing
            UpdateInstaller.Status.WAITING -> R.string.update_install_requested
            UpdateInstaller.Status.SUCCEEDED -> R.string.update_install_success
            UpdateInstaller.Status.TIMED_OUT -> R.string.update_sota_timeout
            UpdateInstaller.Status.FAILED -> R.string.update_sota_failure
        }
        uiState = uiState.copy(
            stage = if (result.status == UpdateInstaller.Status.SUCCEEDED) NewUiUpdateStage.IDLE
                else if (updateFile?.isFile == true) NewUiUpdateStage.READY else uiState.stage,
            installPreparing = result.busy,
            message = if (result.status == UpdateInstaller.Status.FAILED) activity.getString(message, result.reason)
                else activity.getString(message),
        )
        onStateChanged()
    }

    private fun diagnostic(event: String) = DiagnosticLogging.record { UpdateDiagnostics.record(activity.applicationContext, event) }

    private fun latestRelease(): UpdateRelease? =
        lookup(installedVersion(), "DiPlay/${installedVersion()}")

    private fun installedVersion(): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.1.0-beta.1"

    companion object {
        private const val TAG = "DiPlay-Update"
    }
}
