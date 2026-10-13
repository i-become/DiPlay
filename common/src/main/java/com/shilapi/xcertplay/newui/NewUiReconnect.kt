package com.shilapi.xcertplay.newui

import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.CarPlayBackgroundSession
import com.shilapi.xcertplay.appDialogBuilder
import com.shilapi.xcertplay.host.R

internal fun ComponentActivity.applyWithReconnectConfirm(
    title: String,
    message: String,
    onApplied: () -> Unit,
    save: () -> Unit,
) {
    if (!CarPlayBackgroundSession.hasSession()) {
        save()
        onApplied()
        return
    }
    appDialogBuilder()
        .setTitle(title)
        .setMessage(message)
        .setPositiveButton(R.string.apply_and_reconnect) { _, _ ->
            save()
            onApplied()
        }
        .setNegativeButton(R.string.cancel, null)
        .show()
}
