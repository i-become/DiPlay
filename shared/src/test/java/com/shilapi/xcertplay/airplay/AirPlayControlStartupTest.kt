package com.shilapi.xcertplay.airplay

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.Socket
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class AirPlayControlStartupTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    @Test fun disabledProtocolTraceDoesNotEvaluatePayloadFormatting() {
        val logs = mutableListOf<String>()
        session(listener = object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { logs += message }
        }).use { session ->
            session.logTrace { error("Raw payload formatting must not run during normal startup") }
            assertTrue(logs.isEmpty())
        }
    }

    @Test fun explicitProtocolTraceStillSupportsPayloadCapture() {
        val logs = mutableListOf<String>()
        session(listener = object : AirPlaySessionListener {
            override val protocolTraceEnabled = true
            override fun onDebugLog(message: String) { logs += message }
        }).use { session ->
            session.logTrace { "test payload" }
            assertEquals(listOf("TRACE test payload"), logs)
        }
    }

    @Test fun infoResponseRetainsIconAndCapabilitiesWithoutRawTraceAndReportsEncodingTime() {
        val output = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getInputStream() = ByteArrayInputStream("GET /info RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray())
            override fun getOutputStream() = output
        }
        val logs = mutableListOf<String>()
        val icon = ByteArray(12_000) { it.toByte() }
        val config = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1",
            AirPlayDisplayConfig(1920, 720), cluster = AirPlayDisplayConfig(1600, 600),
            icons = listOf(AirPlayIcon(120, 120, icon)))
        session(socket, config, object : AirPlaySessionListener {
            override fun onDebugLog(message: String) { logs += message }
        }).use { session ->
            session.javaClass.getDeclaredMethod("runControl").apply { isAccessible = true }.invoke(session)
            val response = RtspMessage.parseMessages(output.toByteArray()).messages.single()
            assertEquals("200", response.path)
            assertEquals("1", response.headers["cseq"])
            val info = BplistCodec.decode(response.body) as Map<*, *>
            assertEquals(2, (info["displays"] as List<*>).size)
            val oemIcon = (info["oemIcons"] as List<*>).single() as Map<*, *>
            assertArrayEquals(icon, oemIcon["imageData"] as ByteArray)
            assertTrue(info.containsKey("audioFormats"))
            assertTrue(info.containsKey("hidDevices"))
            assertFalse(logs.any { it.startsWith("TRACE ") })
            val timing = logs.single { it.startsWith("airplay info timing ") }
            for (field in listOf("buildMs", "encodeMs")) {
                assertTrue(timing.substringAfter("$field=").substringBefore(' ').toLong() >= 0)
            }
            assertTrue(timing.endsWith("responseBytes=${response.body.size}"))
        }
    }

    private fun session(
        socket: Socket = Socket(),
        config: AirPlayConfig = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(800, 480)),
        listener: AirPlaySessionListener,
    ) = AirPlaySession(socket, config, AirPlayIdentity.generate(), PairingStore(), null, listener,
        object : AirPlayMediaHandler {})

    @Test fun pipelinedResponseIsSentBeforeProcessingTheNextRequestAndTimingIsReported() {
        val input = ByteArrayInputStream((
            "OPTIONS * RTSP/1.0\r\nCSeq: 1\r\n\r\n" +
                "RECORD * RTSP/1.0\r\nCSeq: 2\r\n\r\n"
            ).toByteArray(Charsets.US_ASCII))
        val output = ByteArrayOutputStream()
        val socket = object : Socket() {
            override fun getInputStream() = input
            override fun getOutputStream() = output
        }
        val logs = mutableListOf<String>()
        var active = false
        val session = AirPlaySession(
            socket = socket,
            config = AirPlayConfig("test", "02:00:00:00:00:02", "02:00:00:00:00:01", "1", AirPlayDisplayConfig(800, 480)),
            identity = AirPlayIdentity.generate(), pairings = PairingStore(), mfi = null,
            listener = object : AirPlaySessionListener {
                override fun onSessionActive(session: AirPlaySession) {
                    active = true
                    val sent = output.toString("US-ASCII")
                    assertTrue("The previous response must already be on the wire", sent.contains("CSeq: 1\r\n"))
                    assertFalse(sent.contains("CSeq: 2\r\n"))
                }
                override fun onDebugLog(message: String) { logs += message }
            },
            media = object : AirPlayMediaHandler {},
        )
        try {
            session.javaClass.getDeclaredMethod("runControl").apply { isAccessible = true }.invoke(session)
            assertTrue(active)
            assertTrue(output.toString("US-ASCII").contains("CSeq: 2\r\n"))
            val timings = logs.filter { it.startsWith("airplay control timing ") }
            assertEquals(2, timings.size)
            assertTrue(timings[0].contains("method=OPTIONS route=other request=1 status=200"))
            assertTrue(timings[1].contains("method=RECORD route=other request=2 status=200"))
            for (timing in timings) for (field in listOf("elapsedMs", "gapMs", "processingMs", "writeMs")) {
                assertTrue(timing.substringAfter("$field=").substringBefore(' ').toLong() >= 0)
            }
        } finally { session.close() }
    }
}
