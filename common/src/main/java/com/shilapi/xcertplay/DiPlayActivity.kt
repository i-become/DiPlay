// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.newui.NewUiActivity

/** Legacy class name kept for deep links; the Hongqi shell lives in [NewUiActivity]. */
class DiPlayActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forward = Intent(this, NewUiActivity::class.java).apply {
            action = intent.action
            data = intent.data
            intent.extras?.let { putExtras(it) }
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(forward)
        finish()
    }

    companion object {
        internal fun isLauncherIntent(intent: Intent): Boolean = DiPlayLauncher.isLauncherIntent(intent)

        const val EXTRA_SETTINGS_CATEGORY = "settings_category"
    }
}
