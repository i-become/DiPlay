package com.shilapi.xcertplay.update

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Durable SOTA request state, not a PackageInstaller session or an OEM success callback. */
internal object UpdateInstaller {
    const val TIMEOUT_MS = 120_000L
    internal const val PREFERENCES = "diplay_sota_install"
    private val lock = Any()
    private val reconciling = AtomicBoolean()
    private var lastCheck = Long.MIN_VALUE

    enum class Status { PREPARING, WAITING, SUCCEEDED, FAILED, TIMED_OUT }

    data class Result(val requestId: String, val status: Status, val revision: Long, val reason: String) {
        val busy: Boolean get() = status == Status.PREPARING || status == Status.WAITING
    }

    private fun preferences(context: Context) = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun result(context: Context): Result? {
        val values = preferences(context).all
        val id = values["request"] as? String ?: return null
        val status = runCatching { Status.valueOf(values["status"] as String) }.getOrDefault(Status.FAILED)
        return Result(id, status, values["revision"] as? Long ?: 0, values["reason"] as? String ?: "")
    }

    /** All callers must use a background thread, including APK parsing and staging. */
    fun install(context: Context, source: File, digest: String): Result =
        submit(context, source, digest, Hs5SotaTransport(context)) { id ->
            val work = OneTimeWorkRequestBuilder<SotaInstallResultWorker>()
                .setInitialDelay(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("request" to id)).build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("diplay-sota-result", ExistingWorkPolicy.REPLACE, work)
                .result.get(10, TimeUnit.SECONDS)
        }

    internal fun submit(
        context: Context, source: File, digest: String, transport: SotaTransport,
        scheduleTimeout: (String) -> Unit,
    ): Result = synchronized(lock) {
        reconcile(context, transport = transport)
        result(context)?.takeIf { it.busy }?.let { return@synchronized it }
        val id = UUID.randomUUID().toString()
        val prefs = preferences(context)
        check(prefs.edit().clear().putString("request", id)
            .putLong("startedElapsed", SystemClock.elapsedRealtime())
            .putString("status", Status.PREPARING.name).putLong("revision", System.nanoTime()).commit())
        var staging: SotaStaging? = null
        try {
            transport.checkEndpoint()
            val target = transport.verifyArchive(source, digest)
            staging = SotaStaging(id, transport.storageMode())
            // Persist the cleanup token before exposing any staging path to the OEM UID.
            check(prefs.edit().putLong("version", target.version).putLong("previousUpdate", target.previousUpdate)
                .putInt("rootMode", staging.originalMode).commit())
            scheduleTimeout(id)
            transport.stage(staging, source, digest)
            setResult(context, Status.WAITING, "SOTA_SUBMITTED")
            transport.send(staging)
            diagnostic(context, "sota-dispatched request=$id version=${target.version}")
        } catch (failure: Exception) {
            setResult(context, Status.FAILED, (failure as? SotaFailure)?.code ?: "SOTA_REQUEST_FAILED")
            diagnostic(context, "sota-failed type=${failure.javaClass.simpleName} reason=${result(context)?.reason}")
            staging?.let { transport.cleanup(it) }
        }
        result(context)!!
    }

    /** Uses the UI's existing refresh loop; no permanent installer polling task. */
    fun reconcileAsync(context: Context) {
        if (result(context)?.busy != true || !reconciling.compareAndSet(false, true)) return
        val now = SystemClock.elapsedRealtime()
        if (lastCheck != Long.MIN_VALUE && now >= lastCheck && now - lastCheck < 1_000L) {
            reconciling.set(false)
            return
        }
        lastCheck = now
        Thread({
            try { reconcile(context.applicationContext) }
            catch (failure: Exception) { diagnostic(context, "sota-reconcile-failed type=${failure.javaClass.simpleName}") }
            finally { reconciling.set(false) }
        }, "diplay-sota-result").start()
    }

    internal fun reconcile(
        context: Context, requestId: String? = null, expired: Boolean = false,
        transport: SotaTransport = Hs5SotaTransport(context),
    ) = synchronized(lock) {
        val current = result(context) ?: return@synchronized
        if (requestId != null && requestId != current.requestId) return@synchronized
        if (!current.busy && current.status != Status.TIMED_OUT) return@synchronized
        val prefs = preferences(context)
        val installed = context.packageManager.getPackageInfo(context.packageName, 0)
        val updated = Hs5SotaTransport.versionCode(installed) >= prefs.getLong("version", Long.MAX_VALUE) &&
            installed.lastUpdateTime != prefs.getLong("previousUpdate", installed.lastUpdateTime)
        val age = SystemClock.elapsedRealtime() - prefs.getLong("startedElapsed", SystemClock.elapsedRealtime())
        when {
            updated -> setResult(context, Status.SUCCEEDED, "VERSION_CONFIRMED")
            current.busy && (expired || age >= TIMEOUT_MS || age < 0) ->
                setResult(context, Status.TIMED_OUT, "SOTA_RESULT_TIMEOUT")
            else -> return@synchronized
        }
        if (prefs.contains("rootMode")) transport.cleanup(SotaStaging(current.requestId, prefs.getInt("rootMode", 0)))
    }

    private fun setResult(context: Context, status: Status, reason: String) {
        val oldRevision = result(context)?.revision ?: 0
        check(preferences(context).edit().putString("status", status.name).putString("reason", reason)
            .putLong("revision", maxOf(System.nanoTime(), oldRevision + 1)).commit())
        diagnostic(context, "sota-result status=$status reason=$reason")
        DiagnosticLogging.record { UpdateAvailability.recordInstall(context, System.currentTimeMillis(), "sota status=$status reason=$reason") }
    }

    private fun diagnostic(context: Context, text: String) =
        DiagnosticLogging.record { UpdateDiagnostics.record(context, text) }
}
