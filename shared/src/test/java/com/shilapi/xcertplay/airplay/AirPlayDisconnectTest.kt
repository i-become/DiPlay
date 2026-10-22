package com.shilapi.xcertplay.airplay

import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AirPlayDisconnectTest {
    @Test fun wholeSessionTeardownEndsWithoutWaitingForThePhoneToCloseTcp() =
        assertWholeSessionTeardown(ByteArray(0))

    @Test fun emptyStreamListAlsoEndsTheWholeSession() =
        assertWholeSessionTeardown(BplistCodec.encode(mapOf("streams" to emptyList<Any>())))

    private fun assertWholeSessionTeardown(body: ByteArray) {
        val cleanupEntered = CountDownLatch(1)
        val releaseCleanup = CountDownLatch(1)
        val cleanupFinished = CountDownLatch(1)
        val media = object : AirPlayMediaHandler {
            override fun onSessionClosed(session: AirPlaySession) {
                cleanupEntered.countDown()
                try { releaseCleanup.await(5, TimeUnit.SECONDS) } finally { cleanupFinished.countDown() }
            }
        }
        val fixture = Connection(media)
        try {
            fixture.request("RECORD")
            fixture.session.activeStreams.addAll(listOf(110, 111, 100))
            fixture.request("TEARDOWN", body)
            assertTrue("Disconnect must be published while the phone keeps TCP open", fixture.ended.await(2, TimeUnit.SECONDS))
            assertTrue(cleanupEntered.await(2, TimeUnit.SECONDS))
            assertEquals("The RTSP reply must precede media cleanup and socket close", -1, fixture.phone.getInputStream().read())
            assertEquals(1, fixture.endCount.get())
            assertTrue(fixture.session.isClosed)
        } finally {
            releaseCleanup.countDown()
            fixture.close()
            assertTrue(cleanupFinished.await(2, TimeUnit.SECONDS))
        }
    }

    @Test fun audioStreamTeardownKeepsTheControlSessionUsable() = assertPartialTeardown(100)

    @Test fun screenStreamTeardownAllowsSetupToRecreateTheScreen() = assertPartialTeardown(110)

    @Test fun videoSettingsTeardownDoesNotCloseTheIapTunnelOrSession() = assertPartialTeardown(130, 3L)

    private fun assertPartialTeardown(type: Int, streamId: Long? = null) {
        val tornDown = mutableListOf<Int>()
        Connection(object : AirPlayMediaHandler {
            override fun onTeardown(session: AirPlaySession, type: Int) { tornDown += type }
        }).use { fixture ->
            fixture.session.activeStreams.addAll(listOf(100, 110, 130))
            val stream = linkedMapOf<String, Any>("type" to type)
            if (streamId != null) stream["streamID"] = streamId
            fixture.request("TEARDOWN", BplistCodec.encode(mapOf("streams" to listOf(stream))))
            fixture.request("RECORD")
            assertFalse(fixture.session.isClosed)
            assertEquals(0, fixture.endCount.get())
            assertEquals(if (streamId == null) listOf(type) else emptyList<Int>(), tornDown)
            assertTrue(130 in fixture.session.activeStreams)
        }
    }

    @Test fun socketCloseAndUiNotificationPrecedeSlowMediaCleanup() {
        val event = Socket()
        val ended = AtomicInteger()
        val cleaned = AtomicBoolean()
        val session = session(Socket(), object : AirPlaySessionListener {
            override fun onSessionEnded(session: AirPlaySession) {
                assertTrue(event.isClosed)
                assertFalse(cleaned.get())
                ended.incrementAndGet()
            }
        }, object : AirPlayMediaHandler {
            override fun onSessionClosed(session: AirPlaySession) {
                assertEquals(1, ended.get())
                assertTrue(event.isClosed)
                cleaned.set(true)
                throw IllegalStateException("renderer teardown failure")
            }
        })
        AirPlaySession::class.java.getDeclaredField("eventSocket").apply { isAccessible = true }.set(session, event)
        session.close()
        session.close()
        assertTrue(cleaned.get())
        assertEquals(1, ended.get())
        assertFalse(session.sendCommand(mapOf("type" to "forceKeyFrame")))
    }

    @Test fun failureBeforeTheControlReadLoopStillPublishesDisconnect() {
        val ended = CountDownLatch(1)
        val session = session(Socket(), object : AirPlaySessionListener {
            override fun onSessionEnded(session: AirPlaySession) { ended.countDown() }
        })
        try {
            session.start()
            assertTrue(ended.await(2, TimeUnit.SECONDS))
        } finally { session.close() }
    }

    private inner class Connection(media: AirPlayMediaHandler) : Closeable {
        val phone: Socket
        val ended = CountDownLatch(1)
        val endCount = AtomicInteger()
        val session: AirPlaySession
        private var cseq = 0
        init {
            val accepted = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
                phone = Socket(server.inetAddress, server.localPort).apply { soTimeout = 3_000 }
                server.accept()
            }
            session = session(accepted, object : AirPlaySessionListener {
                override fun onSessionEnded(session: AirPlaySession) {
                    endCount.incrementAndGet()
                    ended.countDown()
                }
            }, media)
            session.start()
        }
        fun request(method: String, body: ByteArray = ByteArray(0)) {
            val head = "$method * RTSP/1.0\r\nCSeq: ${++cseq}\r\nContent-Length: ${body.size}\r\n\r\n"
            phone.getOutputStream().apply { write(head.toByteArray(Charsets.US_ASCII)); write(body); flush() }
            val response = StringBuilder()
            while (!response.endsWith("\r\n\r\n")) {
                val next = phone.getInputStream().read()
                check(next >= 0) { "Peer closed before replying: $response" }
                response.append(next.toChar())
            }
            assertTrue(response.toString(), response.startsWith("RTSP/1.0 200"))
        }
        override fun close() { phone.close(); session.close() }
    }

    private fun session(
        socket: Socket,
        listener: AirPlaySessionListener,
        media: AirPlayMediaHandler = object : AirPlayMediaHandler {},
    ) = AirPlaySession(
        socket, AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1",
            AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480)),
        AirPlayIdentity.generate(), PairingStore(), null, listener, media,
    )
}
