package com.shilapi.xcertplay.hud

import java.util.concurrent.TimeUnit

/** Localized strings written into the QiSi instrument guidance parcel. */
internal object HongqiQisiLabels {
    fun segmentDistance(meters: Int): String = when {
        meters < 15 -> "现在"
        meters < 1_000 -> "${meters}米"
        else -> {
            val kmWhole = meters / 1_000
            val remainder = meters % 1_000
            if (remainder == 0) "${kmWhole}公里" else String.format("%.1f公里", meters / 1_000.0)
        }
    }

    fun routeDistance(meters: Int?): String {
        if (meters == null || meters < 0) return ""
        return segmentDistance(meters)
    }

    fun eta(seconds: Long?): String {
        if (seconds == null || seconds < 0) return ""
        val minutes = TimeUnit.SECONDS.toMinutes(seconds.coerceAtMost(Int.MAX_VALUE.toLong()))
        if (minutes < 60) return "${minutes.coerceAtLeast(1)}分钟"
        val hours = minutes / 60
        val rem = minutes % 60
        return if (rem == 0L) "${hours}小时" else "${hours}小时${rem}分钟"
    }
}
