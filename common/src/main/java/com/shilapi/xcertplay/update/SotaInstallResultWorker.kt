package com.shilapi.xcertplay.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Worker
import androidx.work.WorkerParameters

internal class SotaInstallResultWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val id = inputData.getString("request") ?: return Result.success()
        return runCatching {
            UpdateInstaller.reconcile(applicationContext, requestId = id, expired = true)
            Result.success()
        }.getOrElse { if (runAttemptCount < 2) Result.retry() else Result.failure() }
    }
}

/** A system-delivered self-replacement event, not a public OEM result that could be spoofed. */
class UpdatePackageReplacedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        Thread({
            try { runCatching { UpdateInstaller.reconcile(context.applicationContext) } }
            finally { pending.finish() }
        }, "diplay-sota-replaced").start()
    }
}
