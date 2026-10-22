package com.shilapi.xcertplay.update

import com.shilapi.xcertplay.diagnostics.DiagnosticLogging

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Bundle

/** Keeps a result link to the confirmation UI, including OEM installers that immediately exit. */
class UpdateInstallActivity : Activity() {
    private val sessionId: Int get() = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (UpdateInstaller.result(this)?.let { it.sessionId == sessionId && it.busy } != true) {
            finish()
            return
        }
        if (savedInstanceState != null) return
        val confirmation = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        if (confirmation == null) {
            failLaunch("missing confirmation intent")
            return
        }
        try {
            // Keep this Activity as the result recipient rather than creating another task.
            confirmation.flags = confirmation.flags and Intent.FLAG_ACTIVITY_NEW_TASK.inv()
            confirmation.putExtra(Intent.EXTRA_RETURN_RESULT, true)
            DiagnosticLogging.record { UpdateDiagnostics.record(this, "install-confirmation-launch session=$sessionId action=${confirmation.action}") }
            startActivityForResult(confirmation, CONFIRM_INSTALL)
        } catch (failure: RuntimeException) {
            failLaunch("${failure.javaClass.simpleName}: ${failure.message}")
        }
    }

    @Deprecated("System installer result bridge")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != CONFIRM_INSTALL) return
        val legacyResult = data?.getIntExtra("android.intent.extra.INSTALL_RESULT", Int.MIN_VALUE)
        DiagnosticLogging.record { UpdateDiagnostics.record(this, "install-confirmation-return session=$sessionId resultCode=$resultCode installResult=$legacyResult") }
        val current = UpdateInstaller.result(this)
        if (current?.sessionId == sessionId && current.status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Android 9's session confirmation also returns RESULT_CANCELED after
            // approval. Only PackageInstaller's status broadcast is authoritative.
            UpdateInstaller.record(this, sessionId, UpdateInstaller.INSTALLING, "confirmation returned resultCode=$resultCode; waiting for system result")
        }
        finish()
    }

    private fun failLaunch(detail: String) {
        UpdateInstaller.record(this, sessionId, UpdateInstaller.LAUNCH_FAILED, detail)
        UpdateInstaller.abandon(this, sessionId)
        finish()
    }

    companion object { private const val CONFIRM_INSTALL = 1 }
}
