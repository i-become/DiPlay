package com.shilapi.xcertplay.newui

import com.shilapi.xcertplay.host.R

enum class NewUiDestination(
    val labelRes: Int,
    val iconRes: Int,
) {
    CONNECTION(R.string.new_ui_nav_connection, R.drawable.new_ui_ic_sidebar_link),
    DISPLAY(R.string.new_ui_nav_display, R.drawable.ic_dp_display),
    SETTINGS(R.string.new_ui_nav_settings, R.drawable.ic_dp_controls),
    DIAGNOSTICS(R.string.new_ui_nav_diagnostics, R.drawable.ic_dp_diagnostics),
    ABOUT(R.string.new_ui_nav_about, R.drawable.ic_dp_about),
}
