package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.orchestration.CarPlayController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class NwdSteeringKeysTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private val controller = mock(CarPlayController::class.java)
    private val host = mock(CarPlayHostActivity::class.java)
    private val receiver = NwdSteeringKeyReceiver()
    private var current = controller

    @Before fun setUp() {
        DiagnosticLogging.setEnabled(false)
        context.getSharedPreferences("diplay_nwd_keys", 0).edit().clear().commit()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        `when`(host.projectionShown()).thenReturn(true)
        `when`(host.exitProjection(anyString())).thenReturn(true)
        CarPlayHostSettingsBridge.register(host)
        SteeringWheelEvents.attach(context, controller)
    }

    @After fun tearDown() {
        SteeringWheelEvents.detach(current)
        CarPlayHostSettingsBridge.unregister(host)
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun event(code: Byte) = Intent(NwdSteeringKeyMappings.ACTION)
        .setComponent(ComponentName(context, NwdSteeringKeyReceiver::class.java))
        .putExtra(NwdSteeringKeyMappings.EXTRA, code)

    private fun closeMapping(code: Int = 42) = NwdSteeringKeyMappings.save(
        context, code, NwdSteeringKeyMappings.Command.CLOSE_PROJECTION,
    )

    @Test fun byteZeroIsValidAndReceiverOnlyQueuesTheUiAction() {
        closeMapping(0)
        receiver.onReceive(context, event(0))
        verify(host, never()).exitProjection(anyString())
        shadowOf(Looper.getMainLooper()).idle()
        verify(host).exitProjection("NWD key 0")
        verify(controller, never()).close()
    }

    @Test fun invalidActionMissingExtraWrongTypesAndNegativeBytesDoNothing() {
        closeMapping()
        val valid = event(42)
        listOf(
            Intent(valid).setAction("unrelated"),
            Intent(valid).apply { removeExtra(NwdSteeringKeyMappings.EXTRA) },
            Intent(valid).putExtra(NwdSteeringKeyMappings.EXTRA, 42),
            Intent(valid).putExtra(NwdSteeringKeyMappings.EXTRA, "42"),
            event((-1).toByte()), event((-128).toByte()),
        ).forEach { receiver.onReceive(context, it) }
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
    }

    @Test fun rawNwdFourAndMediaDefaultsAreNotBack() {
        assertNull(NwdSteeringKeyMappings.resolve(context, 4))
        assertNull(NwdSteeringKeyMappings.resolve(context, 0))
        assertEquals(NwdSteeringKeyMappings.Command.PREVIOUS, NwdSteeringKeyMappings.resolve(context, 5))
        assertEquals(NwdSteeringKeyMappings.Command.NEXT, NwdSteeringKeyMappings.resolve(context, 6))
        listOf<Byte>(4, 5, 6).forEach { receiver.onReceive(context, event(it)) }
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
    }

    @Test fun learnedMappingsOverrideDefaultsAndLongPressIsSeparate() {
        closeMapping(5)
        assertEquals(NwdSteeringKeyMappings.Command.CLOSE_PROJECTION, NwdSteeringKeyMappings.resolve(context, 5))
        assertNull(NwdSteeringKeyMappings.resolve(context, 5, longPress = true))
        NwdSteeringKeyMappings.save(context, 5, NwdSteeringKeyMappings.Command.SIRI, longPress = true)
        NwdSteeringKeyMappings.clear(context, 5)
        assertEquals(NwdSteeringKeyMappings.Command.PREVIOUS, NwdSteeringKeyMappings.resolve(context, 5))
        assertEquals(NwdSteeringKeyMappings.Command.SIRI, NwdSteeringKeyMappings.resolve(context, 5, true))
    }

    @Test fun explicitIgnoreOverridesAMediaDefault() {
        NwdSteeringKeyMappings.save(context, 5, NwdSteeringKeyMappings.Command.IGNORE)
        assertEquals(NwdSteeringKeyMappings.Command.IGNORE, NwdSteeringKeyMappings.resolve(context, 5))
    }

    @Test fun queuedBackCannotCloseAReplacementActivity() {
        closeMapping()
        receiver.onReceive(context, event(42))
        val replacement = mock(CarPlayHostActivity::class.java)
        CarPlayHostSettingsBridge.register(replacement)
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
        verify(replacement, never()).exitProjection(anyString())
        CarPlayHostSettingsBridge.unregister(replacement)
    }

    @Test fun queuedBackIsDroppedAfterDetachEvenIfTheControllerIsReattached() {
        closeMapping()
        receiver.onReceive(context, event(42))
        SteeringWheelEvents.detach(controller)
        SteeringWheelEvents.attach(context, controller)
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
    }

    @Test fun queuedBackIsDroppedWhenThePhoneSessionChangesOnTheSameController() {
        closeMapping()
        receiver.onReceive(context, event(42))
        `when`(controller.activeAirPlaySessionToken()).thenReturn(Any())
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
    }

    @Test fun aLateOldDetachDoesNotUnregisterTheNewController() {
        closeMapping()
        current = mock(CarPlayController::class.java)
        `when`(current.activeAirPlaySessionToken()).thenReturn(Any())
        SteeringWheelEvents.attach(context, current)
        SteeringWheelEvents.detach(controller)
        receiver.onReceive(context, event(42))
        shadowOf(Looper.getMainLooper()).idle()
        verify(host).exitProjection("NWD key 42")
    }

    @Test fun noActiveSessionCannotCloseTheUi() {
        closeMapping()
        `when`(controller.activeAirPlaySessionToken()).thenReturn(null)
        receiver.onReceive(context, event(42))
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, never()).exitProjection(anyString())
    }

    @Test fun liveImplicitBroadcastWorksAndPackageBroadcastIsNotProcessedTwice() {
        closeMapping()
        context.sendBroadcast(event(42).setComponent(null).setPackage(context.packageName))
        shadowOf(Looper.getMainLooper()).idle()
        verify(host, times(1)).exitProjection("NWD key 42")
        assertTrue(SteeringWheelEvents.dynamicReceiverRegistered)
        SteeringWheelEvents.detach(controller)
        assertFalse(SteeringWheelEvents.dynamicReceiverRegistered)
    }
}
