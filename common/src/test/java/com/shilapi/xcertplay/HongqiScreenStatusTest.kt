package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.mockito.Mockito.mock
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.media.AndroidMediaSink

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HongqiScreenStatusTest {
    @Test fun vehicleAccOffStopsRetainedSessionAndUnknownStatusCannotReenableConnection() {
        val type = Class.forName("com.shilapi.xcertplay.HongqiScreenStatus\$Connection")
        val source = type.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
        val connection = HongqiScreenStatus::class.java.getDeclaredField("connection").apply { isAccessible = true }
        val raw = HongqiScreenStatus::class.java.getDeclaredField("raw").apply { isAccessible = true }
        val off = HongqiScreenStatus::class.java.getDeclaredField("screenOff").apply { isAccessible = true }
        val accept = HongqiScreenStatus::class.java.getDeclaredMethod("accept", type, Int::class.javaPrimitiveType).apply { isAccessible = true }
        val previousConnection = connection.get(HongqiScreenStatus)
        val previousRaw = raw.get(HongqiScreenStatus)
        val previousOff = off.getBoolean(HongqiScreenStatus)
        var stops = 0
        try {
            connection.set(HongqiScreenStatus, source)
            raw.set(HongqiScreenStatus, null)
            val controller = mock(CarPlayController::class.java)
            CarPlayBackgroundSession.store(controller, mock(AndroidMediaSink::class.java), 1920, 720,
                this, CarPlaySessionDisplay(1920, 720, 0, true, true, 1920, 720)) { complete ->
                stops++
                CarPlayBackgroundSession.clear(controller)
                complete()
            }
            CarPlayBackgroundSession.active = true
            accept.invoke(HongqiScreenStatus, source, 4)
            assertEquals(1, stops)
            assertTrue(HongqiScreenStatus.screenOff)
            assertFalse(CarPlayBackgroundSession.active)
            assertNull(CarPlayBackgroundSession.snapshot())
            assertTrue(ConnectionProgressUiState.disconnected())
            accept.invoke(HongqiScreenStatus, source, 0)
            assertTrue(HongqiScreenStatus.screenOff)
            accept.invoke(HongqiScreenStatus, source, 1)
            assertFalse(HongqiScreenStatus.screenOff)
            assertEquals(1, stops)
        } finally {
            CarPlayBackgroundSession.clear()
            ConnectionProgressUiState.reset()
            connection.set(HongqiScreenStatus, previousConnection)
            raw.set(HongqiScreenStatus, previousRaw)
            off.setBoolean(HongqiScreenStatus, previousOff)
        }
    }

    @Test fun accOffInVehicleReportBlocksConnectionUntilExplicitWake() {
        assertEquals(true, HongqiScreenStatus.offState(4))
        assertEquals(false, HongqiScreenStatus.offState(1))
    }

    @Test fun screenAndPowerOffAlsoBlockButUnknownStatesCannotClearTheGate() {
        assertEquals(true, HongqiScreenStatus.offState(2))
        assertEquals(true, HongqiScreenStatus.offState(3))
        listOf(0, 7, 8, 9, 10, 11, -1).forEach { assertNull(HongqiScreenStatus.offState(it)) }
    }
}
