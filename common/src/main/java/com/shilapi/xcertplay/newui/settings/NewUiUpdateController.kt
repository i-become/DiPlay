package com.shilapi.xcertplay.newui.settings

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.core.content.FileProvider
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.update.UpdateApkExport
import com.shilapi.xcertplay.update.UpdateAvailability
import com.shilapi.xcertplay.update.UpdateCatalog
import com.shilapi.xcertplay.update.UpdateChecksums
import com.shilapi.xcertplay.update.UpdateClient
import com.shilapi.xcertplay.update.UpdateRelease
import com.shilapi.xcertplay.update.UpdateReleaseLookup
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
    private var savedUri: Uri? = null

    fun checkForUpdates() {
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
                        val version = installedVersion()
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
                        Log.w(TAG, "update check failed", failure)
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
        if (release == null) {
            release = UpdateAvailability.available(activity, installedVersion())
        }
        if (release == null) return
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
                var latest = latestRelease() ?: throw IOException(activity.getString(R.string.update_up_to_date))
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
                val expected = UpdateChecksums.parse(checksumsFile.readText())
                val actual = UpdateChecksums.sha256Hex(apkFile)
                if (!UpdateChecksums.matches(expected, latest.apkName, actual)) {
                    throw IOException("Checksum mismatch for ${latest.apkName}")
                }
                release = latest
                apkFile
            }.onFailure { directory.deleteRecursively() }
            val saved = outcome.getOrNull()?.let { UpdateApkExport.copy(activity.applicationContext, it) }
            activity.runOnUiThread {
                if (gen != generation || activity.isFinishing || activity.isDestroyed) {
                    directory.deleteRecursively()
                    return@runOnUiThread
                }
                outcome.fold(
                    { file ->
                        updateFile = file
                        savedUri = saved?.uri
                        uiState = NewUiUpdateUiState(
                            stage = NewUiUpdateStage.READY,
                            releaseTag = release?.tagName,
                            savedPath = saved?.path,
                        )
                        onStateChanged()
                    },
                    { failure ->
                        Log.w(TAG, "update download failed", failure)
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
        val file = updateFile ?: return
        val gen = generation
        Thread({
            val publicUri = savedUri?.takeIf { sameContent(it, file) }
            activity.runOnUiThread {
                if (gen == generation && !activity.isFinishing && !activity.isDestroyed) {
                    startInstaller(file, publicUri)
                }
            }
        }, "new-ui-update-install-prep").start()
    }

    private fun startInstaller(file: File, publicUri: Uri?) {
        val uri = publicUri ?: FileProvider.getUriForFile(activity, "${activity.packageName}.update-apks", file)
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val installers = activity.packageManager.queryIntentActivities(view, PackageManager.MATCH_DEFAULT_ONLY).size
        val launch = if (installers > 1) Intent.createChooser(view, null) else view
        val outcome = "${if (installers > 1) "chooser" else "direct"} uri=${if (publicUri != null) "public" else "provider"} installers=$installers"
        try {
            activity.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Log.i(TAG, "install started: $outcome")
            UpdateAvailability.recordInstall(activity.applicationContext, System.currentTimeMillis(), outcome)
        } catch (failure: RuntimeException) {
            Log.w(TAG, "install failed to start: $outcome", failure)
            UpdateAvailability.recordInstall(
                activity.applicationContext,
                System.currentTimeMillis(),
                "$outcome failed=${failure.javaClass.simpleName}",
            )
            uiState = uiState.copy(
                stage = NewUiUpdateStage.FAILED,
                message = activity.getString(R.string.update_failed),
            )
            onStateChanged()
        }
    }

    private fun sameContent(uri: Uri, verified: File): Boolean = runCatching {
        val copy = activity.contentResolver.openInputStream(uri)?.use { UpdateChecksums.sha256Hex(it) }
        copy != null && copy == UpdateChecksums.sha256Hex(verified)
    }.getOrDefault(false)

    private fun latestRelease(): UpdateRelease? =
        UpdateReleaseLookup.latest(installedVersion(), "DiPlay/${installedVersion()}")

    private fun installedVersion(): String =
        activity.packageManager.getPackageInfo(activity.packageName, 0).versionName ?: "0.1.0-beta.1"

    companion object {
        private const val TAG = "DiPlay-Update"
    }
}
