package com.shilapi.xcertplay.network

import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.RandomAccessFile
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 29], manifest = Config.NONE)
class TcpLivenessTest {
    @Before fun resetOptions() {
        SocketOptions.descriptors.clear()
        SocketOptions.options.clear()
        SocketOptions.failAtOption = null
    }

    @Test fun configuringPeerDetectionDoesNotCloseTheLiveSocketOnLegacyOrModernAndroid() {
        withConnectedSockets { phone, accessory ->
            val diagnostics = mutableListOf<String>()
            TcpLiveness.configure(accessory, diagnostics::add)
            assertTrue(accessory.keepAlive)
            assertTrue(diagnostics.isNotEmpty())
            assertBidirectionalTraffic(phone, accessory)
        }
    }

    @Test
    @Config(sdk = [28], shadows = [SocketOptions::class])
    fun successfulLegacyTuningNeverInvalidatesTheBorrowedDescriptor() {
        withBorrowedDescriptor { descriptor, owner ->
            withConnectedSockets { phone, accessory ->
                val diagnostics = mutableListOf<String>()
                TcpLiveness.configure(accessory, { socket ->
                    assertSame(accessory, socket)
                    descriptor
                }, diagnostics::add)

                assertEquals(listOf(4 to 10, 5 to 3, 6 to 3, OsConstants.TCP_USER_TIMEOUT to 20_000),
                    SocketOptions.options)
                assertTrue(SocketOptions.descriptors.all { it === descriptor })
                assertTrue(diagnostics.single().startsWith("TCP peer health enabled"))
                assertTrue("Borrowing a descriptor must not close or detach it", descriptor.valid())
                owner.write(42)
                owner.seek(0)
                assertEquals(42, owner.read())
                assertBidirectionalTraffic(phone, accessory)
            }
        }
    }

    @Test
    @Config(sdk = [28], shadows = [SocketOptions::class])
    fun unsupportedLegacyOptionsPreserveTheDescriptorAndTheConnection() {
        withBorrowedDescriptor { descriptor, owner ->
            withConnectedSockets { phone, accessory ->
                SocketOptions.failAtOption = OsConstants.TCP_USER_TIMEOUT
                val diagnostics = mutableListOf<String>()
                TcpLiveness.configure(accessory, { descriptor }, diagnostics::add)

                assertEquals(4, SocketOptions.options.size)
                assertTrue(diagnostics.single().contains("unavailable: ErrnoException"))
                assertTrue(descriptor.valid())
                owner.write(17)
                assertBidirectionalTraffic(phone, accessory)
            }
        }
    }

    @Test
    @Config(sdk = [28], shadows = [SocketOptions::class])
    fun deniedLegacyDescriptorAccessLeavesBasicKeepaliveAndTrafficWorking() {
        withConnectedSockets { phone, accessory ->
            val diagnostics = mutableListOf<String>()
            TcpLiveness.configure(accessory, { throw SecurityException("Firmware denies access") }, diagnostics::add)

            assertTrue(accessory.keepAlive)
            assertTrue(SocketOptions.options.isEmpty())
            assertTrue(diagnostics.single().contains("unavailable: SecurityException"))
            assertBidirectionalTraffic(phone, accessory)
        }
    }

    private fun withConnectedSockets(test: (Socket, Socket) -> Unit) {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            Socket(server.inetAddress, server.localPort).use { phone ->
                server.accept().use { accessory ->
                    test(phone, accessory)
                }
            }
        }
    }

    private fun assertBidirectionalTraffic(phone: Socket, accessory: Socket) {
        assertFalse(accessory.isClosed)
        accessory.soTimeout = 1_000
        phone.getOutputStream().write(42)
        assertEquals(42, accessory.getInputStream().read())
        accessory.getOutputStream().write(17)
        phone.soTimeout = 1_000
        assertEquals(17, phone.getInputStream().read())
    }

    private fun withBorrowedDescriptor(test: (FileDescriptor, RandomAccessFile) -> Unit) {
        val file = File.createTempFile("tcp-liveness", ".test")
        try {
            RandomAccessFile(file, "rw").use { owner -> test(owner.fd, owner) }
        } finally {
            file.delete()
        }
    }

    @Implements(Os::class)
    class SocketOptions {
        companion object {
            val descriptors = mutableListOf<FileDescriptor>()
            val options = mutableListOf<Pair<Int, Int>>()
            var failAtOption: Int? = null

            @Implementation @JvmStatic
            fun setsockoptInt(fd: FileDescriptor, level: Int, option: Int, value: Int) {
                assertEquals(OsConstants.IPPROTO_TCP, level)
                assertTrue(fd.valid())
                descriptors += fd
                options += option to value
                if (option == failAtOption) throw ErrnoException("setsockoptInt", OsConstants.ENOPROTOOPT)
            }
        }
    }
}
