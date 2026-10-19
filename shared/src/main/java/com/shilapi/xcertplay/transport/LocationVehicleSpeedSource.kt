package com.shilapi.xcertplay.transport

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import android.util.Log

/** Bench speed when GPS reports stationary (~50 km/h) for instrument-cluster previews. */
const val DEBUG_CLUSTER_DEMO_SPEED_METERS_PER_SECOND = 13.9

/**
 * Wheel speed from Android location fixes for phones and head units without BYD ADB wheel data.
 * Used with simulated cluster debugging so Apple Maps can render instrument-cluster UI.
 */
class LocationVehicleSpeedSource(
    context: Context,
    private val demoMetersPerSecondWhenStationary: Double? = null,
    private val onSpeedSample: ((Double) -> Unit)? = null,
) : VehicleSpeedSource {
    private val locationManager =
        context.applicationContext.getSystemService(LocationManager::class.java)
    private val lock = Any()
    private val pending = ArrayList<VehicleSpeedSample>()
    @Volatile private var running = false
    private var gear = VehicleGear.DRIVE

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!running) return
            val speed = resolveSpeedMetersPerSecond(location)
            synchronized(lock) {
                pending.add(VehicleSpeedSample(SystemClock.elapsedRealtime(), speed))
            }
            onSpeedSample?.invoke(speed)
        }

        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        override fun onStatusChanged(provider: String, status: Int, extras: Bundle?) = Unit
    }

    private fun resolveSpeedMetersPerSecond(location: Location): Double {
        val measured = if (location.hasSpeed()) location.speed.toDouble().coerceAtLeast(0.0) else 0.0
        if (measured > 0.5) return measured
        return demoMetersPerSecondWhenStationary?.coerceAtLeast(0.0) ?: 0.0
    }

    @SuppressLint("MissingPermission")
    override fun start() {
        if (running) return
        running = true
        val providers = buildList {
            add(LocationManager.GPS_PROVIDER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(LocationManager.FUSED_PROVIDER)
            }
            add(LocationManager.NETWORK_PROVIDER)
        }.filter { it in locationManager.allProviders }
        var subscribed = 0
        for (provider in providers) {
            try {
                locationManager.requestLocationUpdates(
                    provider,
                    UPDATE_INTERVAL_MILLIS,
                    MIN_DISTANCE_METERS,
                    listener,
                    Looper.getMainLooper(),
                )
                subscribed++
            } catch (error: Exception) {
                Log.w(TAG, "Could not subscribe to $provider for vehicle speed", error)
            }
        }
        if (subscribed == 0) {
            Log.w(TAG, "No location provider for simulated vehicle speed")
        } else if (demoMetersPerSecondWhenStationary != null) {
            Log.i(TAG, "Simulated cluster vehicle speed active demo=${demoMetersPerSecondWhenStationary}m/s when stationary")
        }
    }

    override fun stop() {
        if (!running) return
        running = false
        runCatching { locationManager.removeUpdates(listener) }
        synchronized(lock) { pending.clear() }
    }

    override fun drain(): VehicleSpeedReading? {
        val batch = synchronized(lock) {
            if (pending.isEmpty()) return null
            pending.toList().also { pending.clear() }
        }
        return VehicleSpeedReading(gear, batch)
    }

    private companion object {
        const val TAG = "DiPlay-VehicleSpeed"
        const val UPDATE_INTERVAL_MILLIS = 250L
        const val MIN_DISTANCE_METERS = 0f
    }
}
