package com.shilapi.xcertplay.orchestration

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.os.Handler
import android.os.Looper
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class BluetoothAudioHandoffTest {
    private val handler = Handler(Looper.getMainLooper())
    private val target = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:11:22:33:44:55")
    private val other = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("AA:BB:CC:DD:EE:FF")
    private val messages = mutableListOf<String>()
    private var current = true
    private var supported = true
    private var requests = 0
    private var releases = 0
    private lateinit var listener: BluetoothProfile.ServiceListener
    private val handoff by lazy { BluetoothAudioHandoff(target, handler, { current },
        requestProxy = { requests++; listener = it; supported },
        releaseProxy = { releases++ }, diagnostic = { messages.add(it) }) }

    @Before fun setup() { DiagnosticLogging.setEnabled(true) }
    @After fun cleanup() {
        handoff.close()
        idle()
        DiagnosticLogging.setEnabled(false)
    }

    @Test fun onlyTheCurrentIphonesMusicProfileIsReleasedOnce() {
        val sink = FakeSink(listOf(target, other))
        handoff.start(); handoff.start(); idle()
        connected(sink)
        connected(sink)
        assertEquals(1, requests)
        assertEquals(listOf(target), sink.disconnected)
        assertEquals(2, releases)
        assertTrue(messages.single().endsWith("requested=true"))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(4))
        assertEquals(1, messages.size)
    }

    @Test fun anotherPhonesMusicConnectionIsLeftAlone() {
        handoff.start(); idle()
        val sink = FakeSink(listOf(other))
        connected(sink)
        assertTrue(sink.disconnected.isEmpty())
        assertEquals(1, releases)
    }

    @Test fun cancellationBeforeBindingDoesNoBluetoothWork() {
        handoff.start(); handoff.close(); idle()
        assertEquals(0, requests)
    }

    @Test fun aDisconnectedSessionDoesNotRequestAProfile() {
        current = false
        handoff.start(); idle()
        assertEquals(0, requests)
    }

    @Test fun aLateProxyIsReleasedWithoutChangingAReplacementSessionsAudio() {
        handoff.start(); idle()
        current = false
        val sink = FakeSink(listOf(target))
        connected(sink)
        assertTrue(sink.disconnected.isEmpty())
        assertEquals(1, releases)
    }

    @Test fun aProxyArrivingAfterExplicitCancellationIsReleased() {
        handoff.start(); idle(); handoff.close()
        val sink = FakeSink(listOf(target))
        connected(sink)
        assertTrue(sink.disconnected.isEmpty())
        assertEquals(1, releases)
    }

    @Test fun unavailableProfilesTimeOutOnceAndLateCallbacksCannotChangeAudio() {
        handoff.start(); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(1, requests)
        assertTrue(messages.single().contains("timeout"))
        val sink = FakeSink(listOf(target))
        connected(sink)
        assertTrue(sink.disconnected.isEmpty())
        assertEquals(1, releases)
    }

    @Test fun firmwarePermissionDenialDoesNotCrashOrLeakTheProfile() {
        handoff.start(); idle()
        val sink = FakeSink(listOf(target), denied = true)
        connected(sink)
        assertEquals(1, releases)
        assertTrue(messages.single().endsWith("SecurityException"))
    }

    @Test fun unsupportedFirmwareDoesNotPollOrRetry() {
        supported = false
        handoff.start(); idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(30))
        assertEquals(1, requests)
        assertEquals(0, releases)
        assertTrue(messages.single().endsWith("unsupported"))
    }

    @Test fun noDiagnosticMessagesAreBuiltOrDeliveredWhenLoggingIsOff() {
        DiagnosticLogging.setEnabled(false)
        handoff.start(); idle()
        val sink = FakeSink(listOf(target))
        connected(sink)
        assertEquals(listOf(target), sink.disconnected)
        assertTrue(messages.isEmpty())
    }

    private fun connected(sink: FakeSink) {
        listener.onServiceConnected(BluetoothAudioHandoff.A2DP_SINK, sink)
        idle()
    }
    private fun idle() { shadowOf(Looper.getMainLooper()).idle() }

    class FakeSink(private val devices: List<BluetoothDevice>, private val denied: Boolean = false) : BluetoothProfile {
        val disconnected = mutableListOf<BluetoothDevice>()
        override fun getConnectedDevices() = devices
        override fun getDevicesMatchingConnectionStates(states: IntArray) = devices
        override fun getConnectionState(device: BluetoothDevice) =
            if (device in devices) BluetoothProfile.STATE_CONNECTED else BluetoothProfile.STATE_DISCONNECTED
        fun disconnect(device: BluetoothDevice): Boolean {
            if (denied) throw SecurityException("firmware permission")
            disconnected.add(device)
            return true
        }
    }
}
