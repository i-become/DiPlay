package com.shilapi.xcertplay.update

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.SystemClock
import java.io.File
import java.io.IOException

/** Stage the APK in system-owned storage instead of handing an OEM installer a content URI. */
internal object UpdateInstaller {
    private const val PREFERENCES = "diplay_update_install"
    const val STAGED = -10
    const val COMMITTED = -11
    const val INSTALLING = -12
    const val LAUNCH_FAILED = -13

    data class Result(val sessionId: Int, val status: Int, val revision: Long) {
        val busy: Boolean get() = status == STAGED || status == COMMITTED ||
            status == PackageInstaller.STATUS_PENDING_USER_ACTION || status == INSTALLING
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun result(context: Context): Result? {
        val prefs = preferences(context)
        if (!prefs.contains("session")) return null
        return Result(prefs.getInt("session", -1), prefs.getInt("status", LAUNCH_FAILED), prefs.getLong("revision", 0))
    }

    /** Called on the worker thread: parsing, copying and fsync must not block the main screen. */
    @Suppress("DEPRECATION")
    fun prepare(context: Context, file: File): Int {
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("APK metadata could not be read")
        if (archive.packageName != context.packageName) throw IOException("APK package does not match DiPlay")
        val version = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        DiagnosticLogging.record { UpdateDiagnostics.record(context, "install-apk package=${archive.packageName} version=$version bytes=${file.length()}") }
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    file.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
            }
            synchronized(this) {
                preferences(context).edit().putInt("session", id).putLong("version", version)
                    .putLong("started", System.currentTimeMillis()).putInt("status", STAGED)
                    .putLong("updatedElapsed", SystemClock.elapsedRealtime())
                    .putLong("revision", System.nanoTime()).commit()
            }
            DiagnosticLogging.record { UpdateDiagnostics.record(context, "install-session-staged session=$id bytes=${file.length()}") }
            return id
        } catch (failure: Exception) {
            runCatching { installer.abandonSession(id) }
            throw failure
        }
    }

    fun commit(context: Context, id: Int) {
        val intent = Intent(context, UpdateInstallStatusReceiver::class.java)
            .setAction("${context.packageName}.UPDATE_INSTALL_RESULT.$id")
            .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getBroadcast(context, id, intent, flags)
        record(context, id, COMMITTED, "submitted to PackageInstaller")
        try {
            context.packageManager.packageInstaller.openSession(id).use { it.commit(callback.intentSender) }
        } catch (failure: Exception) {
            abandon(context, id)
            record(context, id, LAUNCH_FAILED, "commit ${failure.javaClass.simpleName}: ${failure.message}")
            throw failure
        }
    }

    fun abandon(context: Context, id: Int) {
        runCatching { context.packageManager.packageInstaller.abandonSession(id) }
    }

    @Synchronized
    fun record(context: Context, id: Int, status: Int, detail: String) {
        if (result(context)?.sessionId != id) {
            DiagnosticLogging.record { UpdateDiagnostics.record(context, "install-result-ignored session=$id status=$status") }
            return
        }
        val message = detail.replace(Regex("[\\r\\n]+"), " ").take(350)
        preferences(context).edit().putInt("status", status).putLong("revision", System.nanoTime())
            .putLong("updatedElapsed", SystemClock.elapsedRealtime()).apply()
        val outcome = "session=$id status=$status detail=$message"
        DiagnosticLogging.record { UpdateDiagnostics.record(context, "install-result $outcome") }
        UpdateAvailability.recordInstall(context, System.currentTimeMillis(), outcome)
    }

    /** Self-updates can replace the process before its final success broadcast is delivered. */
    @Suppress("DEPRECATION")
    fun reconcile(context: Context) {
        val current = result(context) ?: return
        if (!current.busy) return
        val prefs = preferences(context)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val version = if (Build.VERSION.SDK_INT >= 28) installed.longVersionCode else installed.versionCode.toLong()
        val info = context.packageManager.packageInstaller.getSessionInfo(current.sessionId)
        val age = SystemClock.elapsedRealtime() - prefs.getLong("updatedElapsed", SystemClock.elapsedRealtime())
        val timeout = when (current.status) {
            STAGED -> 30_000L
            PackageInstaller.STATUS_PENDING_USER_ACTION -> 600_000L
            else -> 120_000L
        }
        if (version >= prefs.getLong("version", Long.MAX_VALUE) && installed.lastUpdateTime >= prefs.getLong("started", Long.MAX_VALUE)) {
            record(context, current.sessionId, PackageInstaller.STATUS_SUCCESS, "verified installed version=$version after process replacement")
        } else if (info == null) {
            record(context, current.sessionId, LAUNCH_FAILED, "installation session is no longer available; no successful update found")
        } else if (age >= timeout && !info.isActive) {
            record(context, current.sessionId, LAUNCH_FAILED, "no installation result after ${age}ms status=${current.status} active=${info.isActive} progress=${info.progress}")
            abandon(context, current.sessionId)
        }
    }
}
