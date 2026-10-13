// SPDX-License-Identifier: AGPL-3.0-only
package com.shilapi.xcertplay

internal enum class SettingsCategory {
    OVERVIEW, CONNECTION, DISPLAY, AUDIO, NAVIGATION, VEHICLE, LANGUAGE, DIAGNOSTICS, ADVANCED, ABOUT,
}

/** A settings card. Every entry needs one category in [SettingsInformationArchitecture]; see AGENTS.md. */
internal enum class SettingsSection {
    CARPLAY_CONTROLS,
    WHEEL_KEYS,
    CONNECTION_SETUP,
    DIAGNOSTICS,
    AUTOMATIC_CONNECTION,
    USB_CONNECTION,
    BYD_ADB,
    DISPLAY_AND_PERFORMANCE,
    EXPERIMENTAL_DISPLAY,
    ADVANCED_MEDIA,
    AMBIENT_LIGHTING,
    CAR_BUTTON,
    AUDIO_ROUTING,
    NAVIGATION_WHEEL,
    LOCATION,
    CLUSTER_MAP,
    BYD_NAVIGATION,
    PERMISSIONS_AND_HELP,
    LANGUAGE,
}

internal object SettingsInformationArchitecture {
    val sectionsByCategory: Map<SettingsCategory, Set<SettingsSection>> = mapOf(
        SettingsCategory.OVERVIEW to emptySet(),
        SettingsCategory.LANGUAGE to setOf(SettingsSection.LANGUAGE),
        SettingsCategory.ABOUT to emptySet(),
        SettingsCategory.CONNECTION to setOf(
            SettingsSection.CONNECTION_SETUP,
            SettingsSection.AUTOMATIC_CONNECTION,
            SettingsSection.USB_CONNECTION,
            SettingsSection.BYD_ADB,
            SettingsSection.PERMISSIONS_AND_HELP,
        ),
        SettingsCategory.DISPLAY to setOf(SettingsSection.DISPLAY_AND_PERFORMANCE),
        SettingsCategory.AUDIO to setOf(SettingsSection.AUDIO_ROUTING),
        SettingsCategory.NAVIGATION to setOf(
            SettingsSection.LOCATION,
            SettingsSection.BYD_NAVIGATION,
            SettingsSection.CLUSTER_MAP,
        ),
        SettingsCategory.VEHICLE to setOf(
            SettingsSection.CARPLAY_CONTROLS,
            SettingsSection.WHEEL_KEYS,
            SettingsSection.CAR_BUTTON,
        ),
        SettingsCategory.DIAGNOSTICS to setOf(SettingsSection.DIAGNOSTICS),
        SettingsCategory.ADVANCED to setOf(
            SettingsSection.EXPERIMENTAL_DISPLAY,
            SettingsSection.ADVANCED_MEDIA,
            SettingsSection.NAVIGATION_WHEEL,
            SettingsSection.AMBIENT_LIGHTING,
        ),
    )
}
