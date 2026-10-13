package com.shilapi.xcertplay.newui

import android.content.Intent
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.shilapi.xcertplay.host.R

internal object NewUiHeadUnitIntents {
    fun openHome(activity: ComponentActivity) {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        runCatching { activity.startActivity(home) }
            .onFailure {
                Toast.makeText(
                    activity,
                    R.string.open_this_setting_from_your_car_s_settings_app,
                    Toast.LENGTH_LONG,
                ).show()
            }
    }
}
