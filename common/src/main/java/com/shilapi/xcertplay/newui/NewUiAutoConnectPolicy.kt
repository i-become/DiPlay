package com.shilapi.xcertplay.newui

import android.content.Intent
import com.shilapi.xcertplay.DiPlayActivity
import com.shilapi.xcertplay.DiPlayLauncher
import com.shilapi.xcertplay.PhoneBluetoothReceiver

/** When the Hongqi shell should start CarPlay immediately instead of showing the connection page. */
internal object NewUiAutoConnectPolicy {
    fun shouldConnectOnColdStart(
        intent: Intent,
        phoneAddress: String?,
        setupError: String?,
        sessionRunning: Boolean,
        autoConnectEnabled: Boolean,
    ): Boolean {
        if (!autoConnectEnabled) return false
        if (phoneAddress.isNullOrBlank()) return false
        if (setupError != null) return false
        if (sessionRunning) return false
        if (intent.getBooleanExtra(PhoneBluetoothReceiver.EXTRA_AUTO_CONNECT, false)) return false
        if (intent.hasExtra(DiPlayLauncher.EXTRA_PAGE)) return false
        if (intent.hasExtra(DiPlayActivity.EXTRA_SETTINGS_CATEGORY)) return false
        return true
    }
}
