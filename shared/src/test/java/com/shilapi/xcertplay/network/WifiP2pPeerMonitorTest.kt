package com.shilapi.xcertplay.network

import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pGroup
import android.net.wifi.p2p.WifiP2pManager
import android.os.Handler
import android.os.Looper
import java.time.Duration
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter.from

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class WifiP2pPeerMonitorTest {
    private val app get() = RuntimeEnvironment.getApplication() as Context
    private var current: WifiP2pGroup? = group(true)
    private var disconnected = 0
    private var queries = 0
    private val monitor by lazy { WifiP2pPeerMonitor(app, "DiPlay", Handler(Looper.getMainLooper()),
        requestGroup = { queries++; it(current) }, onPeerLeft = { disconnected++ }, diagnostic = {}) }

    @After fun cleanup() { monitor.close(); shadowOf(Looper.getMainLooper()).idle() }

    @Test fun confirmedClientDepartureEndsTheSessionWithoutWaitingForTcpEof() {
        start()
        broadcast(group(false))
        assertEquals(0, disconnected)
        advance()
        assertEquals(1, disconnected)
        broadcast(group(false))
        advance()
        assertEquals(1, disconnected)
    }

    @Test fun startupAndLegacyEmptyClientListsNeverTerminateAWorkingSession() {
        current = group(false)
        start()
        repeat(4) { broadcast(group(false)); advance() }
        assertEquals(0, disconnected)
    }

    @Test fun phoneReturningDuringTheDebouncePreservesTheSession() {
        start()
        broadcast(group(false))
        broadcast(group(true))
        advance()
        assertEquals(0, disconnected)
    }

    @Test fun confirmationQueryCanSeeThePhoneReturnWithoutABroadcast() {
        start()
        broadcast(group(false))
        current = group(true)
        advance()
        assertEquals(0, disconnected)
    }

    @Test fun otherApplicationsGroupsAndQueuedCallbacksCannotEndANewSession() {
        start()
        broadcast(group(false, "Other"))
        advance()
        assertEquals(0, disconnected)
        broadcast(group(false))
        monitor.close()
        val before = queries
        advance()
        assertEquals(before, queries)
        assertEquals(0, disconnected)
    }

    @Test fun wholeGroupDisappearanceAfterAssociationAlsoEndsTheSession() {
        start()
        current = null
        app.sendBroadcast(Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION))
        shadowOf(Looper.getMainLooper()).idle()
        advance()
        assertEquals(1, disconnected)
    }

    @Test fun noPollingOrVideoIdleTimerRunsOnAStableConnection() {
        start()
        val before = queries
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMinutes(5))
        assertEquals(before, queries)
        assertEquals(0, disconnected)
    }

    private fun start() { monitor.start(); shadowOf(Looper.getMainLooper()).idle() }
    private fun advance() { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2)) }
    private fun broadcast(group: WifiP2pGroup) {
        current = group
        app.sendBroadcast(Intent(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            .putExtra(WifiP2pManager.EXTRA_WIFI_P2P_GROUP, group))
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun group(present: Boolean, name: String = "DiPlay"): WifiP2pGroup = WifiP2pGroup().also {
        ReflectionHelpers.callInstanceMethod<Unit>(it, "setIsGroupOwner", from(Boolean::class.javaPrimitiveType!!, true))
        ReflectionHelpers.callInstanceMethod<Unit>(it, "setNetworkName", from(String::class.java, name))
        if (present) ReflectionHelpers.callInstanceMethod<Unit>(it, "addClient", from(WifiP2pDevice::class.java, WifiP2pDevice()))
    }
}
