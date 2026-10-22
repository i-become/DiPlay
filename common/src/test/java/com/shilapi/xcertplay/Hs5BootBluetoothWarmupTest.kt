package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.shilapi.xcertplay.orchestration.BluetoothBootPreconnect
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5BootBluetoothWarmupTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val app get() = RuntimeEnvironment.getApplication()
    private val address = "02:11:22:33:44:66"
    private lateinit var socket: BluetoothSocket

    @Before fun setup() {
        CarPlayBackgroundSession.clear()
        BluetoothBootPreconnect.cancel()
        ReflectionHelpers.getField<AtomicBoolean>(Hs5BootBluetoothWarmup, "started").set(false)
        app.getSharedPreferences("diplay", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("xcertplay_airplay", Context.MODE_PRIVATE).edit().clear().commit()
        ShadowBuild.setManufacturer("HONG QI")
        ShadowBuild.setModel("HS5")
        DiPlayPreferences.saveAutoConnect(app, true)
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.WIRELESS)
        DiPlayPreferences.savePhone(app, address, "Saved phone")
        val adapter = BluetoothAdapter.getDefaultAdapter()
        shadowOf(adapter).setEnabled(true)
        val device = mock(BluetoothDevice::class.java)
        socket = mock(BluetoothSocket::class.java)
        `when`(device.address).thenReturn(address)
        `when`(device.createRfcommSocketToServiceRecord(any(UUID::class.java))).thenReturn(socket)
        shadowOf(adapter).setBondedDevices(setOf(device))
    }

    @After fun cleanup() {
        BluetoothBootPreconnect.cancel()
        CarPlayBackgroundSession.clear()
        ReflectionHelpers.getField<AtomicBoolean>(Hs5BootBluetoothWarmup, "started").set(false)
    }

    @Test fun savedBondedPhoneIsDialledWithoutStartingAnyActivityOrDiscovery() {
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        val connection = BluetoothBootPreconnect.take(address)!!.awaitAndTake()
        try {
            assertSame(socket, connection.socket)
            verify(socket, times(1)).connect()
            assertFalse(BluetoothAdapter.getDefaultAdapter().isDiscovering)
            assertNull(shadowOf(app).nextStartedActivity)
            assertTrue(CarPlayStartupDiagnostics.report().contains("bluetooth-connected="))
        } finally { connection.socket.close() }
    }

    @Test fun disabledAutoConnectDoesNotDialTheSavedPhone() {
        DiPlayPreferences.saveAutoConnect(app, false)
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        assertNull(BluetoothBootPreconnect.take(address))
        verify(socket, never()).connect()
    }

    @Test fun wiredDefaultDoesNotStartWirelessBluetooth() {
        DiPlayPreferences.saveDefaultConnectionMode(app, DefaultConnectionMode.USB)
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        assertNull(BluetoothBootPreconnect.take(address))
    }

    @Test fun aPhoneThatIsNoLongerBondedIsNotDialled() {
        shadowOf(BluetoothAdapter.getDefaultAdapter()).setBondedDevices(emptySet())
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        val attempt = BluetoothBootPreconnect.take(address)!!
        try { assertThrows(IOException::class.java) { attempt.awaitAndTake() } }
        finally { attempt.close() }
        verify(socket, never()).connect()
    }

    @Test fun stoppingBeforeTheControllerStartsCancelsTheBootConnection() {
        Hs5BootBluetoothWarmup.scheduleIfNeeded(app)
        CarPlayBackgroundSession.stop()
        assertNull(BluetoothBootPreconnect.take(address))
    }
}
