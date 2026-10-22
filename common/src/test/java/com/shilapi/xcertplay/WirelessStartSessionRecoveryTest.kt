package com.shilapi.xcertplay

import com.shilapi.xcertplay.iap2.session.Iap2Session
import com.shilapi.xcertplay.iap2.wire.Iap2Frame
import com.shilapi.xcertplay.mfi.Iap2MfiAuthenticationClient
import com.shilapi.xcertplay.transport.*
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Answers
import org.mockito.Mockito.*

class WirelessStartSessionRecoveryTest {
    private val identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1",
        wireless = Iap2WirelessIdentification("02:11:22:33:44:55", "test"))
    private val endpoint = Iap2WirelessCarPlayEndpoint("test", "test1234", 0,
        Iap2WirelessSecurity.WPA_WPA2,
        listOf("192.0.2.1"), 7000, "test", "test", "1")

    @Test fun recoveryReusesTheAuthenticatedChannelAndWritesStartBeforeReportingItsEvent() {
        val events = mutableListOf<String>()
        val frames = mutableListOf<Iap2Frame>()
        val session = mock(Iap2Session::class.java) { invocation ->
            if (invocation.method.name == "send" && invocation.arguments.firstOrNull() is Iap2Frame) {
                val frame = invocation.getArgument<Iap2Frame>(0)
                frames.add(frame)
                if (frame.messageId == 0x4301) events.add("write")
            }
            Answers.RETURNS_DEFAULTS.answer(invocation)
        }
        val mfi = mock(Iap2MfiAuthenticationClient::class.java)
        `when`(session.recv(anyLong())).thenReturn(Iap2Frame(0x4300, byteArrayOf()), null)
        `when`(session.isClosed).thenReturn(true)
        mockConstruction(Iap2IdentificationClient::class.java).use {
            val result = Iap2WirelessControlClient(session, mfi).run(
                identification, endpoint,
                beforeStartSession = { events.add("validate") },
                onStartSessionSent = { events.add("sent") },
                retryStartSession = { events.add("discovery"); true },
            )
            assertEquals(2, result.carPlayStartSessionsSent)
            assertEquals(listOf("validate", "write", "sent", "discovery", "validate", "write", "sent"), events)
            verify(session, never()).close()
            verify(session, atLeastOnce()).recv(500L)
            assertEquals(2, frames.count { frame -> frame.messageId == 0x4301 })
            assertEquals(0, frames.count { frame -> frame.messageId == 0x5703 })
        }
    }

    @Test fun noRecoveryIsRequestedBeforeTheFirstStartOrAfterTheCallerReportsTcp() {
        for (sendInitialStart in listOf(false, true)) {
            val session = mock(Iap2Session::class.java)
            `when`(session.recv(anyLong())).thenReturn(
                if (sendInitialStart) Iap2Frame(0x4300, byteArrayOf()) else null, null)
            `when`(session.isClosed).thenReturn(true)
            var checks = 0
            mockConstruction(Iap2IdentificationClient::class.java).use {
                val result = Iap2WirelessControlClient(session, mock(Iap2MfiAuthenticationClient::class.java)).run(
                    identification, endpoint, retryStartSession = { checks++; false },
                )
                assertEquals(if (sendInitialStart) 1 else 0, result.carPlayStartSessionsSent)
                assertEquals(if (sendInitialStart) 1 else 0, checks)
            }
        }
    }
}
