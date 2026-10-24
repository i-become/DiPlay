package com.shilapi.xcertplay.airplay

import java.io.IOException
import java.io.OutputStream
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AirPlaySiriTest {
    @Test fun successMeansBothPressAndReleaseWereWrittenToTheEncryptedEventChannel() {
        val event = EventSocket()
        session(event).use { session ->
            assertTrue(session.invokeSiri())
            assertEquals(2, event.writes.size)
            val receiver = ControlCipher(ByteArray(32), ByteArray(32))
            val actions = event.writes.mapIndexed { index, encrypted ->
                val decrypted = receiver.decrypt(encrypted)
                assertEquals(0, decrypted.rest.size)
                val text = decrypted.data.toString(Charsets.ISO_8859_1)
                assertTrue(text.startsWith("POST /command RTSP/1.0\r\n"))
                assertTrue(text.contains("CSeq: ${index + 1}\r\n"))
                val bodyStart = text.indexOf("\r\n\r\n") + 4
                val command = BplistCodec.decode(decrypted.data.copyOfRange(bodyStart, decrypted.data.size)) as Map<*, *>
                assertEquals("requestSiri", command["type"])
                ((command["params"] as Map<*, *>)["siriAction"] as Number).toInt()
            }
            assertEquals(listOf(2, 3), actions)
            assertFalse(session.isClosed)
        }
    }

    @Test fun anEventChannelThatIsNotReadyDoesNotReportSuccess() {
        session().use { session ->
            assertFalse(session.invokeSiri())
            assertFalse(session.isClosed)
        }
    }

    @Test fun failureWritingThePressClosesTheBrokenChannelAndDoesNotReportSuccess() {
        val event = EventSocket(failOnWrite = 1)
        session(event).use { session ->
            assertFalse(session.invokeSiri())
            assertEquals(0, event.writes.size)
            assertTrue(session.isClosed)
            assertTrue(event.isClosed)
        }
    }

    @Test fun failureWritingTheReleaseDoesNotReportThePressAloneAsSuccess() {
        val event = EventSocket(failOnWrite = 2)
        session(event).use { session ->
            assertFalse(session.invokeSiri())
            assertEquals(1, event.writes.size)
            assertTrue(session.isClosed)
            assertTrue(event.isClosed)
        }
    }

    @Test fun aClosedSessionCannotWriteAnotherSiriRequest() {
        val event = EventSocket()
        val session = session(event)
        session.close()
        assertFalse(session.invokeSiri())
        assertTrue(event.writes.isEmpty())
    }

    private fun session(event: Socket? = null): AirPlaySession = AirPlaySession(
        Socket(),
        AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1",
            AirPlayDisplayConfig(widthPixels = 800, heightPixels = 480)),
        AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(), null,
        object : AirPlaySessionListener {}, object : AirPlayMediaHandler {},
    ).apply {
        if (event != null) {
            AirPlaySession::class.java.getDeclaredField("eventSocket").apply { isAccessible = true }.set(this, event)
            AirPlaySession::class.java.getDeclaredField("eventCipher").apply { isAccessible = true }
                .set(this, ControlCipher(ByteArray(32), ByteArray(32)))
        }
    }

    private class EventSocket(private val failOnWrite: Int = 0) : Socket() {
        val writes = mutableListOf<ByteArray>()
        private var attemptedWrites = 0
        private val output = object : OutputStream() {
            override fun write(value: Int) { error("Expected a complete encrypted write") }
            override fun write(bytes: ByteArray) {
                if (++attemptedWrites == failOnWrite) throw IOException("Event channel closed by phone")
                writes += bytes.copyOf()
            }
        }
        override fun getOutputStream(): OutputStream = output
    }
}
