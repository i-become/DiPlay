package com.shilapi.xcertplay

import android.bluetooth.BluetoothAdapter
import android.os.Looper
import com.shilapi.xcertplay.airplay.AirPlayConfig
import com.shilapi.xcertplay.airplay.AirPlayDisplayConfig
import com.shilapi.xcertplay.airplay.AirPlayIdentity
import com.shilapi.xcertplay.airplay.AirPlayMediaHandler
import com.shilapi.xcertplay.airplay.AirPlaySession
import com.shilapi.xcertplay.airplay.AirPlaySessionListener
import com.shilapi.xcertplay.airplay.PairingStore
import com.shilapi.xcertplay.orchestration.CarPlayController
import com.shilapi.xcertplay.orchestration.CarPlayRuntimeConfig
import com.shilapi.xcertplay.orchestration.CarPlayTransport
import com.shilapi.xcertplay.orchestration.MfiTarget
import com.shilapi.xcertplay.transport.Iap2IdentificationConfig
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowBuild
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayBluetoothAudioHandoffTest {
    private lateinit var controller: CarPlayController
    private val session = mock(AirPlaySession::class.java)

    @Before fun setup() { ShadowBuild.setManufacturer("HONGQI") }
    @After fun cleanup() {
        if (::controller.isInitialized) { controller.close(); controller.awaitClosed(3_000) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun wirelessCarPlayRequestsMusicHandoffOnceAfterTheTunnelIsReady() {
        ready()
        request()
        val first = handoff()
        assertNotNull(first)
        request()
        assertSame(first, handoff())
    }

    @Test fun choosingOriginalCarBluetoothAudioPreservesTheBluetoothMusicConnection() {
        ready(bluetoothAudio = true)
        request()
        assertNull(handoff())
    }

    @Test fun bootstrapAndRenderedVideoFallbackCannotDisconnectBluetoothMusic() {
        ready()
        flag("wirelessTunnelReady", false)
        request()
        assertNull(handoff())
        flag("wirelessTunnelReady", true)
        flag("wirelessHandoffRequested", false)
        request()
        assertNull(handoff())
    }

    @Test fun failedOrReplacedAttemptsCannotChangeThePhonesAudioRoute() {
        ready()
        flag("wirelessFailureReported", true)
        request()
        assertNull(handoff())
        flag("wirelessFailureReported", false)
        request(generation = -1)
        assertNull(handoff())
    }

    @Test fun endedSessionsCancelTheQueuedAudioHandoff() {
        ready()
        request()
        assertNotNull(handoff())
        ReflectionHelpers.getField<AirPlaySessionListener>(controller, "sessionListener").onSessionEnded(session)
        assertNull(handoff())
        request()
        assertNull(handoff())
    }

    @Test fun wiredConnectionsKeepTheirExistingAudioPolicy() {
        ready(transport = CarPlayTransport.WIRED)
        request()
        assertNull(handoff())
    }

    @Test fun otherHeadUnitsKeepTheirExistingBluetoothMusicPolicy() {
        ShadowBuild.setManufacturer("generic")
        ready()
        request()
        assertNull(handoff())
    }

    private fun ready(bluetoothAudio: Boolean = false, transport: CarPlayTransport = CarPlayTransport.WIRELESS) {
        controller = CarPlayController(RuntimeEnvironment.getApplication(), CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.REMOTE, transport = transport,
            remoteMfiServer = "http://127.0.0.1:9000",
            identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1", 3),
        ), AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(1920, 720), disableAudioOutput = bluetoothAudio),
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {})
        ReflectionHelpers.setField(controller, "activeSession", session)
        ReflectionHelpers.setField(controller, "wirelessBluetoothTarget",
            BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55"))
        flag("wirelessHandoffRequested", true)
        flag("wirelessTunnelReady", true)
        flag("wirelessActiveReported", true)
    }

    private fun flag(name: String, value: Boolean) = ReflectionHelpers.getField<AtomicBoolean>(controller, name).set(value)
    private fun handoff(): Any? = ReflectionHelpers.getField(controller, "bluetoothAudioHandoff")
    private fun request(generation: Int = 0) {
        ReflectionHelpers.callInstanceMethod<Unit>(controller, "handoffBluetoothMusic", from(Int::class.javaPrimitiveType!!, generation))
    }
}
