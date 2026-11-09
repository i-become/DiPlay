package com.shilapi.xcertplay.hud

import android.content.Context

/** Settings for the Hongqi HS5/QiSi navigation integration. */
object Hs5OutputSettings {
    private const val PREFS = "diplay_hs5_outputs"
    private const val KEY_ENABLED = "navigation_enabled"
    private const val KEY_PHONE_AUDIO_ROUTE = "phone_audio_route_experimental"
    private const val KEY_PHONE_AUDIO_ROUTE_APPS = "phone_audio_route_apps_experimental"
    private const val KEY_HS5_CLUSTER_VISIBLE = "hs5_cluster_visible"

    fun enabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()

    /** Hold Telecom calls in the head unit audio path when CarPlay call audio is active. */
    fun phoneAudioRoute(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHONE_AUDIO_ROUTE, false)

    fun setPhoneAudioRoute(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_PHONE_AUDIO_ROUTE, enabled).apply()

    /** The equivalent audio routing opt-in for VoIP applications. */
    fun phoneAudioRouteApps(context: Context): Boolean =
        prefs(context).getBoolean(KEY_PHONE_AUDIO_ROUTE_APPS, false)

    fun setPhoneAudioRouteApps(context: Context, enabled: Boolean) =
        prefs(context).edit().putBoolean(KEY_PHONE_AUDIO_ROUTE_APPS, enabled).apply()

    /** Whether the CarPlay map is currently visible on the HS5 cluster. */
    fun hs5ClusterVisible(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HS5_CLUSTER_VISIBLE, true)

    fun setHs5ClusterVisible(context: Context, visible: Boolean) =
        prefs(context).edit().putBoolean(KEY_HS5_CLUSTER_VISIBLE, visible).apply()

    /** CarPlay call keys are part of the HS5 integration and remain enabled by default. */
    fun carPlayCallControls(context: Context): Boolean = enabled(context)

    /** True only for the supported Hongqi/QiSi navigation stack. */
    fun available(context: Context): Boolean =
        Hs5HeadUnit.isHeadUnit(context) &&
            (Hs5NavPackages.resolvedPackage(context) != null ||
                Hs5NavPackages.scanCandidatePackages(context).isNotEmpty())

    fun navigationAvailable(context: Context): Boolean = available(context)

    fun hs5NavigationAvailable(context: Context): Boolean = available(context)

    fun resolvedGaodePackage(context: Context): String? = Hs5NavPackages.resolvedPackage(context)

    fun gaodeBroadcastReceivers(context: Context): String =
        Hs5NavPackages.packagesFromAutonaviBroadcast(context).joinToString().ifBlank { "none" }

    fun gaodeMapCandidates(context: Context): String =
        Hs5NavPackages.scanCandidatePackages(context).take(12).joinToString().ifBlank { "none" }

    fun navigationOutputPackages(context: Context): String =
        Hs5NavPackages.navigationOutputPackages(context).joinToString().ifBlank { "none" }

    fun isQisiNavigationStack(context: Context): Boolean = Hs5NavPackages.isQisiStack(context)

    fun headUnitIdentificationTags(context: Context): String = Hs5HeadUnit.headUnitTags(context)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
