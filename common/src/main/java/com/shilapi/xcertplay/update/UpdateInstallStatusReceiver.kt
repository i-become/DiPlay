package com.shilapi.xcertplay.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller

/** Explicit PendingIntent target; the system can deliver a result after the UI is recreated. */
class UpdateInstallStatusReceiver : BroadcastReceiver() {
    @Suppress("DEPRECATION")
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (UpdateInstaller.result(context)?.sessionId != id) return
        UpdateInstaller.record(context, id, status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty())
        if (status != PackageInstaller.STATUS_PENDING_USER_ACTION) return
        val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if (confirmation == null) {
            UpdateInstaller.record(context, id, UpdateInstaller.LAUNCH_FAILED, "missing system confirmation intent")
            UpdateInstaller.abandon(context, id)
            return
        }
        try {
            context.startActivity(
                Intent(context, UpdateInstallActivity::class.java)
                    .putExtra(PackageInstaller.EXTRA_SESSION_ID, id)
                    .putExtra(Intent.EXTRA_INTENT, confirmation)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        } catch (failure: RuntimeException) {
            UpdateInstaller.record(context, id, UpdateInstaller.LAUNCH_FAILED, "confirmation ${failure.javaClass.simpleName}: ${failure.message}")
            UpdateInstaller.abandon(context, id)
        }
    }
}
