package com.shilapi.xcertplay.transport

import android.location.Location
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class LocationVehicleSpeedSourceTest {
    @Test fun stationaryUsesDemoSpeedWhenConfigured() {
        val source = LocationVehicleSpeedSource(
            org.robolectric.RuntimeEnvironment.getApplication(),
            demoMetersPerSecondWhenStationary = 13.9,
        )
        val method = LocationVehicleSpeedSource::class.java.getDeclaredMethod(
            "resolveSpeedMetersPerSecond",
            Location::class.java,
        ).apply { isAccessible = true }
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
        }
        assertEquals(13.9, method.invoke(source, location) as Double, 0.001)
    }

    @Test fun measuredSpeedOverridesDemo() {
        val source = LocationVehicleSpeedSource(
            org.robolectric.RuntimeEnvironment.getApplication(),
            demoMetersPerSecondWhenStationary = 13.9,
        )
        val method = LocationVehicleSpeedSource::class.java.getDeclaredMethod(
            "resolveSpeedMetersPerSecond",
            Location::class.java,
        ).apply { isAccessible = true }
        val location = Location("gps").apply {
            latitude = 0.0
            longitude = 0.0
            speed = 5f
        }
        assertEquals(5.0, method.invoke(source, location) as Double, 0.001)
    }

    @Test fun pascdEncodesDrainedSamples() {
        val reading = VehicleSpeedReading(
            VehicleGear.DRIVE,
            listOf(VehicleSpeedSample(1_000, 10.0)),
        )
        assertNotNull(PascdEncoder.encode(reading))
    }
}
