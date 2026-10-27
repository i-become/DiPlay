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
import android.os.SystemClock
import com.shilapi.xcertplay.diagnostics.DiagnosticLogging
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
class Hs5VehicleMonitorTest {
    private val main = Handler(Looper.getMainLooper())
    private val component = ComponentName(QisiPositionLightBinder.PACKAGE, QisiPositionLightBinder.SERVICE)
    private lateinit var connection: ServiceConnection
    private var listener: IBinder? = null
    private var binds = 0
    private var unbinds = 0
    private var reads = 0
    private var canReads = 0
    private var rpmReads = 0
    private var unregisters = 0
    private var speed = 4200
    private var duringSpeedRead: (() -> Unit)? = null
    private var failSpeed = false
    private var reject = false
    private var allowListen = true
    private var expectedLooper = Looper.getMainLooper()
    private var backgroundConnected: CountDownLatch? = null
    private var monitor: Hs5VehicleMonitor? = null
    private val records = mutableListOf<String>()
    private val changes = mutableListOf<Hs5VehicleSample>()
    private val state = object : Binder() {
        init { attachInterface(null, QisiPositionLightBinder.VEHICLE_STATE) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertSame(expectedLooper, Looper.myLooper())
            data.enforceInterface(QisiPositionLightBinder.VEHICLE_STATE)
            if (code == 42 || code == 43) {
                val received = data.readStrongBinder()
                if (code == 42) {
                    if (!allowListen) return false
                    listener = received
                } else { assertSame(listener, received); unregisters++ }
                reply!!.writeNoException()
                return true
            }
            assertTrue("Unexpected polled field $code", code in listOf(7, 6, 31, 58, 26, 80))
            if (code == 26 || code == 80) assertTrue(data.readInt() in 0..3)
            assertEquals(0, data.dataAvail())
            reads++
            if (code == 7) duringSpeedRead?.invoke()
            if (code == 7 && failSpeed) {
                reply!!.writeException(SecurityException("getter unavailable"))
                return true
            }
            reply!!.writeNoException()
            reply.writeInt(if (code == 7) speed else 0)
            backgroundConnected?.countDown()
            return true
        }
    }
    private val root = object : Binder() {
        init { attachInterface(null, QisiPositionLightBinder.ROOT) }
        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            assertSame(expectedLooper, Looper.myLooper())
            data.enforceInterface(QisiPositionLightBinder.ROOT)
            assertEquals(1, code)
            assertEquals("No optional getter-only managers", 15, data.readInt())
            reply!!.writeNoException()
            reply.writeStrongBinder(state)
            return true
        }
    }
    private val context = object : ContextWrapper(RuntimeEnvironment.getApplication()) {
        override fun bindService(intent: Intent, conn: ServiceConnection, flags: Int): Boolean {
            assertSame(expectedLooper, Looper.myLooper())
            assertEquals(component, intent.component)
            assertEquals(Context.BIND_AUTO_CREATE, flags)
            connection = conn
            binds++
            if (backgroundConnected != null) conn.onServiceConnected(component, root)
            return !reject
        }
        override fun unbindService(conn: ServiceConnection) { unbinds++ }
    }
    private fun start(worker: Handler = main) {
        monitor = Hs5VehicleMonitor(context, worker, main, {
            assertSame(Looper.getMainLooper(), Looper.myLooper())
            changes.add(monitor!!.sample)
        }, Hs5CanReader { canReads++; error("legacy CAN reader must never run") },
            rpmReader = Hs5RpmReader { _, _ ->
                assertSame(expectedLooper, Looper.myLooper())
                rpmReads++
                Hs5RpmReading(rpmPrimary = 700f, rpmOem = 700, rpcAvailable = true)
            }, record = { records.add(it) })
        monitor!!.start()
        if (worker === main) idle()
    }
    private fun idle(ms: Long = 0) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms)) }
    private fun connect() { connection.onServiceConnected(component, root); idle() }
    private fun emit(raw: Int, code: Int = 10, target: IBinder = listener!!) {
        val data = Parcel.obtain()
        try {
            data.writeInterfaceToken(Hs5VehiclePush.DESCRIPTOR)
            data.writeInt(raw)
            target.transact(code, data, null, IBinder.FLAG_ONEWAY)
        } finally { data.recycle() }
    }
    private fun kph() = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).speedKph

    @After fun close() { monitor?.stop(); idle(); DiagnosticLogging.setEnabled(false) }

    @Test fun oneInitialSnapshotThenPushOnlyWithoutIdleQueriesOrCanReads() {
        start(); connect()
        assertEquals(42f, kph())
        assertEquals(12, reads)
        val notifications = changes.size
        speed = 9999
        idle(60_000)
        assertEquals(42f, kph()) // Change-only callbacks must not expire unchanged values.
        assertEquals(12, reads)
        assertEquals(0, canReads)
        assertTrue(rpmReads > 2)
        assertEquals(notifications, changes.size)
        emit(125); idle()
        assertEquals(1.25f, kph())
        assertEquals(125, changes.last().speed.value)
        assertEquals(12, reads)
    }

    @Test fun pushDuringBootstrapWinsOverLaggingGetter() {
        duringSpeedRead = { emit(9100) }
        start(); connect()
        assertEquals(91f, kph())
        idle(5000)
        assertEquals(91f, kph())
    }

    @Test fun tirePushDuringBootstrapWinsEvenWhenGetterCompletesLater() {
        duringSpeedRead = {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(Hs5VehiclePush.DESCRIPTOR)
                data.writeInt(1); data.writeInt(233)
                listener!!.transact(47, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
            org.robolectric.shadows.ShadowSystemClock.advanceBy(Duration.ofMillis(10))
        }
        start(); connect()
        val car = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals(2.33f, car.tirePressureBar[1]!!, .00001f)
        assertNull(car.tirePressureBar[0])
        assertEquals(12, reads)
    }

    @Test fun unavailableGetterStillAllowsImmediatePushAndInvalidSpeedClearsNeedle() {
        failSpeed = true
        start(); connect()
        assertNull(kph())
        for ((raw, expected) in listOf(100 to 1f, 4300 to 43f, 26000 to 260f)) {
            emit(raw); idle(); assertEquals(expected, kph())
        }
        for (raw in listOf(-1, 26001, Int.MAX_VALUE)) {
            emit(raw); idle(); assertNull(kph())
        }
        emit(65); idle(); assertEquals(.65f, kph())
    }

    @Test fun stopAndReconnectClearReadingsAndRejectOldListeners() {
        start(); connect()
        val old = listener!!
        connection.onServiceDisconnected(component); idle()
        assertNull(kph())
        emit(8800, target = old); idle(); assertNull(kph())
        idle(3000); assertEquals(2, binds)
        speed = 100; connect()
        emit(9900, target = old); idle(); assertEquals(1f, kph())
        val live = listener!!
        monitor!!.stop(); idle()
        val stoppedReads = reads
        emit(8800, target = live); idle(30_000)
        assertEquals(Hs5VehicleSample(), monitor!!.sample)
        assertEquals(stoppedReads, reads)
        assertEquals(0, canReads)
        assertEquals(2, unregisters)
        assertEquals(2, unbinds)
    }

    @Test fun bindTimeoutAndRejectionRetryWithoutDataPolling() {
        start(); idle(8000)
        assertEquals(1, unbinds)
        idle(3000); assertEquals(2, binds)
        reject = true
        connection.onNullBinding(component); idle(3000)
        assertEquals(3, binds)
        idle(3000); assertEquals(4, binds)
        assertEquals(0, reads)
        assertEquals(0, canReads)
    }

    @Test fun unsupportedListenerDoesNotFallBackToPolling() {
        allowListen = false
        start(); connect(); idle(2000)
        assertEquals(0, reads)
        assertNull(kph())
        assertFalse(monitor!!.sample.pushConnected)
    }

    @Test fun individualTirePushesRetainWheelIdentityAndNeverPollAgain() {
        start(); connect()
        val before = reads
        fun tire(code: Int, wheel: Int, raw: Int) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(Hs5VehiclePush.DESCRIPTOR)
                data.writeInt(wheel); data.writeInt(raw)
                listener!!.transact(code, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
            idle()
        }
        tire(47, 2, 233); tire(49, 2, 1); tire(47, 99, 88)
        idle(60_000)
        val car = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals(2.33f, car.tirePressureBar[2]!!, .00001f)
        assertEquals(true, car.tireWarning[2])
        assertNull(car.tirePressureBar[0])
        assertEquals(before, reads)
        tire(47, 2, 255)
        assertNull(monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).tirePressureBar[2])
    }

    @Test fun otherDocumentedPushesUpdateWithoutAnyGetter() {
        start(); connect()
        emit(3, code = 11); emit(50, code = 30); emit(1, code = 29); idle()
        val car = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals("D", car.gear)
        assertEquals(.5f, car.fuelFraction)
        assertEquals(true, car.lowFuelWarning)
        assertEquals(700f, car.rpm!!, 0f)
        assertNull(car.assistance)
        assertEquals(12, reads)
    }

    @Test fun diagnosticsAreOptInRetainDriveAfterStopAndNeverFetchOnExport() {
        start(); connect(); emit(4300); idle()
        assertTrue(records.isEmpty())
        assertEquals("HS5 vehicle diagnostics disabled", monitor!!.report())
        DiagnosticLogging.setEnabled(true)
        emit(4400); idle(1000); emit(0); idle()
        monitor!!.stop(); idle(); speed = 0
        monitor!!.start(); idle(); connect()
        val report = monitor!!.report()
        assertTrue(report.contains("rawInt=4400"))
        assertTrue(report.contains("rangeValidKph=44.0"))
        assertTrue(report.contains("speed=0.0"))
        assertTrue(report.contains("rpmPollIntervalMs=750"))
        assertFalse(report.contains("canPolling=disabled"))
        val before = reads
        repeat(10) { monitor!!.report() }
        assertEquals(before, reads)
        records.forEach { assertEquals(it, DiagnosticRedactor.redact(it)) }
        DiagnosticLogging.setEnabled(false)
        val count = records.size
        emit(4500); idle(6000)
        assertEquals(count, records.size)
        DiagnosticLogging.setEnabled(true)
        assertTrue(monitor!!.report().contains("speedEvidence=[]"))
    }

    @Test fun binderRunsOnWorkerAndOnlyPublicationRunsOnMain() {
        val thread = HandlerThread("hs5-test").also { it.start() }
        val worker = Handler(thread.looper)
        expectedLooper = thread.looper
        backgroundConnected = CountDownLatch(1)
        try {
            start(worker)
            assertTrue(backgroundConnected!!.await(3, TimeUnit.SECONDS))
            val flushed = CountDownLatch(1)
            worker.post { flushed.countDown() }
            assertTrue(flushed.await(3, TimeUnit.SECONDS))
            idle()
            assertTrue(changes.isNotEmpty())
            assertEquals(42f, kph())
            monitor!!.stop()
            val stopped = CountDownLatch(1)
            worker.post { stopped.countDown() }
            assertTrue(stopped.await(3, TimeUnit.SECONDS))
            monitor = null
        } finally { thread.quitSafely() }
    }
}
