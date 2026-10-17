package com.shilapi.xcertplay.iap2.message

import org.junit.Assert.assertEquals
import org.junit.Test

class Iap2CommunicationsMessagesTest {
    @Test fun acceptAndEndCallUseCommunicationsMessageIds() {
        assertEquals(0x415B, Iap2CommunicationsMessages.acceptCall().messageId)
        assertEquals(0x415C, Iap2CommunicationsMessages.endCall().messageId)
    }
}
