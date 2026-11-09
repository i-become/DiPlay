package com.shilapi.xcertplay.hud

import android.os.Binder
import android.os.DeadObjectException
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
import com.shilapi.xcertplay.orchestration.ConnectionDiagnosticTrace
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.RuntimeEnvironment
import org.robolectric.util.ReflectionHelpers
import org.robolectric.shadows.ShadowBuild

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class QisiClusterMapSessionTest {
    private val calls = mutableListOf<String>()
    private var callback: IBinder? = null
    private var rejectRegistration = false
    private var rejectNavigation = false
    private var closes = 0
    private val guidanceCalls = mutableListOf<Int>()
    private var rejectInstrumentGuidance = false

    @After fun stopDiagnostics() {
        DiagnosticLogging.setEnabled(false)
        ConnectionDiagnosticTrace.sink = null
    }
    private val system = object : Binder() {
        init { attachInterface(null, "ltd.qisi.adapterproxy.ICarSystemControlManager") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface("ltd.qisi.adapterproxy.ICarSystemControlManager")
            when (code) {
                64 -> {
                    callback = data.readStrongBinder()
                    calls += "register"
                    if (rejectRegistration) return false
                }
                65 -> {
                    assertSame(callback, data.readStrongBinder())
                    calls += "unregister"
                }
                69 -> {
                    calls += "navigation:${data.readInt()}:${data.readInt()}"
                    if (rejectNavigation) { rejectNavigation = false; return false }
                }
                else -> fail("unexpected system transaction $code")
            }
            assertEquals(0, data.dataAvail())
            reply!!.writeNoException()
            if (code == 69) reply.writeInt(1)
            return true
        }
    }
    private val instrument = object : Binder() {
        init { attachInterface(null, "ltd.qisi.adapterproxy.ICarInstrumentRelatedManager") }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface("ltd.qisi.adapterproxy.ICarInstrumentRelatedManager")
            if (code == 5 || code == 6) {
                guidanceCalls += code
                if (code == 6 && rejectInstrumentGuidance) return false
                reply!!.writeNoException()
                reply.writeInt(1)
                return true
            }
            assertEquals(10, code)
            assertEquals(1, data.readInt()) // Present parcelable.
            assertEquals(0, data.readInt()) // Full-map info type.
            calls += "map:${data.readInt()}"
            assertEquals(0, data.dataAvail())
            reply!!.writeNoException()
            reply.writeInt(1)
            return true
        }
    }

    private fun session(onQuit: () -> Unit = { closes++ }) =
        QisiClusterMapSession(system, instrument, Handler(Looper.getMainLooper()), onQuit)

    @Test fun entersFullScreenOnlyAfterSubscribingAndReleasesItBeforeUnregistering() {
        val session = session()
        session.open()
        session.open()
        assertEquals(listOf("register", "map:4", "navigation:1:1"), calls)
        session.close()
        session.close()
        assertEquals(listOf("register", "map:4", "navigation:1:1",
            "map:2", "navigation:0:0", "unregister"), calls)
    }

    @Test fun hardKeyQuitClosesOnMainThreadWithoutAnAndroidBackKey() {
        val session = session()
        session.open()
        sendRequest("HardKeyQuit")
        assertEquals(0, closes)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun zeroRequestIsAlsoAnExitButOtherNavigationRequestsAreNot() {
        session().open()
        listOf("1", "ICFailed", "2", "", null).forEach { sendRequest(it) }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, closes)
        sendRequest("0")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun queuedAndSubsequentCallbacksCannotCloseAReopenedMap() {
        val session = session()
        session.open()
        val old = callback!!
        sendRequest("HardKeyQuit", old)
        session.close()
        session.open()
        sendRequest("HardKeyQuit", old)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, closes)
        sendRequest("HardKeyQuit")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun failedSubscriptionDoesNotChangeTheCarsCurrentMapMode() {
        rejectRegistration = true
        try { session().open(); fail("registration should fail") } catch (_: RemoteException) {}
        assertEquals(listOf("register", "unregister"), calls)
        sendRequest("HardKeyQuit")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, closes)
    }

    @Test fun failedEntryReleasesThePartialRequestAndAllowsRetry() {
        val session = session()
        rejectNavigation = true
        try { session.open(); fail("navigation should fail") } catch (_: RemoteException) {}
        assertEquals(listOf("register", "map:4", "navigation:1:1",
            "map:2", "navigation:0:0", "unregister"), calls)
        session.open()
        sendRequest("HardKeyQuit")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun duplicateExitRequestsAreInvalidatedWhenTheFirstClosesTheMap() {
        lateinit var session: QisiClusterMapSession
        session = session { closes++; session.close() }
        session.open()
        sendRequest("HardKeyQuit")
        sendRequest("0")
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, closes)
    }

    @Test fun callbackRejectsAnotherInterface() {
        session().open()
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        try {
            data.writeInterfaceToken("other.interface")
            data.writeString("HardKeyQuit")
            try {
                callback!!.transact(15, data, reply, 0)
                reply.readException()
                fail("wrong interface accepted")
            }
            catch (_: SecurityException) {}
        } finally { data.recycle(); reply.recycle() }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, closes)
    }

    @Test fun protocolEvidenceIsExportedOnlyWhileSessionLoggingIsEnabled() {
        val evidence = mutableListOf<String>()
        ConnectionDiagnosticTrace.sink = { evidence += it }
        DiagnosticLogging.setEnabled(false)
        val session = session()
        session.open()
        sendRequest("HardKeyQuit")
        shadowOf(Looper.getMainLooper()).idle()
        session.close()
        assertTrue(evidence.isEmpty())
        DiagnosticLogging.setEnabled(true)
        session.open()
        sendRequest("HardKeyQuit")
        shadowOf(Looper.getMainLooper()).idle()
        session.close()
        assertTrue(evidence.any { it.contains("exit listener registered") })
        assertTrue(evidence.any { it.contains("navigation=1,true") })
        assertTrue(evidence.any { it.contains("request=HardKeyQuit") })
        assertTrue(evidence.any { it.contains("delivered to current instrument window") })
        assertTrue(evidence.any { it.contains("exit listener removed") })
    }

    @Test fun guidanceBridgeKeepsTheExitSubscriptionForExactlyTheVisibleMapLifetime() {
        withBridge {
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            Hs5GuidanceBridge.setClusterMapShown(true)
            sendRequest("HardKeyQuit")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, closes)
            Hs5GuidanceBridge.setClusterMapShown(false)
            assertEquals("unregister", calls.last())
            sendRequest("HardKeyQuit")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, closes)
        }
    }

    @Test fun replacingTheBridgeHostDropsOldQuitRequestsAndRegistersANewCallback() {
        withBridge {
            Hs5GuidanceBridge.setClusterMapCloseRequest { fail("old host was called") }
            Hs5GuidanceBridge.setClusterMapShown(true)
            val old = callback!!
            sendRequest("HardKeyQuit", old)
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            sendRequest("HardKeyQuit", old)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, closes)
            sendRequest("HardKeyQuit")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, closes)
        }
    }

    @Test fun adapterReconnectRestoresTheSubscriptionEvenWhenTheHudModuleIsUnavailable() {
        val evidence = mutableListOf<String>()
        ConnectionDiagnosticTrace.sink = { evidence += it }
        DiagnosticLogging.setEnabled(true)
        withBridge {
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            Hs5GuidanceBridge.setClusterMapShown(true)
            val old = callback!!
            ReflectionHelpers.callInstanceMethod<Unit>(Hs5GuidanceBridge, "resetModulesLocked")
            val remote = object : Binder() {
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    assertEquals(1, code)
                    data.enforceInterface("ltd.qisi.adapterproxy.IVehicleRemoteClienter")
                    val module = data.readInt()
                    reply!!.writeNoException()
                    reply.writeStrongBinder(when (module) { 8 -> instrument; 13 -> system; else -> null })
                    return true
                }
            }
            ReflectionHelpers.callInstanceMethod<Unit>(Hs5GuidanceBridge, "attachRemoteService",
                ReflectionHelpers.ClassParameter.from(IBinder::class.java, remote))
            assertTrue(Hs5GuidanceBridge.clusterMapDiagnosticReport().contains("active=true"))
            assertTrue(Hs5GuidanceBridge.clusterMapDiagnosticReport().contains("hud=false"))
            sendRequest("HardKeyQuit", old)
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(0, closes)
            sendRequest("HardKeyQuit")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(evidence.joinToString("\n"), 1, closes)
        }
    }

    @Test fun rejectedHudFieldDoesNotReleaseTheMapOrBlockLaterFields() {
        verifyGuidanceFailureKeepsMap(expectedFailures = 1) { _, _ -> false }
    }

    @Test fun negativeHudResultDoesNotReleaseTheMapOrBlockLaterFields() {
        verifyGuidanceFailureKeepsMap(expectedFailures = 4) { _, reply ->
            reply!!.writeNoException()
            reply.writeInt(-1)
            true
        }
    }

    @Test fun remoteHudExceptionDoesNotReleaseTheMapOrBlockLaterFields() {
        verifyGuidanceFailureKeepsMap(expectedFailures = 4) { _, reply ->
            reply!!.writeException(IllegalArgumentException("unsupported field value"))
            true
        }
    }

    private fun verifyGuidanceFailureKeepsMap(expectedFailures: Int, reject: (Parcel, Parcel?) -> Boolean) {
        val hudCalls = mutableListOf<Int>()
        val hud = hudBinder { code, data, reply ->
            hudCalls += code
            if (code == QisiVehicleBinder.HUD_SET_NAV_STATUS) reject(data, reply) else {
                reply!!.writeNoException()
                reply.writeInt(1)
                true
            }
        }
        withBridge {
            ReflectionHelpers.setStaticField(Hs5GuidanceBridge::class.java, "hudBinder", hud)
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            Hs5GuidanceBridge.setClusterMapShown(true)
            repeat(3) { assertTrue(sendGuidance()) }
            ReflectionHelpers.callInstanceMethod<Unit>(Hs5GuidanceBridge, "clearQisiLocked")
            assertTrue(Hs5GuidanceBridge.clusterMapDiagnosticReport().contains("active=true"))
            assertEquals(listOf("register", "map:4", "navigation:1:1"), calls)
            assertEquals(4, hudCalls.count { it == QisiVehicleBinder.HUD_SET_CAMERA_DISTANCE })
            assertEquals(expectedFailures, hudCalls.count { it == QisiVehicleBinder.HUD_SET_NAV_STATUS })
            assertEquals(listOf(5, 6, 6, 5), guidanceCalls)
            sendRequest("HardKeyQuit")
            shadowOf(Looper.getMainLooper()).idle()
            assertEquals(1, closes)
        }
    }

    @Test fun rejectedInstrumentGuidanceDoesNotReleaseTheMapAndStillDeactivatesTbt() {
        rejectInstrumentGuidance = true
        withBridge {
            ReflectionHelpers.setStaticField(Hs5GuidanceBridge::class.java, "hudBinder",
                hudBinder { _, _, reply -> reply!!.writeNoException(); reply.writeInt(1); true })
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            Hs5GuidanceBridge.setClusterMapShown(true)
            repeat(3) { assertTrue(sendGuidance()) }
            ReflectionHelpers.callInstanceMethod<Unit>(Hs5GuidanceBridge, "clearQisiLocked")
            assertEquals(listOf(5, 6, 5), guidanceCalls)
            assertTrue(Hs5GuidanceBridge.clusterMapDiagnosticReport().contains("active=true"))
            assertEquals(listOf("register", "map:4", "navigation:1:1"), calls)
        }
    }

    @Test fun deadGuidanceBinderStillReleasesAndReconnectsTheService() {
        withBridge {
            ReflectionHelpers.setStaticField(Hs5GuidanceBridge::class.java, "hudBinder",
                hudBinder { _, _, _ -> throw DeadObjectException() })
            Hs5GuidanceBridge.setClusterMapCloseRequest { closes++ }
            Hs5GuidanceBridge.setClusterMapShown(true)
            assertFalse(sendGuidance())
            assertTrue(Hs5GuidanceBridge.clusterMapDiagnosticReport().contains("active=false"))
            assertEquals("unregister", calls.last())
        }
    }

    private fun sendGuidance(): Boolean = ReflectionHelpers.callInstanceMethod(
        Hs5GuidanceBridge, "sendQisiLocked", ReflectionHelpers.ClassParameter.from(
            Hs5GuidanceFrame::class.java, Hs5GuidanceFrame(2, 0, 500, road = "next road")))

    private fun hudBinder(handle: (Int, Parcel, Parcel?) -> Boolean): IBinder = object : Binder() {
        init { attachInterface(null, QisiVehicleBinder.TOKEN_HUD) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            data.enforceInterface(QisiVehicleBinder.TOKEN_HUD)
            return handle(code, data, reply)
        }
    }

    private fun withBridge(test: () -> Unit) {
        ShadowBuild.setModel("HONG QI")
        val bridgeClass = Hs5GuidanceBridge::class.java
        val context = object : android.content.ContextWrapper(RuntimeEnvironment.getApplication()) {
            // These fake binders have no Android service binding to release. Robolectric otherwise
            // synthesizes a disconnection for the replacement binder during unbindService().
            override fun unbindService(connection: android.content.ServiceConnection) = Unit
        }
        ReflectionHelpers.setStaticField(bridgeClass, "context", context)
        ReflectionHelpers.setStaticField(bridgeClass, "useQisi", true)
        ReflectionHelpers.setStaticField(bridgeClass, "instrumentBinder", instrument)
        ReflectionHelpers.setStaticField(bridgeClass, "systemBinder", system)
        ReflectionHelpers.setStaticField(bridgeClass, "remoteBinder", Binder())
        try { test() } finally {
            Hs5GuidanceBridge.setClusterMapShown(false)
            Hs5GuidanceBridge.setClusterMapCloseRequest(null)
            ReflectionHelpers.callInstanceMethod<Unit>(Hs5GuidanceBridge, "resetModulesLocked")
            ReflectionHelpers.setStaticField(bridgeClass, "context", null)
            ReflectionHelpers.setStaticField(bridgeClass, "useQisi", false)
        }
    }

    private fun sendRequest(value: String?, target: IBinder = callback!!) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken("ltd.qisi.adapterproxy.listener.IVehicleSystemControlListener")
            data.writeString(value)
            assertTrue(target.transact(15, data, null, IBinder.FLAG_ONEWAY))
        } finally { data.recycle() }
    }
}
