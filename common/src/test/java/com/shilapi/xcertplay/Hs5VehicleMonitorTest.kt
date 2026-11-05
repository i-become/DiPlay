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
    private var drivingEnabled = false
    private var drivingReads = 0
    private val tirePressures = IntArray(4)
    private val tireWarnings = IntArray(4)
    private var duringSpeedRead: (() -> Unit)? = null
    private var failSpeed = false
    private var reject = false
    private var allowListen = true
    private var lead = Hs5LeadVehicleReading()
    private val optionalManagers = mutableMapOf<Int, IBinder>()
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
            assertTrue("Unexpected polled field $code", code in listOf(7, 6, 31, 58, 26, 80, 25, 24, 2))
            if (code in listOf(25, 24, 2)) { assertTrue(drivingEnabled); drivingReads++ }
            val wheel = if (code == 26 || code == 80) data.readInt().also { assertTrue(it in 0..3) } else 0
            assertEquals(0, data.dataAvail())
            reads++
            if (code == 7) duringSpeedRead?.invoke()
            if (code == 7 && failSpeed) {
                reply!!.writeException(SecurityException("getter unavailable"))
                return true
            }
            reply!!.writeNoException()
            reply.writeInt(when (code) {
                7 -> speed
                25 -> 86
                24 -> 12680
                2 -> 620
                26 -> tirePressures[wheel]
                80 -> tireWarnings[wheel]
                else -> 0
            })
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
            val id = data.readInt()
            assertTrue(id in listOf(15, 23, 46))
            reply!!.writeNoException()
            reply.writeStrongBinder(if (id == 15) state else optionalManagers[id])
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
            }, record = { records.add(it) }, leadVehicleReader = Hs5LeadVehicleReader {
                assertSame(expectedLooper, Looper.myLooper())
                lead
            }, drivingInfoEnabled = { drivingEnabled })
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

    @Test fun drivingInformationReadsOnlyOnDemandAndVehicleEventsAndReceivesRangePushes() {
        drivingEnabled = true
        start(); connect()
        assertEquals(3, drivingReads)
        fun driving() = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals(86f, driving().averageFuelConsumption!!, .001f)
        assertEquals(12680f, driving().totalMileageKm!!, .001f)
        assertEquals(620, driving().remainingMileageKm)
        idle(10000)
        assertEquals("Idle must not start a driving-info poll loop", 3, drivingReads)
        emit(4200); idle(1000)
        assertEquals(6, drivingReads)
        emit(590, code = 31); idle()
        assertEquals(590, driving().remainingMileageKm)
        emit(4400); idle(1000)
        assertEquals("A stale getter must not replace a newer range push", 590, driving().remainingMileageKm)
        drivingEnabled = false
        idle(2000)
        val before = drivingReads
        emit(4300); idle(2000)
        monitor!!.requestDrivingInfo(); idle()
        assertEquals("Hidden driving widget must not read its getters", before, drivingReads)
    }

    @Test fun actualObjectDetectionReachesTheClusterAndIsClearedOnLossAndDisconnect() {
        lead = Hs5LeadVehicleReading(1, "test-ObjectDetection")
        start(); connect()
        fun assistance() = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).assistance
        assertEquals(true, assistance()!!.leadVehicleDetected)
        lead = Hs5LeadVehicleReading(0, "test-ObjectDetection")
        idle(750)
        assertEquals(false, assistance()!!.leadVehicleDetected)
        lead = Hs5LeadVehicleReading(7, "unknown")
        idle(750)
        assertNull(assistance()!!.leadVehicleDetected)
        lead = Hs5LeadVehicleReading(1, "test-ObjectDetection")
        idle(750)
        assertEquals(true, assistance()!!.leadVehicleDetected)
        connection.onServiceDisconnected(component); idle()
        assertNull(assistance())
    }

    @Test fun optionalHudAndIfcManagersUseReadOnlyGettersAndReleaseTheirListeners() {
        val callbacks = mutableMapOf<Hs5AssistancePush.Module, IBinder>()
        val removed = mutableListOf<Hs5AssistancePush.Module>()
        val getterCalls = mutableMapOf<Hs5AssistancePush.Module, MutableList<Int>>()
        for (module in Hs5AssistancePush.Module.entries) {
            optionalManagers[module.id] = object : Binder() {
                init { attachInterface(null, module.descriptor) }
                override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                    data.enforceInterface(module.descriptor)
                    if (code == module.register || code == module.unregister) {
                        val callback = data.readStrongBinder()!!
                        if (code == module.register) callbacks[module] = callback else {
                            assertSame(callbacks[module], callback)
                            removed += module
                        }
                    } else {
                        val allowed = if (module == Hs5AssistancePush.Module.HUD) listOf(10, 17)
                            else listOf(5, 2, 7, 8, 15, 14, 27)
                        assertTrue("Unsafe/unknown transaction $module/$code", code in allowed)
                        getterCalls.getOrPut(module) { mutableListOf() }.add(code)
                    }
                    assertEquals(0, data.dataAvail())
                    reply!!.writeNoException()
                    if (code != module.register && code != module.unregister) reply.writeInt(1)
                    return true
                }
            }
        }
        start(); connect()
        assertEquals(2, callbacks.size)
        assertEquals(9, getterCalls.values.sumOf { it.size })
        fun push(module: Hs5AssistancePush.Module, code: Int, raw: Int) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(module.listenerDescriptor); data.writeInt(raw)
                assertTrue(callbacks.getValue(module).transact(code, data, null, IBinder.FLAG_ONEWAY))
            } finally { data.recycle() }
            idle()
        }
        push(Hs5AssistancePush.Module.HUD, 13, 7)
        push(Hs5AssistancePush.Module.IFC, 2, 3)
        assertEquals(7, monitor!!.sample.hudFcwDisplay.value)
        assertEquals(3, monitor!!.sample.acc.value)
        assertNull(monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).assistance!!.leadVehicleDetected)
        idle(5000)
        assertEquals(9, getterCalls.values.sumOf { it.size })
        monitor!!.stop(); idle()
        assertEquals(2, removed.size)
        push(Hs5AssistancePush.Module.HUD, 13, 2)
        assertEquals(Hs5VehicleSample(), monitor!!.sample)
    }

    @Test fun vehicleEventsRefreshTiresWithoutIdleQueriesOrLegacyCanReads() {
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
        assertEquals(20, reads)
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

    @Test fun individualTirePushesRetainWheelIdentityAndUnknownIndicesRefreshGetters() {
        start(); connect()
        val before = reads
        fun tire(code: Int, wheel: Int, raw: Int) {
            if (wheel in 0..3) {
                if (code == 47) tirePressures[wheel] = raw else tireWarnings[wheel] = raw
            }
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
        assertEquals(before + 8, reads)
        tire(47, 2, 255)
        assertNull(monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).tirePressureBar[2])
        tire(47, 2, 260)
        assertEquals(2.6f, monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).tirePressureBar[2])
    }

    @Test fun tireGettersRefreshAndNotifyTheVisibleClusterWithoutDedicatedTireCallbacks() {
        tirePressures.fill(240)
        start(); connect()
        assertEquals(listOf(2.4f, 2.4f, 2.4f, 2.4f), changes.last().vehicle(0, null).tirePressureBar)
        tirePressures[0] = 230
        tirePressures[3] = 260
        tireWarnings[3] = 1
        emit(1500); idle(1000)
        val displayed = changes.last().vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals(listOf(2.3f, 2.4f, 2.4f, 2.6f), displayed.tirePressureBar)
        assertEquals(listOf(false, false, false, true), displayed.tireWarning)
        assertEquals(1, binds)
        assertEquals(0, unregisters)
        assertEquals(20, reads)
        val notifications = changes.size
        idle(1000)
        emit(1500, code = 10); idle()
        // Identical tire values do not independently request another redraw.
        val afterSpeedNotification = changes.size
        assertEquals(notifications + 1, afterSpeedNotification)
        idle(60_000)
        assertEquals(afterSpeedNotification, changes.size)
        assertEquals(28, reads)
    }

    @Test fun speedBurstsCoalesceTireReadsWithoutPostponingThemAndIdleDoesNotPoll() {
        tirePressures.fill(240)
        start(); connect()
        tirePressures[1] = 245
        repeat(10) {
            repeat(20) { emit(2000) }
            idle(100)
        }
        assertEquals(20, reads)
        assertEquals(2.45f, monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null).tirePressureBar[1])
        val afterBurst = reads
        idle(60_000)
        assertEquals(afterBurst, reads)
        // Gear changes also refresh tires, including while parked with no speed event.
        tirePressures[1] = 250
        emit(0, code = 11); idle()
        assertEquals(2.5f, changes.last().vehicle(SystemClock.elapsedRealtime(), null).tirePressureBar[1])
        assertEquals(afterBurst + 8, reads)
    }

    @Test fun systemTireWarningRefreshesAllWheelsImmediatelyAndStopCancelsDeferredReads() {
        tirePressures.fill(240)
        start(); connect()
        tireWarnings[2] = 1
        emit(1, code = 48); idle()
        assertEquals(20, reads) // No need to wait for the regular vehicle-event throttle.
        assertEquals(true, changes.last().vehicle(SystemClock.elapsedRealtime(), null).tireWarning[2])
        emit(100); idle() // Queues a refresh for later.
        monitor!!.stop(); idle()
        idle(60_000)
        assertEquals(20, reads)
        assertEquals(Hs5VehicleSample(), monitor!!.sample)
    }

    @Test fun otherDocumentedPushesUpdateWithoutAnyGetter() {
        start(); connect()
        emit(3, code = 11); emit(50, code = 30); emit(1, code = 29); idle()
        val car = monitor!!.sample.vehicle(SystemClock.elapsedRealtime(), null)
        assertEquals("D", car.gear)
        assertEquals(.5f, car.fuelFraction)
        assertEquals(true, car.lowFuelWarning)
        assertEquals(700f, car.rpm!!, 0f)
        assertNull(car.assistance!!.leadVehicleDetected)
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
            // Connect queues an initial RPM read, which can replace a pending UI
            // notification. Drain that queued work before running the main looper.
            worker.post { worker.post { flushed.countDown() } }
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
