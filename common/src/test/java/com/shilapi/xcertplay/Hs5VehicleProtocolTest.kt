package com.shilapi.xcertplay

import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class Hs5VehicleProtocolTest {
    @Test fun systemTireWarningIsDeliveredAsAScalarNotificationAndTruncatedPayloadIsIgnored() {
        val events = mutableListOf<Pair<Int, Int>>()
        val listener = Hs5VehiclePush { code, raw -> events += code to raw }
        for (raw in listOf(null, 1, 0)) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(Hs5VehiclePush.DESCRIPTOR)
                if (raw != null) data.writeInt(raw)
                listener.transact(48, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
        }
        assertEquals(listOf(48 to 1, 48 to 0), events)
    }

    @Test fun speedHundredthsAreConvertedBeforeRangeCheckingWithoutRounding() {
        for ((raw, expected) in listOf(0 to 0f, 1 to .01f, 125 to 1.25f, 4300 to 43f, 26000 to 260f)) {
            assertEquals(expected, Hs5VehicleSpeed.kph(raw))
            assertEquals(expected, Hs5VehicleSample(speed = Hs5TimedInt(raw, 100)).vehicle(100, null).speedKph)
        }
        for (raw in listOf(-1, 26001, Int.MAX_VALUE)) assertNull(Hs5VehicleSpeed.kph(raw))
        assertNull(Hs5VehicleSpeed.kph(null))
    }

    @Test fun listenerValuesRemainUntilDisconnectButCannotKeepCanDataAlive() {
        val sample = Hs5VehicleSample(pushConnected = true, speed = Hs5TimedInt(125, 100),
            gear = Hs5TimedInt(3, 100), can = Hs5CanReadings(1200f, 90f), canAt = 100)
        val car = sample.vehicle(60_000, null)
        assertEquals(1.25f, car.speedKph)
        assertEquals("D", car.gear)
        assertNull(car.rpm)
        assertNull(car.coolantCelsius)
        assertNull(Hs5VehicleSample().vehicle(60_000, null).speedKph)
    }

    @Test fun contradictedRoadTestFormulasCannotDriveTheProductionGauges() {
        val frame = "0025fe0a460201049000000000000000000000000000000000".chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
        val coolant = "18ac4402000000003500000000000000000000000000000000".chunked(2)
            .map { it.toInt(16).toByte() }.toByteArray()
        val readings = Hs5CanDecoder.decode(frame, coolant)
        assertEquals(145.5f, readings.rpm)
        assertEquals(53f, readings.coolant)
        val parked = Hs5VehicleDisplayPolicy.apply(OemClusterVehicleState(
            speedKph = 0f, rpm = readings.rpm, gear = "P"))
        assertNull(parked.rpm)
        for (speed in listOf(1f, 42f, 100f, 260f)) {
            val candidate = OemClusterVehicleState(speedKph = speed, rpm = readings.rpm,
                coolantCelsius = readings.coolant, fuelFraction = .22f, gear = "P")
            val displayed = Hs5VehicleDisplayPolicy.apply(candidate)
            assertEquals(speed, displayed.speedKph)
            assertEquals(readings.rpm, displayed.rpm)
            assertNull(displayed.coolantCelsius)
            assertEquals(.22f, displayed.fuelFraction)
            assertEquals("P", displayed.gear)
        }
        assertEquals(0f, Hs5VehicleDisplayPolicy.apply(OemClusterVehicleState(speedKph = 0f)).speedKph)
        assertNull(Hs5VehicleDisplayPolicy.apply(OemClusterVehicleState()).speedKph)
    }

    @Test fun tirePushUsesTwoIntegersAndConvertsKpaToBarWithoutSubstitutingExamples() {
        val received = mutableListOf<Triple<Int, Int, Int>>()
        val listener = Hs5VehiclePush(receiveTire = { code, wheel, raw ->
            received.add(Triple(code, wheel, raw))
        }) { _, _ -> fail("tire event entered the scalar channel") }
        for ((code, wheel, raw) in listOf(Triple(47, 0, 233), Triple(49, 3, 1))) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(Hs5VehiclePush.DESCRIPTOR)
                data.writeInt(wheel); data.writeInt(raw)
                listener.transact(code, data, null, IBinder.FLAG_ONEWAY)
            } finally { data.recycle() }
        }
        assertEquals(listOf(Triple(47, 0, 233), Triple(49, 3, 1)), received)
        val sample = Hs5VehicleSample(pushConnected = true,
            tires = listOf(233, 0, 254, 255).map { Hs5TimedInt(it, 100) },
            tireWarnings = listOf(0, 1, 254, 255).map { Hs5TimedInt(it, 100) })
        val car = sample.vehicle(60_000, null)
        assertEquals(2.33f, car.tirePressureBar[0]!!, .00001f)
        // Initial getters and retained push readings both carry OEM-scaled kPa.
        for (connected in listOf(false, true)) {
            val pressures = Hs5VehicleSample(pushConnected = connected,
                tires = listOf(240, 260, 275, -65533).map { Hs5TimedInt(it, 100) }).vehicle(100, null)
            assertEquals(listOf(2.4f, 2.6f, 2.75f, null), pressures.tirePressureBar)
        }
        assertEquals(listOf(null, 2.54f, 2.55f), car.tirePressureBar.drop(1))
        assertEquals(listOf(false, true, null, null), car.tireWarning)
        assertTrue(Hs5VehicleSample().vehicle(100, null).tirePressureBar.all { it == null })
    }

    private fun engine(rpm: Int) = ByteArray(9).apply {
        val raw = rpm * 4
        this[4] = raw.toByte()
        this[5] = (raw shr 8).toByte()
    }

    @Test fun decoderUsesDocumentedPayloadOffsetsAndQuarterRpmScale() {
        val coolant = ByteArray(9).apply { this[0] = 24; this[8] = 93 }
        assertEquals(Hs5CanReadings(2150f, 93f), Hs5CanDecoder.decode(engine(2150), coolant))
        assertEquals(8000f, Hs5CanDecoder.decode(engine(8000), null).rpm)
        assertNull(Hs5CanDecoder.decode(engine(8001), null).rpm)
    }

    @Test fun truncatedWrongIdAndUnavailableCanFramesCannotBecomeReadings() {
        assertEquals(Hs5CanReadings(), Hs5CanDecoder.decode(ByteArray(8), ByteArray(8)))
        assertNull(Hs5CanDecoder.decode(engine(800).apply { this[0] = 1 }, null).rpm)
        for (value in listOf(151, 255)) {
            assertNull(Hs5CanDecoder.decode(null,
                ByteArray(9).apply { this[0] = 24; this[8] = value.toByte() }).coolant)
        }
        assertNull(Hs5CanDecoder.decode(null, ByteArray(9).apply { this[8] = 90 }).coolant)
    }

    @Test fun stationaryParkGateHidesLowNoiseButRetainsActualIdleRpm() {
        val parked = Hs5VehicleSample(speed = Hs5TimedInt(0, 100), gear = Hs5TimedInt(0, 100),
            can = Hs5CanReadings(300f, 90f), canAt = 100)
        assertNull(parked.vehicle(100, null).rpm)
        assertEquals(800f, parked.copy(can = Hs5CanReadings(800f)).vehicle(100, null).rpm)
        assertEquals(300f, parked.copy(gear = Hs5TimedInt()).vehicle(100, null).rpm)
        assertEquals(300f, parked.copy(speed = Hs5TimedInt()).vehicle(100, null).rpm)
        assertEquals(300f, parked.copy(speed = Hs5TimedInt(200, 100)).vehicle(100, null).rpm)
    }

    @Test fun speedPushesDoNotExtendCanOrGearFreshnessAndUnknownsAreNotGuessed() {
        val sample = Hs5VehicleSample(speed = Hs5TimedInt(8800, 4000), gear = Hs5TimedInt(0, 100),
            fuel = Hs5TimedInt(50, 4000), seatBelt = Hs5TimedInt(1, 4000), brake = Hs5TimedInt(1, 4000),
            can = Hs5CanReadings(2000f, 90f), canAt = 100)
        val car = sample.vehicle(4000, true)
        assertEquals(88f, car.speedKph)
        assertNull(car.rpm)
        assertNull(car.coolantCelsius)
        assertNull(car.gear)
        assertEquals(.5f, car.fuelFraction)
        assertEquals(false, car.seatBeltWarning)
        assertNull(car.parkingBrake)
        assertNull(car.assistance)
        assertEquals(true, car.positionLights)
        assertEquals("D", sample.copy(gear = Hs5TimedInt(3, 4000)).vehicle(4000, null).gear)
        assertEquals("P", sample.copy(gear = Hs5TimedInt(0, 4000)).vehicle(4000, null).gear)
        assertNull(sample.vehicle(6501, null).speedKph)
        assertNull(Hs5TimedInt(5).fresh(0))
        assertNull(Hs5TimedInt(5, 500).fresh(499))
    }

    @Test fun individualRejectedAndMalformedGettersDoNotSuppressTheOtherReadings() {
        val calls = mutableListOf<Int>()
        val adapter = Hs5VehicleBinder(object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(QisiPositionLightBinder.VEHICLE_STATE)
                calls.add(code)
                if (code == 31) return false
                if (code == 6) { reply!!.writeNoException(); return true }
                if (code == 4) assertEquals(0, data.readInt())
                assertEquals(0, data.dataAvail())
                reply!!.writeNoException()
                reply.writeInt(42)
                return true
            }
        })
        assertNull(adapter.read(31))
        assertNull(adapter.read(31))
        assertNull(adapter.read(6))
        assertEquals(42, adapter.read(7))
        assertEquals(42, adapter.read(4, 0))
        assertEquals(listOf(31, 6, 7, 4), calls)
    }

    @Test fun subscriptionUsesDocumentedTransactionsAndTheSameListenerForCleanup() {
        val listener = Hs5VehiclePush { _, _ -> }
        val codes = mutableListOf<Int>()
        val adapter = Hs5VehicleBinder(object : Binder() {
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(QisiPositionLightBinder.VEHICLE_STATE)
                codes.add(code)
                assertSame(listener, data.readStrongBinder())
                assertEquals(0, data.dataAvail())
                reply!!.writeNoException()
                return true
            }
        })
        assertTrue(adapter.listen(listener, true))
        assertTrue(adapter.listen(listener, false))
        assertEquals(listOf(42, 43), codes)
    }

    @Test fun pushSupportsOneWayCallbacksAndIgnoresUnknownOrIncompletePayloads() {
        val values = mutableListOf<Pair<Int, Int>>()
        val listener = Hs5VehiclePush { code, raw -> values.add(code to raw) }
        fun send(code: Int, raw: Int?, descriptor: String = Hs5VehiclePush.DESCRIPTOR) {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(descriptor)
                if (raw != null) data.writeInt(raw)
                assertTrue(listener.transact(code, data, null, IBinder.FLAG_ONEWAY))
            } finally { data.recycle() }
        }
        send(10, 80); send(11, 0); send(30, 25); send(29, 1)
        send(10, null); send(28, 1); send(10, 4, "unexpected.interface")
        assertEquals(listOf(10 to 80, 11 to 0, 30 to 25, 29 to 1), values)
    }

    @Test fun documentedSensorGearsFuelAndBeltHaveTheirOwnNamespaces() {
        val gears = listOf("P", "R", "N", "D", "S", "M", "W", "E", "B")
        gears.forEachIndexed { raw, expected ->
            assertEquals(expected, Hs5VehicleSample(gear = Hs5TimedInt(raw, 100)).vehicle(100, null).gear)
        }
        for (raw in listOf(0, 1, 50, 100)) {
            assertEquals(raw / 100f, Hs5VehicleSample(fuel = Hs5TimedInt(raw, 100)).vehicle(100, null).fuelFraction)
        }
        for (raw in listOf(0, 1)) {
            val car = Hs5VehicleSample(seatBelt = Hs5TimedInt(raw, 100),
                passengerSeatBelt = Hs5TimedInt(0, 100), lowBeam = Hs5TimedInt(raw, 100),
                fuelWarning = Hs5TimedInt(raw, 100), brake = Hs5TimedInt(1, 100)).vehicle(100, null)
            assertEquals(raw == 0, car.seatBeltWarning)
            assertEquals(raw == 1, car.lowBeam)
            assertEquals(raw == 1, car.lowFuelWarning)
            assertNull(car.parkingBrake)
        }
        assertEquals(false, Hs5VehicleSample(seatBelt = Hs5TimedInt(1, 100),
            passengerSeatBelt = Hs5TimedInt(0, 100)).vehicle(100, null).seatBeltWarning)
        assertEquals(true, Hs5VehicleSample(fuelWarning = Hs5TimedInt(2, 100)).vehicle(100, null).lowFuelWarning)
    }

    @Test fun errorSentinelsInitialFuelAndNonFiniteTemperaturesNeverBecomeReadings() {
        for (raw in listOf(-1, -2, -3, -65535, -65534, -65533, -65532, 254, 255)) {
            val car = Hs5VehicleSample(fuel = Hs5TimedInt(raw, 100), gear = Hs5TimedInt(raw, 100),
                seatBelt = Hs5TimedInt(raw, 100), lowBeam = Hs5TimedInt(raw, 100),
                fuelWarning = Hs5TimedInt(raw, 100), driveMode = Hs5TimedInt(raw, 100)).vehicle(100, null)
            assertNull(car.gear); assertNull(car.fuelFraction); assertNull(car.seatBeltWarning)
            assertNull(car.lowBeam); assertNull(car.lowFuelWarning); assertNull(car.driveModeCode)
        }
        for (raw in listOf(Float.NaN, Float.POSITIVE_INFINITY, -65533f, -51f, 71f)) {
            assertNull(Hs5VehicleSample(outside = Hs5TimedFloat(raw, 100)).vehicle(100, null).outsideCelsius)
        }
        val sample = Hs5VehicleSample(outside = Hs5TimedFloat(-12.5f, 100),
            lowBeam = Hs5TimedInt(1, 100), driveMode = Hs5TimedInt(2, 100), fuel = Hs5TimedInt(50, 100),
            fuelWarning = Hs5TimedInt(1, 100), seatBelt = Hs5TimedInt(0, 100))
        assertEquals(-12.5f, sample.vehicle(100, null).outsideCelsius)
        assertEquals(2, sample.vehicle(100, null).driveModeCode)
        val expired = sample.copy(speed = Hs5TimedInt(80, 2601)).vehicle(2601, null)
        assertNull(expired.outsideCelsius); assertNull(expired.lowBeam); assertNull(expired.driveModeCode)
        assertNull(expired.fuelFraction); assertNull(expired.lowFuelWarning); assertNull(expired.seatBeltWarning)
        assertNull(Hs5TimedFloat(17f, 500).fresh(499))
    }

    @Test fun assistanceFunctionStatesAndWarningLevelsDoNotInventTargetsOrLanes() {
        for (raw in 0..3) {
            val car = Hs5VehicleSample(acc = Hs5TimedInt(raw, 100), fcw = Hs5TimedInt(raw, 100),
                aeb = Hs5TimedInt(raw, 100), ldw = Hs5TimedInt(raw, 100), lka = Hs5TimedInt(raw, 100),
                fcwWarning = Hs5TimedInt(2, 100), aebWarning = Hs5TimedInt(0, 100)).vehicle(100, null)
            assertEquals(OemClusterAssistanceStatus(raw, raw, raw, raw, raw, 2, 0), car.assistanceStatus)
            assertNull(car.assistance)
        }
        assertNull(Hs5VehicleSample(acc = Hs5TimedInt(-2, 100), fcwWarning = Hs5TimedInt(255, 100))
            .vehicle(100, null).assistanceStatus)
        assertNull(Hs5VehicleSample(acc = Hs5TimedInt(2, 100)).vehicle(2601, null).assistanceStatus)
    }

    @Test fun extensionQueriesValidateDescriptorsAndReadFloatInsteadOfIntegerBits() {
        val queries = mutableListOf<Int>()
        val atc = object : Binder() {
            init { attachInterface(null, Hs5VehicleExtensions.Module.ATC.descriptor) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(Hs5VehicleExtensions.Module.ATC.descriptor)
                assertEquals(93, code); assertEquals(0, data.dataAvail())
                reply!!.writeNoException(); reply.writeFloat(17.5f)
                return true
            }
        }
        var allowBcm = false
        val bcm = object : Binder() { init { attachInterface(null, Hs5VehicleExtensions.Module.BCM.descriptor) } }
        val root = object : Binder() {
            init { attachInterface(null, QisiPositionLightBinder.ROOT) }
            override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
                data.enforceInterface(QisiPositionLightBinder.ROOT)
                assertEquals(1, code)
                val id = data.readInt(); queries.add(id)
                assertEquals(0, data.dataAvail())
                reply!!.writeNoException()
                reply.writeStrongBinder(when (id) { 33 -> atc; 25 -> if (allowBcm) bcm else atc; else -> null })
                return true
            }
        }
        var time = 100L
        val extensions = Hs5VehicleExtensions(root) { time }
        val manager = extensions.manager(Hs5VehicleExtensions.Module.ATC)!!
        assertEquals(17.5f, manager.readFloat(93))
        assertSame(manager, extensions.manager(Hs5VehicleExtensions.Module.ATC))
        assertNull(extensions.manager(Hs5VehicleExtensions.Module.BCM))
        allowBcm = true
        time = 10_099
        assertNull(extensions.manager(Hs5VehicleExtensions.Module.BCM))
        time = 10_100
        assertNotNull(extensions.manager(Hs5VehicleExtensions.Module.BCM))
        assertEquals(listOf(33, 25, 25), queries)
        assertEquals("ATC=true BCM=true DMS=false IFC=false HUD=false", extensions.report())
    }

    @Test fun systemRpcUsesOnlyReadOpcodeAndTheTwoRequiredZeroFilledRequests() {
        alfusos.rpc.RpcManager.requests.clear()
        val reader = Hs5RpcCanReader()
        val result = reader.read()
        assertEquals(Hs5CanReadings(800f, 90f), result)
        assertEquals("rpcAvailable=true", reader.diagnosticStatus())
        assertEquals(listOf(0, 24), alfusos.rpc.RpcManager.requests.map { it[0].toInt() })
        assertTrue(alfusos.rpc.RpcManager.requests.all { it.size == 26 && it.drop(1).all { b -> b == 0.toByte() } })
    }

    @Test fun hudDisplayEnumsArePreservedSeparatelyAndExpireWithoutInventingObjectDetection() {
        val sample = Hs5VehicleSample(hudFcwDisplay = Hs5TimedInt(7, 100),
            hudAdasDisplay = Hs5TimedInt(1, 100), cruiseMode = Hs5TimedInt(3, 100),
            aebDisplay = Hs5TimedInt(0, 100), fcwWarning = Hs5TimedInt(2, 100))
        val car = sample.vehicle(100, null)
        assertEquals(7, car.assistanceStatus!!.hudFcwDisplay)
        assertEquals(2, car.assistanceStatus!!.fcwWarning)
        assertNull(car.assistance)
        assertNull(sample.vehicle(2601, null).assistanceStatus)
        assertNull(Hs5VehicleSample(hudFcwDisplay = Hs5TimedInt(-2, 100),
            hudAdasDisplay = Hs5TimedInt(-65534, 100)).vehicle(100, null).assistanceStatus)
    }

    @Test fun objectPresenceRequiresFreshBinaryDetectionRatherThanWarningOrCruiseEnums() {
        for (raw in listOf(0, 1)) {
            val sample = Hs5VehicleSample(leadVehicle = Hs5LeadVehicleReading(raw), leadVehicleAt = 100)
            assertEquals(raw == 1, sample.vehicle(100, null).assistance!!.leadVehicleDetected)
            assertNull(sample.vehicle(2601, null).assistance)
            assertNull(sample.vehicle(99, null).assistance)
        }
        for (raw in listOf(null, -1, 2, 254, 255)) {
            assertNull(Hs5VehicleSample(leadVehicle = Hs5LeadVehicleReading(raw), leadVehicleAt = 100)
                .vehicle(100, null).assistance!!.leadVehicleDetected)
        }
    }

    @Test fun freshAdasReadingCarriesLaneBitsAndUnknownsThroughToTheCluster() {
        val allLanes = Hs5VehicleSample(leadVehicle = Hs5LeadVehicleReading(
            raw = 0, laneDetectState = 2, leftLaneDetected = false, rightLaneDetected = true,
        ), leadVehicleAt = 100).vehicle(100, null).assistance!!
        assertEquals(false, allLanes.leadVehicleDetected)
        assertEquals(false, allLanes.leftLaneDetected)
        assertEquals(true, allLanes.rightLaneDetected)

        val unavailable = Hs5VehicleSample(leadVehicleAt = 100).vehicle(100, null).assistance!!
        assertNull(unavailable.leadVehicleDetected)
        assertNull(unavailable.leftLaneDetected)
        assertNull(unavailable.rightLaneDetected)
        assertNull(Hs5VehicleSample(leadVehicleAt = 100).vehicle(2601, null).assistance)
    }
}
