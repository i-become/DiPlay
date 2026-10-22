package com.shilapi.xcertplay

import android.bluetooth.BluetoothSocket
import com.shilapi.xcertplay.orchestration.BluetoothBootPreconnect
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class BluetoothBootPreconnectTest {
    private val address = "02:11:22:33:44:55"

    @After fun cleanup() { BluetoothBootPreconnect.cancel() }

    @Test fun repeatedWarmupDialsOnceAndTransfersOwnershipToTheController() {
        val socket = mock(BluetoothSocket::class.java)
        assertTrue(BluetoothBootPreconnect.schedule(address, { socket }))
        assertFalse(BluetoothBootPreconnect.schedule(address, { error("Duplicate connection") }))
        val attempt = BluetoothBootPreconnect.take(address)!!
        val connection = attempt.awaitAndTake()
        assertSame(socket, connection.socket)
        attempt.close()
        BluetoothBootPreconnect.cancel()
        verify(socket, times(1)).connect()
        verify(socket, never()).close()
        assertNull(BluetoothBootPreconnect.take(address))
        socket.close()
    }

    @Test fun aDifferentPhoneCannotClaimTheOldConnection() {
        val socket = mock(BluetoothSocket::class.java)
        val connecting = CountDownLatch(1)
        doAnswer { connecting.countDown(); null }.`when`(socket).connect()
        BluetoothBootPreconnect.schedule(address, { socket })
        assertTrue(connecting.await(3, TimeUnit.SECONDS))
        assertNull(BluetoothBootPreconnect.take("02:11:22:33:44:66"))
        verify(socket).close()
    }

    @Test fun closingAnInFlightAttemptClosesItsSocketAndUnblocksTheDial() {
        val socket = mock(BluetoothSocket::class.java)
        val connecting = CountDownLatch(1)
        val closed = CountDownLatch(1)
        doAnswer {
            connecting.countDown()
            closed.await(3, TimeUnit.SECONDS)
            throw IOException("Closed")
        }.`when`(socket).connect()
        doAnswer { closed.countDown(); null }.`when`(socket).close()
        BluetoothBootPreconnect.schedule(address, { socket })
        assertTrue(connecting.await(3, TimeUnit.SECONDS))
        val attempt = BluetoothBootPreconnect.take(address)!!
        attempt.close()
        assertTrue(closed.await(1, TimeUnit.SECONDS))
        assertThrows(IOException::class.java) { attempt.awaitAndTake() }
    }

    @Test fun lateSocketCreatedAfterCancellationIsClosedWithoutDialling() {
        val socket = mock(BluetoothSocket::class.java)
        val preparing = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CountDownLatch(1)
        doAnswer { closed.countDown(); null }.`when`(socket).close()
        BluetoothBootPreconnect.schedule(address, {
            preparing.countDown()
            while (true) {
                try { release.await(); break } catch (_: InterruptedException) { /* Simulate a slow OEM call. */ }
            }
            socket
        })
        try {
            assertTrue(preparing.await(3, TimeUnit.SECONDS))
            BluetoothBootPreconnect.cancel()
            release.countDown()
            assertTrue(closed.await(3, TimeUnit.SECONDS))
            verify(socket, never()).connect()
        } finally { release.countDown() }
    }

    @Test fun failedDialClosesTheSocketAndReportsFailureForNormalRetry() {
        val events = CopyOnWriteArrayList<String>()
        val socket = mock(BluetoothSocket::class.java)
        doThrow(IOException("Phone unavailable")).`when`(socket).connect()
        BluetoothBootPreconnect.schedule(address, { socket }, { events.add(it) })
        val attempt = BluetoothBootPreconnect.take(address)!!
        assertThrows(IOException::class.java) { attempt.awaitAndTake() }
        attempt.close()
        verify(socket).close()
        assertTrue(events.any { it.contains("failed failureClass=IOException stage=rfcomm-connect") })
    }

    @Test fun preparingFailureCanBeDistinguishedFromASocketDialFailure() {
        val events = CopyOnWriteArrayList<String>()
        BluetoothBootPreconnect.schedule(address, { throw IOException("Radio unavailable") }, { events.add(it) })
        val attempt = BluetoothBootPreconnect.take(address)!!
        assertThrows(IOException::class.java) { attempt.awaitAndTake() }
        assertTrue(events.any { it.contains("failed failureClass=IOException stage=prepare-socket") })
        assertTrue(events.any { it.startsWith("handoff-wait ") })
        attempt.close()
    }
}
