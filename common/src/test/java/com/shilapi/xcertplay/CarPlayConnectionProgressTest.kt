package com.shilapi.xcertplay

import com.shilapi.xcertplay.orchestration.CarPlayStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CarPlayConnectionProgressTest {
    @Test
    fun debugLogsAdvanceMonotonicallyAlongTheWirelessPath() {
        val samples = listOf(
            "wireless bring-up generation=3 starting" to 1,
            "STEP mfi/ready: MFi authentication provider is ready" to 4,
            "wireless bring-up phase=hotspot+bluetooth elapsedMs=1200 generation=3" to 6,
            "wireless bring-up phase=host-address elapsedMs=1500 generation=3" to 7,
            "wireless AirPlay listener attached bind=[ip] port=7000" to 9,
            "wireless bring-up phase=airplay+bonjour elapsedMs=1800 generation=3" to 10,
            "wireless RFCOMM connected address=[address]" to 13,
            "wireless iAP2 CSM channel opened over RFCOMM" to 14,
            "iap2 identification accepted" to 16,
            "iap2 tx=0x4301 carplay-start-session" to 22,
            "iap2 tx=0x5703 post-transport accessory-wifi-configuration" to 23,
            "PROGRESS wait-first-tcp elapsedMs=3000" to 24,
            "PROGRESS wait-first-tcp elapsedMs=6000" to 25,
            "wireless startup generation=3 listener=abc first TCP atNs=123" to 26,
            "wireless startup generation=3 listener=abc session established atNs=456" to 27,
        )
        var lit = 0
        for ((line, expected) in samples) {
            val step = CarPlayConnectionProgress.stepIndexFromDebugLog(line)
            assertEquals(expected, step)
            lit = maxOf(lit, step ?: 0)
        }
        assertEquals(27, lit)
    }

    @Test
    fun statusMappingDoesNotRegressOnFailure() {
        assertEquals(4, CarPlayConnectionProgress.stepIndexFromStatus(CarPlayStatus.MfiReady))
        assertNull(CarPlayConnectionProgress.stepIndexFromStatus(CarPlayStatus.Failed("test")))
    }

    @Test
    fun stepCountIsTwentySeven() {
        assertEquals(27, CarPlayConnectionProgress.STEP_COUNT)
    }
}
