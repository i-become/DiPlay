package com.shilapi.xcertplay

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
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class CarPlayControllerUiHandoffTest {
    private lateinit var controller: CarPlayController
    private val session = mock(AirPlaySession::class.java)

    @Before fun setup() {
        controller = CarPlayController(RuntimeEnvironment.getApplication(), CarPlayRuntimeConfig(
            mfiTarget = MfiTarget.REMOTE, transport = CarPlayTransport.WIRELESS,
            remoteMfiServer = "http://127.0.0.1:9000",
            identification = Iap2IdentificationConfig("test", "test", "test", "test", "1", "1", 3),
        ), AirPlayConfig("test", "test", "", "1", AirPlayDisplayConfig(1920, 720)),
            AirPlayIdentity(ByteArray(32), ByteArray(32), "test"), PairingStore(),
            object : AirPlaySessionListener {}, object : AirPlayMediaHandler {}, {})
    }

    @After fun cleanup() {
        if (::controller.isInitialized) { controller.close(); controller.awaitClosed(3_000) }
        shadowOf(Looper.getMainLooper()).idle()
    }

    @Test fun adoptingAHeadlessActiveSessionReplaysItsConnectedState() {
        ReflectionHelpers.setField(controller, "activeSession", session)
        val active = mutableListOf<AirPlaySession>()
        controller.attachUi(object : AirPlaySessionListener {
            override fun onSessionActive(session: AirPlaySession) { active.add(session) }
        }, {})
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(session), active)
    }

    @Test fun aSessionEndingDuringAdoptionCannotBeReplayedAsConnected() {
        ReflectionHelpers.setField(controller, "activeSession", session)
        val events = mutableListOf<String>()
        controller.attachUi(object : AirPlaySessionListener {
            override fun onSessionActive(session: AirPlaySession) { events.add("active") }
            override fun onSessionEnded(session: AirPlaySession) { events.add("ended") }
        }, {})
        ReflectionHelpers.getField<AirPlaySessionListener>(controller, "sessionListener").onSessionEnded(session)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf("ended"), events)
    }

    @Test fun disconnectJustBeforeAdoptionIsDeliveredToTheNewUi() {
        ReflectionHelpers.setField(controller, "lastEndedSession", session)
        val ended = mutableListOf<AirPlaySession>()
        controller.attachUi(object : AirPlaySessionListener {
            override fun onSessionEnded(session: AirPlaySession) { ended.add(session) }
        }, {})
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(session), ended)
    }

    @Test fun onlyTheCurrentUiReceivesTheQueuedHandoff() {
        ReflectionHelpers.setField(controller, "activeSession", session)
        var first = 0
        var second = 0
        controller.attachUi(object : AirPlaySessionListener {
            override fun onSessionActive(session: AirPlaySession) { first++ }
        }, {})
        controller.attachUi(object : AirPlaySessionListener {
            override fun onSessionActive(session: AirPlaySession) { second++ }
        }, {})
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, first)
        assertEquals(1, second)
    }
}
