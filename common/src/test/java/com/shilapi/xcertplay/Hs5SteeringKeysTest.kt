package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Hs5SteeringKeysTest {
    @Test
    fun factoryKeyCodesMapToCarPlayRoles() {
        assertEquals(0, QisiSteeringServiceClient.carPlayActionForFactoryKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(1, QisiSteeringServiceClient.carPlayActionForFactoryKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(2, QisiSteeringServiceClient.carPlayActionForFactoryKey(KeyEvent.KEYCODE_CALL))
        assertEquals(3, QisiSteeringServiceClient.carPlayActionForFactoryKey(259))
        assertNull(QisiSteeringServiceClient.carPlayActionForFactoryKey(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
