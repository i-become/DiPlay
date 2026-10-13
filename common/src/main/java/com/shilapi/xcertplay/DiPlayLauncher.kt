// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.content.Context
import android.content.Intent

internal object DiPlayLauncher {
    fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            !intent.hasExtra(EXTRA_PAGE)

    fun openCarPlayProjection(context: Context) {
        context.startActivity(
            Intent(context, CarPlayHostActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
        )
    }

    const val EXTRA_PAGE = "page"
}
