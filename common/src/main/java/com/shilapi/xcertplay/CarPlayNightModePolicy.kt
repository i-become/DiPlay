package com.shilapi.xcertplay

import android.content.Context
import android.content.res.Configuration
import java.util.Calendar

/** Resolves the CarPlay night flag from persisted mode without a live host controller. */
internal object CarPlayNightModePolicy {
    fun effectiveNight(context: Context): Boolean {
        val systemNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val minute = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }
        return when (AirPlayPersistence.loadCarPlayNightMode(context)) {
            CarPlayNightMode.DAY -> false
            CarPlayNightMode.NIGHT -> true
            CarPlayNightMode.SCHEDULE -> AirPlayPersistence.loadCarPlayNightSchedule(context).isNight(minute)
            CarPlayNightMode.SYSTEM, CarPlayNightMode.AMBIENT -> HongqiPositionLights.night() ?: systemNight
        }
    }
}
