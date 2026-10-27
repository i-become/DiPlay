package com.shilapi.xcertplay

import androidx.annotation.StringRes
import com.shilapi.xcertplay.host.R

/** Stable preference values, separate from the phone's cluster content URL. */
enum class ClusterStyle(val preferenceValue: String, @param:StringRes val titleRes: Int,
                        @param:StringRes val descriptionRes: Int) {
    // Keep the historic value so renaming Basic to Urban preserves installed preferences.
    BASIC("basic", R.string.settings_cluster_style_basic, R.string.settings_cluster_style_basic_description),
    SPORT("sport", R.string.settings_cluster_style_sport, R.string.settings_cluster_style_sport_description),
    ORIGINAL("original", R.string.settings_cluster_style_original, R.string.settings_cluster_style_original_description);

    companion object {
        fun fromPreference(value: String?): ClusterStyle = entries.firstOrNull {
            it.preferenceValue == value
        } ?: ORIGINAL
    }
}

