package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class AirPlayControlDiagnosticsTest {
    @Test fun identifiesNegotiationWithoutRequestHeadersQueriesOrBodyContents() {
        assertEquals("airplay control request method=POST route=pair-verify contentBytes=128",
            AirPlayControlDiagnostics.request("POST", "rtsp://192.168.49.1/pair-verify?token=secret", 128))
        assertEquals("airplay control request method=SETUP route=other contentBytes=64",
            AirPlayControlDiagnostics.request("SETUP", "rtsp://192.168.49.1/private-session", 64))
        assertEquals("airplay control request method=OTHER route=other contentBytes=0",
            AirPlayControlDiagnostics.request("Jane's iPhone\npassword=secret", "/private-secret", 0))
    }

    @Test fun separatesRequestGapsFromLocalHandlingAndReplyTimesWithoutExportingPeerData() {
        assertEquals(
            "airplay control timing method=POST route=pair-verify request=2 status=200 elapsedMs=2500 gapMs=2100 processingMs=350 writeMs=50",
            AirPlayControlDiagnostics.timing("POST", "rtsp://192.168.49.1/pair-verify?token=secret", 2, 200,
                2500, 2100, 350, 50),
        )
        val line = AirPlayControlDiagnostics.timing("private\npassword=secret", "/Jane's iPhone", 3, 500, 2600, 80, 20, 0)
        assertTrue(line.contains("method=OTHER route=other"))
        assertFalse(line.contains("secret"))
        assertFalse(line.contains("iPhone"))
    }
}
