package com.shilapi.xcertplay

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.ServiceConnection
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.Parcel
import android.os.RemoteException
import java.time.Duration
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
@LooperMode(LooperMode.Mode.PAUSED)
class QisiPositionLightsTest {
    @org.junit.Before fun enableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(true) }
    @org.junit.After fun disableRecording() { com.shilapi.xcertplay.diagnostics.DiagnosticLogging.setEnabled(false) }
    private val handler = Handler(Looper.getMainLooper())
    private val component = ComponentName(QisiPositionLightBinder.PACKAGE, QisiPositionLightBinder.SERVICE)
    private var monitor: QisiPositionLightMonitor? = null
    private val changes = mutableListOf<Boolean?>()
    private val events = mutableListOf<String>()
    private var raw = 0
    private var rejectRead = false
    private var throwRead = false
    private var emptyRead = false
    private var readCount = 0
    private var moduleQueries = 0
    private var binds = 0
    private var unbinds = 0
    private var bindAccepted = true
    private var denyBind = false
    private lateinit var connection: ServiceConnection

    private val stateBinder = object : Binder() {
        init { attachInterface(null, QisiPositionLightBinder.VEHICLE_STATE) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertEquals(19, code)
            assertEquals(0, flags)
            data.enforceInterface(QisiPositionLightBinder.VEHICLE_STATE)
            assertEquals("The position light getter has no parameters", 0, data.dataAvail())
            readCount++
            if (rejectRead) return false
            if (throwRead) { reply!!.writeException(SecurityException("test")); return true }
            if (emptyRead) return true
            reply!!.writeNoException()
            reply.writeInt(raw)
            return true
        }
    }
    private val root = rootBinder(stateBinder)

    private fun rootBinder(module: IBinder?): IBinder = object : Binder() {
        init { attachInterface(null, QisiPositionLightBinder.ROOT) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertEquals(1, code)
            assertEquals(0, flags)
            data.enforceInterface(QisiPositionLightBinder.ROOT)
            assertEquals(15, data.readInt())
            assertEquals(0, data.dataAvail())
            moduleQueries++
            reply!!.writeNoException()
            reply.writeStrongBinder(module)
            return true
        }
    }

    private fun start() {
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                assertEquals(component, intent.component)
                assertEquals(Context.BIND_AUTO_CREATE, flags)
                binds++
                connection = conn
                if (denyBind) throw SecurityException("test")
                return bindAccepted
            }
            override fun unbindService(conn: ServiceConnection) { unbinds++ }
        }
        monitor = QisiPositionLightMonitor(context, handler, handler, changes::add, events::add).also { it.start() }
        idle()
    }

    private fun connect(binder: IBinder = root) {
        connection.onServiceConnected(component, binder)
        idle()
    }

    private fun idle(ms: Long = 0) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }

    @After fun stop() { monitor?.stop(); idle() }

    @Test fun exactReadOnlyProtocolMapsOffToDayAndOnToNightAndSuppressesDuplicates() {
        start()
        connect()
        assertEquals(listOf(false), changes)
        assertEquals(1, moduleQueries)
        idle(499)
        assertEquals(1, readCount)
        idle(1)
        assertEquals(2, readCount)
        assertEquals(listOf(false), changes)
        raw = 1
        idle(500)
        assertEquals(listOf(false, true), changes)
        assertEquals(true, monitor!!.night)
        assertTrue(monitor!!.report().contains("raw=1"))
        assertTrue(events.any { it.contains("module=15 readTransaction=19") })
        raw = 0
        idle(500)
        assertEquals(listOf(false, true, false), changes)
    }

    @Test fun unsupportedStateIsUnknownAndDoesNotBecomeNightOrRebindRepeatedly() {
        start(); connect()
        raw = 7
        idle(500)
        assertEquals(listOf(false, null), changes)
        assertNull(monitor!!.night)
        idle(1_000)
        assertEquals(1, binds)
        assertEquals(listOf(false, null), changes)
        raw = 1
        idle(500)
        assertEquals(listOf(false, null, true), changes)
    }

    @Test fun rejectedReadClearsCachedStateAndRebindsAfterThreeSeconds() {
        start(); connect()
        val old = connection
        rejectRead = true
        idle(500)
        assertNull(monitor!!.night)
        assertEquals(listOf(false, null), changes)
        assertEquals(1, unbinds)
        idle(2_999)
        assertEquals(1, binds)
        idle(1)
        assertEquals(2, binds)
        rejectRead = false
        raw = 1
        old.onServiceConnected(component, root)
        idle()
        assertEquals(1, moduleQueries)
        connect()
        assertEquals(listOf(false, null, true), changes)
    }

    @Test fun remoteExceptionReplyFallsBackAndRetries() {
        start(); connect()
        throwRead = true
        idle(500)
        assertNull(monitor!!.night)
        assertTrue(events.any { it.contains("read-SecurityException") })
        idle(3_000)
        assertEquals(2, binds)
    }

    @Test fun emptyReplyCannotBeMistakenForLightsOff() {
        emptyRead = true
        start(); connect()
        assertTrue(changes.isEmpty())
        assertNull(monitor!!.night)
        assertTrue(events.any { it.contains("read-RemoteException") })
    }

    @Test fun nullAndWrongManagerAreRejectedBeforeReadingVendorTransaction() {
        start(); connect(rootBinder(null))
        assertEquals(0, readCount)
        idle(3_000)
        val wrong = Binder().apply { attachInterface(null, "wrong.interface") }
        connect(rootBinder(wrong))
        assertEquals(0, readCount)
        assertNull(monitor!!.night)
    }

    @Test fun wrongRootIsRejectedWithoutQueryingAModule() {
        start()
        connect(Binder().apply { attachInterface(null, "wrong.root") })
        assertEquals(0, moduleQueries)
        assertEquals(0, readCount)
        idle(3_000)
        assertEquals(2, binds)
    }

    @Test fun disconnectClearsLightSignalAndIgnoresLateCallbacksFromOldBinding() {
        raw = 1
        start(); connect()
        val old = connection
        old.onServiceDisconnected(component)
        idle()
        assertEquals(listOf(true, null), changes)
        idle(3_000)
        old.onBindingDied(component)
        idle()
        connect()
        assertEquals(listOf(true, null, true), changes)
        assertEquals(2, binds)
    }

    @Test fun nullBindingAndBindingDeathBothRetry() {
        start()
        connection.onNullBinding(component)
        idle(3_000)
        assertEquals(2, binds)
        connect()
        connection.onBindingDied(component)
        idle(3_000)
        assertEquals(3, binds)
        assertNull(monitor!!.night)
    }

    @Test fun rejectedAndDeniedBindsCanRetryAndRecover() {
        bindAccepted = false
        start()
        assertEquals(1, binds)
        denyBind = true
        idle(3_000)
        assertEquals(2, binds)
        denyBind = false
        bindAccepted = true
        idle(3_000)
        connect()
        assertEquals(false, monitor!!.night)
        assertEquals(3, binds)
    }

    @Test fun unansweredBindHasABoundedWaitAndStopCancelsAllFutureWork() {
        start()
        monitor!!.start()
        idle(9_999)
        assertEquals(1, binds)
        idle(1)
        assertEquals(1, unbinds)
        idle(3_000)
        assertEquals(2, binds)
        monitor!!.stop()
        idle()
        connection.onServiceConnected(component, root)
        idle(30_000)
        assertEquals(2, binds)
        assertEquals(0, readCount)
        assertTrue(monitor!!.report().contains("state=stopped"))
    }

    @Test fun serviceBindingAndBinderReadsUseWorkerWhileChangesAreDeliveredOnMain() {
        val thread = HandlerThread("test-qisi-reader").apply { start() }
        val bound = CountDownLatch(1)
        val read = CountDownLatch(1)
        val released = CountDownLatch(1)
        val state = object : Binder() {
            init { attachInterface(null, QisiPositionLightBinder.VEHICLE_STATE) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                assertSame(thread.looper, Looper.myLooper())
                data.enforceInterface(QisiPositionLightBinder.VEHICLE_STATE)
                reply!!.writeNoException()
                reply.writeInt(1)
                read.countDown()
                return true
            }
        }
        val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
            override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
                assertSame(thread.looper, Looper.myLooper())
                conn.onServiceConnected(component, rootBinder(state))
                bound.countDown()
                return true
            }
            override fun unbindService(conn: ServiceConnection) { released.countDown() }
        }
        val output = mutableListOf<Boolean?>()
        val background = QisiPositionLightMonitor(context, Handler(thread.looper), handler, {
            assertSame(Looper.getMainLooper(), Looper.myLooper())
            output.add(it)
        })
        try {
            background.start()
            assertTrue(bound.await(3, TimeUnit.SECONDS))
            assertTrue(read.await(3, TimeUnit.SECONDS))
            // Flush publication after read() returns as well as the Binder call itself.
            val flushed = CountDownLatch(1)
            Handler(thread.looper).post { flushed.countDown() }
            assertTrue(flushed.await(3, TimeUnit.SECONDS))
            idle()
            assertEquals(listOf(true), output)
        } finally {
            background.stop()
            assertTrue(released.await(3, TimeUnit.SECONDS))
            thread.quitSafely()
            thread.join(3_000)
            idle()
        }
    }
}
