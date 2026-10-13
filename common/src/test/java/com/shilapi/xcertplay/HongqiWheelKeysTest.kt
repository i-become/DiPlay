package com.shilapi.xcertplay

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HongqiWheelKeysTest {
    @Test
    fun factoryKeyCodesMapToCarPlayRoles() {
        assertEquals(0, HongqiQisiSystemKeysBinder.steeringRoleForFactoryKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS))
        assertEquals(1, HongqiQisiSystemKeysBinder.steeringRoleForFactoryKey(KeyEvent.KEYCODE_MEDIA_NEXT))
        assertEquals(2, HongqiQisiSystemKeysBinder.steeringRoleForFactoryKey(KeyEvent.KEYCODE_CALL))
        assertEquals(3, HongqiQisiSystemKeysBinder.steeringRoleForFactoryKey(259))
        assertNull(HongqiQisiSystemKeysBinder.steeringRoleForFactoryKey(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
