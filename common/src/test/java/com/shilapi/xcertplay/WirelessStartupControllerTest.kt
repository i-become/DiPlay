package com.shilapi.xcertplay

import android.content.ComponentName
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Looper
import com.shilapi.xcertplay.airplay.*
import com.shilapi.xcertplay.network.*
import com.shilapi.xcertplay.orchestration.*
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WirelessStartupControllerTest {
    private val statuses = mutableListOf<CarPlayStatus>()
    private fun controller(context: Context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun getApplicationContext(): Context = this
        override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean = false
    }, phoneAddress: String? = null) = CarPlayController(context,
        CarPlayRuntimeConfig(mfiTarget = MfiTarget.LOCAL, transport = CarPlayTransport.WIRELESS,
            wirelessBluetoothDeviceAddress = phoneAddress,
            identification = Iap2IdentificationConfig(name = "test", modelIdentifier = "test", manufacturer = "test",
                serialNumber = "test", firmwareVersion = "1", hardwareVersion = "1", carPlayUsbInterfaceNumber = 3)),
        AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(800, 480)),
        AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
        object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, statuses::add)

    @After fun clearBootPreconnect() { BluetoothBootPreconnect.cancel() }

    @Test fun controllerReusesProcessStartSocketWithoutDiallingAgain() {
        val socket = mock(BluetoothSocket::class.java)
        BluetoothBootPreconnect.schedule("02:11:22:33:44:66", { socket })
        val controller = controller(phoneAddress = "02:11:22:33:44:66")
        try {
            earlyBluetooth(controller, socket).get(3, TimeUnit.SECONDS)
            assertSame(socket, ReflectionHelpers.getField(controller, "bluetoothSocket"))
            verify(socket, times(1)).connect()
        } finally { controller.close(); controller.awaitClosed(3_000) }
        verify(socket, times(1)).close()
    }

    @Test fun failedBootDialFallsBackToTheNormalControllerConnection() {
        val failed = mock(BluetoothSocket::class.java)
        doThrow(IOException("Boot radio not ready")).`when`(failed).connect()
        BluetoothBootPreconnect.schedule("02:11:22:33:44:66", { failed })
        val socket = mock(BluetoothSocket::class.java)
        val controller = controller(phoneAddress = "02:11:22:33:44:66")
        try {
            earlyBluetooth(controller, socket).get(3, TimeUnit.SECONDS)
            assertSame(socket, ReflectionHelpers.getField(controller, "bluetoothSocket"))
            verify(socket, times(1)).connect()
            verify(failed, times(1)).close()
        } finally { controller.close(); controller.awaitClosed(3_000) }
    }

    @Test fun closingControllerCancelsAnAdoptedBootConnectionStillBeingDialled() {
        val socket = mock(BluetoothSocket::class.java)
        val connecting = CountDownLatch(1)
        val closed = CountDownLatch(1)
        doAnswer {
            connecting.countDown()
            closed.await(3, TimeUnit.SECONDS)
            throw IOException("Closed")
        }.`when`(socket).connect()
        doAnswer { closed.countDown(); null }.`when`(socket).close()
        BluetoothBootPreconnect.schedule("02:11:22:33:44:66", { socket })
        val controller = controller(phoneAddress = "02:11:22:33:44:66")
        try {
            assertTrue(connecting.await(3, TimeUnit.SECONDS))
            val pending = earlyBluetooth(controller, socket)
            assertNull(BluetoothBootPreconnect.take("02:11:22:33:44:66"))
            controller.close()
            assertTrue(controller.awaitClosed(3_000))
            assertTrue(pending.isCancelled)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
            verify(socket, times(1)).close()
        } finally { controller.close(); controller.awaitClosed(3_000); closed.countDown() }
    }

    private fun fail(controller: CarPlayController, generation: Int, error: Throwable) {
        controller.javaClass.getDeclaredMethod("fail", Throwable::class.java, Int::class.javaObjectType)
            .apply { isAccessible = true }.invoke(controller, error, generation)
    }

    private fun earlyBluetooth(controller: CarPlayController, socket: BluetoothSocket): FutureTask<*> {
        val adapter = mock(BluetoothAdapter::class.java)
        val device = mock(BluetoothDevice::class.java)
        `when`(adapter.isEnabled).thenReturn(true)
        `when`(adapter.state).thenReturn(BluetoothAdapter.STATE_ON)
        `when`(adapter.address).thenReturn("02:11:22:33:44:55")
        `when`(adapter.bondedDevices).thenReturn(setOf(device))
        `when`(device.address).thenReturn("02:11:22:33:44:66")
        `when`(device.name).thenReturn("Saved phone")
        `when`(device.createRfcommSocketToServiceRecord(any(java.util.UUID::class.java))).thenReturn(socket)
        ReflectionHelpers.setField(controller, "bluetoothAdapter", adapter)
        val phase = Class.forName("com.shilapi.xcertplay.orchestration.CarPlayController\$Phase")
            .enumConstants.first { (it as Enum<*>).name == "WIRELESS" }
        ReflectionHelpers.setField(controller, "phase", phase)
        return controller.javaClass.getDeclaredMethod("startWirelessPreparation", Int::class.javaPrimitiveType)
            .apply { isAccessible = true }.invoke(controller, 0) as FutureTask<*>
    }

    @Test fun bluetoothSocketConnectsDuringPreparationBeforeAnyHotspotOrListenerExists() {
        val controller = controller()
        val socket = mock(BluetoothSocket::class.java)
        try {
            val prepared = earlyBluetooth(controller, socket).get(3, TimeUnit.SECONDS)
            assertNotNull(ReflectionHelpers.getField<Any>(prepared, "connection"))
            assertNull(ReflectionHelpers.getField<Any?>(controller, "hotspot"))
            assertNull(ReflectionHelpers.getField<Any?>(controller, "vpnService"))
            assertSame(socket, ReflectionHelpers.getField(controller, "bluetoothSocket"))
            verify(socket, times(1)).connect()
        } finally { controller.close(); controller.awaitClosed(3_000) }
        verify(socket, times(1)).close()
    }

    @Test fun closingDuringEarlyConnectCancelsPreparationAndClosesItsSocket() {
        val controller = controller()
        val socket = mock(BluetoothSocket::class.java)
        val connecting = CountDownLatch(1)
        val closed = CountDownLatch(1)
        doAnswer {
            connecting.countDown()
            if (!closed.await(3, TimeUnit.SECONDS)) throw IOException("Test socket was not closed")
            throw IOException("Closed")
        }.`when`(socket).connect()
        doAnswer { closed.countDown(); null }.`when`(socket).close()
        try {
            val pending = earlyBluetooth(controller, socket)
            assertTrue(connecting.await(3, TimeUnit.SECONDS))
            controller.close()
            assertTrue(controller.awaitClosed(3_000))
            assertTrue(pending.isCancelled)
            assertTrue(closed.await(1, TimeUnit.SECONDS))
        } finally { controller.close(); controller.awaitClosed(3_000); closed.countDown() }
    }

    @Test fun timeoutAndCleanupExceptionProduceOnlyOneTypedFailure() {
        val controller = controller()
        try {
            fail(controller, 0, WirelessStartupException(WirelessStartupFailure.FIRST_TCP_TIMEOUT, "timeout"))
            fail(controller, 0, IOException("Bluetooth socket closed"))
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, statuses.size)
            assertEquals(WirelessStartupFailure.FIRST_TCP_TIMEOUT, (statuses.single() as CarPlayStatus.Failed).startupFailure)
        } finally { controller.close(); controller.awaitClosed(2_000) }
    }

    @Test fun queuedOldFailureAndOldCleanupDoNotAffectNewGeneration() {
        val controller = controller()
        val preparation = FutureTask { Unit }
        var closed = 0
        val hotspot = object : WirelessHotspotManager {
            override fun start(timeoutMillis: Long): WirelessHotspotInfo = error("Not started")
            override fun close() { closed++ }
        }
        try {
            fail(controller, 0, WirelessStartupException(WirelessStartupFailure.FIRST_TCP_TIMEOUT, "timeout"))
            ReflectionHelpers.getField<AtomicInteger>(controller, "wirelessGeneration").set(1)
            ReflectionHelpers.setField(controller, "hotspot", hotspot)
            ReflectionHelpers.setField(controller, "bluetoothPreparation", preparation)
            controller.javaClass.getDeclaredMethod("closeWirelessStack", CarPlayVpnService::class.java, Int::class.javaObjectType)
                .apply { isAccessible = true }.invoke(controller, null, 0)
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(statuses.isEmpty())
            assertEquals(0, closed)
            assertFalse("Old cleanup must not cancel new Bluetooth preparation", preparation.isCancelled)
            assertSame(hotspot, ReflectionHelpers.getField(controller, "hotspot"))
            fail(controller, 0, IOException("late old failure"))
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(statuses.isEmpty())
        } finally { controller.close(); controller.awaitClosed(2_000) }
        assertTrue("Closing must cancel pending Bluetooth preparation", preparation.isCancelled)
    }

    @Test fun earlyServiceBindingIsReusedAndLateCallbackAfterCloseIsIgnored() {
        var bindings = 0
        var unbindings = 0
        lateinit var connection: ServiceConnection
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun getApplicationContext(): Context = this
            override fun bindService(service: Intent, conn: ServiceConnection, flags: Int): Boolean {
                bindings++
                if (bindings == 1) return false // Service unavailable during early boot.
                connection = conn
                return true
            }
            override fun unbindService(conn: ServiceConnection) { unbindings++ }
        }
        val controller = controller(context)
        val service = Robolectric.buildService(CarPlayVpnService::class.java).create()
        val bind = controller.javaClass.getDeclaredMethod("bindVpn").apply { isAccessible = true }
        try {
            bind.invoke(controller)
            bind.invoke(controller)
            bind.invoke(controller)
            assertEquals(2, bindings)
            assertEquals("Retry must wait for its own service callback", 1L,
                ReflectionHelpers.getField<CountDownLatch>(controller, "vpnLatch").count)
            connection.onServiceConnected(ComponentName(context, CarPlayVpnService::class.java), service.get().onBind(Intent()))
            assertSame(service.get(), ReflectionHelpers.getField(controller, "vpnService"))
            controller.close()
            assertEquals(1, unbindings)
            connection.onServiceConnected(ComponentName(context, CarPlayVpnService::class.java), service.get().onBind(Intent()))
            assertNull(ReflectionHelpers.getField<Any?>(controller, "vpnService"))
            bind.invoke(controller)
            assertEquals("A closed controller cannot bind again", 2, bindings)
        } finally {
            controller.close()
            controller.awaitClosed(2_000)
            service.destroy()
        }
    }
}
