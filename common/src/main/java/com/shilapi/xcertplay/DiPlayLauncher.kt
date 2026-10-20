// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

import android.app.Activity
import android.content.Context
import android.content.Intent

internal object DiPlayLauncher {
    fun isLauncherIntent(intent: Intent): Boolean =
        intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_LAUNCHER) &&
            !intent.hasExtra(EXTRA_PAGE)

    fun openCarPlayProjection(context: Context) {
        context.startActivity(
            Intent(context, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_ENTER_CARPLAY_PROJECTION, true)
                .addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        (context as? Activity)?.overridePendingTransition(0, 0)
    }

    /** Starts [CarPlayHostActivity] for connection while [NewUiActivity] stays visible on top. */
    fun startConnectionHub(activity: Activity) {
        activity.startActivity(
            Intent(activity, CarPlayHostActivity::class.java)
                .putExtra(EXTRA_NEW_UI_CONNECTION_HUB, true)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION),
        )
        activity.overridePendingTransition(0, 0)
    }

    /** Start or continue bring-up under [CarPlayHostActivity] while keeping [NewUiActivity] in front. */
    const val EXTRA_NEW_UI_CONNECTION_HUB = "com.shihab.diplay.NEW_UI_CONNECTION_HUB"

    /** Open the video surface; do not stay on the host waiting/preparation screen. */
    const val EXTRA_ENTER_CARPLAY_PROJECTION = "com.shihab.diplay.ENTER_CARPLAY_PROJECTION"

    const val EXTRA_PAGE = "page"
}
