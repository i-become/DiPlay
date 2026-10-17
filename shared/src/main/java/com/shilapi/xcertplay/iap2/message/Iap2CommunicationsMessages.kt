package com.shilapi.xcertplay.iap2.message

import com.shilapi.xcertplay.iap2.wire.Iap2Frame

/** iAP2 Communications messages for calls that never publish CallStateUpdate (some VoIP apps). */
object Iap2CommunicationsMessages {
    private const val ACCEPT_CALL = 0x415B
    private const val END_CALL = 0x415C

    /** AcceptCall with no call identifier accepts the ringing call on the iPhone. */
    fun acceptCall(): Iap2Frame = Iap2Messages.buildRaw(ACCEPT_CALL) { }

    fun endCall(): Iap2Frame = Iap2Messages.buildRaw(END_CALL) { }
}
