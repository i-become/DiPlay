package com.shilapi.xcertplay

import android.os.Build
import android.os.Binder
import android.os.IBinder
import android.os.Parcel
import android.os.RemoteException
import android.os.SystemClock
import java.lang.reflect.Method
import com.hs5.vehicle.sdk.Hs5VehicleFieldSemantics
import com.hs5.vehicle.sdk.can.CanPayloadDecoder
import com.hs5.vehicle.sdk.can.CanRpcProbe
import com.hs5.vehicle.sdk.can.CanSignalRuleDecoder
import org.json.JSONObject

/** Read-only subset of the supplied HS5 322200 integration kit. No vendor library is bundled. */
internal class Hs5VehicleBinder(
    private val remote: IBinder,
    private val descriptor: String = QisiPositionLightBinder.VEHICLE_STATE,
) {
    private val unsupported = mutableSetOf<Int>()

    fun read(code: Int, argument: Int? = null): Int? {
        if (code in unsupported) return null
        return exchange(code, { if (argument != null) writeInt(argument) }) {
            if (dataAvail() < 4) null else readInt()
        }
    }

    fun readFloat(code: Int): Float? {
        if (code in unsupported) return null
        return exchange(code, {}) { if (dataAvail() < 4) null else readFloat() }
    }

    fun listen(listener: IBinder, register: Boolean): Boolean =
        exchange(if (register) 42 else 43, { writeStrongBinder(listener) }) { true } ?: false

    val alive: Boolean get() = remote.isBinderAlive

    private fun <T> exchange(code: Int, write: Parcel.() -> Unit, read: Parcel.() -> T): T? {
        val data = Parcel.obtain()
        val reply = Parcel.obtain()
        return try {
            data.writeInterfaceToken(descriptor)
            data.write()
            if (!remote.transact(code, data, reply, 0)) {
                unsupported.add(code)
                null
            } else if (reply.dataAvail() < 4) null else {
                reply.readException()
                reply.read()
            }
        } catch (_: RemoteException) {
            null
        } catch (_: RuntimeException) {
            // One unavailable firmware getter must not suppress the other instruments.
            null
        } finally {
            data.recycle()
            reply.recycle()
        }
    }

    companion object {
        /** Optional managers fail independently; a missing ATC must not drop the speed feed. */
        fun query(root: IBinder, id: Int, descriptor: String): Hs5VehicleBinder? {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                if (root.interfaceDescriptor != QisiPositionLightBinder.ROOT) return null
                data.writeInterfaceToken(QisiPositionLightBinder.ROOT)
                data.writeInt(id)
                if (!root.transact(1, data, reply, 0) || reply.dataAvail() < 4) return null
                reply.readException()
                val manager = reply.readStrongBinder() ?: return null
                if (manager.interfaceDescriptor != descriptor) return null
                Hs5VehicleBinder(manager, descriptor)
            } catch (_: RemoteException) {
                null
            } catch (_: RuntimeException) {
                null
            } finally {
                data.recycle()
                reply.recycle()
            }
        }
    }
}

/** Query only documented read-only extensions, with bounded retries per manager. */
internal class Hs5VehicleExtensions(private val root: IBinder, private val clock: () -> Long) {
    enum class Module(val id: Int, val descriptor: String) {
        ATC(33, "ltd.qisi.adapterproxy.ICarATCManager"),
        BCM(25, "ltd.qisi.adapterproxy.ICarBCMManager"),
        DMS(18, "ltd.qisi.adapterproxy.ICarDMSManager"),
        IFC(46, "ltd.qisi.adapterproxy.ICarIFCManager"),
        HUD(23, "ltd.qisi.adapterproxy.ICarHUDManager"),
    }

    private val managers = arrayOfNulls<Hs5VehicleBinder>(Module.entries.size)
    private val retryAt = LongArray(Module.entries.size)
    @Volatile private var available = 0

    fun manager(module: Module): Hs5VehicleBinder? {
        val index = module.ordinal
        managers[index]?.takeIf { it.alive }?.let { return it }
        available = available and (1 shl index).inv()
        managers[index] = null
        if (clock() < retryAt[index]) return null
        retryAt[index] = clock() + 10_000L
        return Hs5VehicleBinder.query(root, module.id, module.descriptor)?.also {
            managers[index] = it
            available = available or (1 shl index)
        }
    }

    // Called only inside the diagnostics opt-in; no Binder calls or mutable collection reads.
    fun report(): String {
        val mask = available
        return Module.entries.joinToString(" ") { "${it.name}=${mask and (1 shl it.ordinal) != 0}" }
    }
}

internal class Hs5VehiclePush(
    private val receiveTire: (Int, Int, Int) -> Unit = { _, _, _ -> },
    private val receive: (Int, Int) -> Unit,
) : Binder() {
    init { attachInterface(null, DESCRIPTOR) }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        if (code == INTERFACE_TRANSACTION) {
            reply?.writeString(DESCRIPTOR)
            return true
        }
        if (code !in 1..0x00ffffff) return super.onTransact(code, data, reply, flags)
        try {
            data.enforceInterface(DESCRIPTOR)
            if (code in READINGS && data.dataAvail() >= 4) receive(code, data.readInt())
            if ((code == 47 || code == 49) && data.dataAvail() >= 8) {
                receiveTire(code, data.readInt(), data.readInt())
            }
            // Other firmware notifications are acknowledged but have no verified display mapping.
            reply?.writeNoException()
        } catch (_: RuntimeException) {
            // Bad/unknown payloads must not crash a Binder thread, including one-way calls.
        }
        return true
    }

    companion object {
        const val DESCRIPTOR = "ltd.qisi.adapterproxy.listener.IVehicleCarStateChangedListener"
        private val READINGS = setOf(10, 11, 29, 30)
    }
}

internal data class Hs5RpmReading(
    val rpmPrimary: Float? = null,
    val rpmOem: Int? = null,
    val rpmRule: Int? = null,
    val rpmDebugByte7: Int? = null,
    val note: String? = null,
    val rpcAvailable: Boolean = false,
)

internal data class Hs5CanReadings(
    val rpm: Float? = null,
    val coolant: Float? = null,
    val rpmOem: Int? = null,
    val rpmRule: Int? = null,
    val rpmDebugByte7: Int? = null,
    val note: String? = null,
    val rpcAvailable: Boolean = false,
)

internal fun interface Hs5RpmReader {
    fun read(speedRaw: Int?, gearRaw: Int?): Hs5RpmReading
}

/** SDK OEM -> Rule -> verified EMS_3 decode, with all Binder/RPC work on the monitor worker. */
internal class Hs5SdkRpmReader : Hs5RpmReader {
    override fun read(speedRaw: Int?, gearRaw: Int?): Hs5RpmReading {
        if (Build.VERSION.SDK_INT < 28 || speedRaw == null || gearRaw == null) {
            return Hs5RpmReading(note = "adapter vehicle speed/gear unavailable")
        }
        return try {
            val vehicle = JSONObject().put("speed", speedRaw).put("gear", gearRaw)
            val enriched = Hs5VehicleFieldSemantics.enrichSnapshot(vehicle)
            val can = CanRpcProbe.probe(intArrayOf(0))
            val decoded = CanPayloadDecoder.fromCanRpcJson(can, enriched)
            val ems3 = CanSignalRuleDecoder.frameFromCanJson(can, 0)
            val ems3Rpm = CanPayloadDecoder.rpmFromEms3Bytes12Le(ems3)
            val rpmBitFallback = CanPayloadDecoder.rpmFromEms3IntelBit24(ems3)
            val rpmMotorolaFallback = CanPayloadDecoder.rpmFromEms3Motorola24(ems3)
            val unfilteredRpm = decoded.rpmOem ?: decoded.engineSpeedRule.physical
                ?: ems3Rpm ?: rpmBitFallback ?: rpmMotorolaFallback
            val hiddenInPark = CanPayloadDecoder.shouldHideRpmInParkIdle(enriched, unfilteredRpm)
            val rpmPrimary = unfilteredRpm.takeUnless { hiddenInPark }
            val note = when {
                hiddenInPark -> "P挡且车速≈0：转速已隐藏（carIGN 不代表发动机运转）"
                decoded.rpmPrimary == null && ems3Rpm != null -> "EMS_3 payload[1..2] 小端 ×0.25 回退"
                else -> decoded.note
            }
            Hs5RpmReading(
                rpmPrimary = rpmPrimary?.toFloat(),
                rpmOem = decoded.rpmOem,
                rpmRule = decoded.engineSpeedRule.physical,
                rpmDebugByte7 = decoded.rpmDebugByte7,
                note = note,
                rpcAvailable = can.optBoolean("rpc_available", false),
            )
        } catch (error: Throwable) {
            Hs5RpmReading(note = "RPM probe failed: ${error.javaClass.simpleName}")
        }
    }
}

/** HS5 speed units confirmed by the driver: hundredths of km/h. Convert before validation. */
internal object Hs5VehicleSpeed {
    fun kph(raw: Int?): Float? = raw?.takeIf { it in 0..26_000 }?.div(100f)
}

/** Speed stays on its documented hundredths-of-km/h scale; only verified RPM can reach the UI. */
internal object Hs5VehicleDisplayPolicy {
    fun apply(candidate: OemClusterVehicleState): OemClusterVehicleState {
        val hideParkIdle = candidate.gear == "P" && candidate.speedKph != null &&
            candidate.speedKph <= 0.5f && candidate.rpm != null && candidate.rpm <= 350f
        return candidate.copy(
            rpm = candidate.rpm.takeUnless { hideParkIdle },
            coolantCelsius = null,
        )
    }
}

internal object Hs5CanDecoder {
    fun decode(engine: ByteArray?, coolant: ByteArray?): Hs5CanReadings {
        val rpm = engine?.takeIf { valid(it, 0) }?.let {
            ((it[4].toInt() and 255) or ((it[5].toInt() and 255) shl 8)) * 0.25f
        }?.takeIf { it in 0f..8000f }
        val temperature = coolant?.takeIf { valid(it, 24) }?.let { (it[8].toInt() and 255).toFloat() }
            ?.takeIf { it in 0f..150f }
        return Hs5CanReadings(rpm, temperature)
    }

    private fun valid(frame: ByteArray, id: Int) = frame.size >= 9 && (frame[0].toInt() and 255) == id
}

internal fun interface Hs5CanReader {
    fun read(): Hs5CanReadings
    fun diagnosticStatus(): String = "rpcAvailable=unknown"
}

/** Resolve the system framework lazily on the worker; never put an OEM jar in the APK. */
internal class Hs5RpcCanReader(private val clock: () -> Long = SystemClock::elapsedRealtime) : Hs5CanReader {
    private var instance: Any? = null
    @Volatile private var method: Method? = null
    private var retryAt = 0L
    @Volatile private var engineFrame: ByteArray? = null
    @Volatile private var coolantFrame: ByteArray? = null

    override fun read(): Hs5CanReadings {
        if (method == null && clock() >= retryAt) {
            retryAt = clock() + 10_000L
            try {
                val type = Class.forName("alfusos.rpc.RpcManager")
                val resolved = type.getMethod("getMessage", Int::class.javaPrimitiveType, ByteArray::class.java)
                instance = type.getMethod("getInstance").invoke(null)
                if (instance != null) method = resolved
            } catch (_: ReflectiveOperationException) {
                // Absent on other head units. Retry slowly without log/exception formatting.
            } catch (_: LinkageError) {
            } catch (_: SecurityException) {
            }
        }
        val engine = frame(0)
        val coolant = frame(24)
        engineFrame = engine
        coolantFrame = coolant
        return Hs5CanDecoder.decode(engine, coolant)
    }

    override fun diagnosticStatus(): String {
        if (!com.shilapi.xcertplay.diagnostics.DiagnosticLogging.enabled) return "rpcAvailable=${method != null}"
        fun hex(frame: ByteArray?): String = frame?.joinToString("") {
            (it.toInt() and 255).toString(16).padStart(2, '0')
        } ?: "null"
        return "rpcAvailable=${method != null} can0=${hex(engineFrame)} can24=${hex(coolantFrame)}"
    }

    private fun frame(id: Int): ByteArray? {
        val rpc = method ?: return null
        return try {
            rpc.invoke(instance, 1872, ByteArray(26).also { it[0] = id.toByte() }) as? ByteArray
        } catch (_: ReflectiveOperationException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }
}

internal data class Hs5TimedInt(val value: Int? = null, val at: Long = Long.MIN_VALUE) {
    fun fresh(now: Long): Int? = value.takeIf { at >= 0 && now >= at && now - at <= Hs5VehicleSample.MAX_AGE_MS }
}

internal data class Hs5TimedFloat(val value: Float? = null, val at: Long = Long.MIN_VALUE) {
    fun fresh(now: Long): Float? = value.takeIf { at >= 0 && now >= at && now - at <= Hs5VehicleSample.MAX_AGE_MS }
}

/** Separate timestamps prevent frequent speed pushes from keeping old CAN/fuel data alive. */
internal data class Hs5VehicleSample(
    // Change-only listeners retain their last value until the connection is lost.
    val pushConnected: Boolean = false,
    val speed: Hs5TimedInt = Hs5TimedInt(),
    val gear: Hs5TimedInt = Hs5TimedInt(),
    val fuel: Hs5TimedInt = Hs5TimedInt(),
    val fuelWarning: Hs5TimedInt = Hs5TimedInt(),
    val tires: List<Hs5TimedInt> = List(4) { Hs5TimedInt() },
    val tireWarnings: List<Hs5TimedInt> = List(4) { Hs5TimedInt() },
    val seatBelt: Hs5TimedInt = Hs5TimedInt(),
    val passengerSeatBelt: Hs5TimedInt = Hs5TimedInt(),
    val brake: Hs5TimedInt = Hs5TimedInt(),
    val lowBeam: Hs5TimedInt = Hs5TimedInt(),
    val driveMode: Hs5TimedInt = Hs5TimedInt(),
    val outside: Hs5TimedFloat = Hs5TimedFloat(),
    val acc: Hs5TimedInt = Hs5TimedInt(),
    val fcw: Hs5TimedInt = Hs5TimedInt(),
    val aeb: Hs5TimedInt = Hs5TimedInt(),
    val ldw: Hs5TimedInt = Hs5TimedInt(),
    val lka: Hs5TimedInt = Hs5TimedInt(),
    val fcwWarning: Hs5TimedInt = Hs5TimedInt(),
    val aebWarning: Hs5TimedInt = Hs5TimedInt(),
    val hudFcwDisplay: Hs5TimedInt = Hs5TimedInt(),
    val hudAdasDisplay: Hs5TimedInt = Hs5TimedInt(),
    val cruiseMode: Hs5TimedInt = Hs5TimedInt(),
    val aebDisplay: Hs5TimedInt = Hs5TimedInt(),
    val can: Hs5CanReadings = Hs5CanReadings(),
    val canAt: Long = Long.MIN_VALUE,
) {
    fun vehicle(now: Long, lights: Boolean?): OemClusterVehicleState {
        fun pushed(reading: Hs5TimedInt) = if (pushConnected) reading.value else reading.fresh(now)
        val speedNow = Hs5VehicleSpeed.kph(pushed(speed))
        val gearNow = pushed(gear)
        val freshCan = can.takeIf { canAt >= 0 && now >= canAt && now - canAt <= MAX_AGE_MS } ?: Hs5CanReadings()
        // Match the kit's exact gate: true P-idle (>350 rpm) remains visible.
        val hideParkIdle = gearNow == 0 && speedNow != null && speedNow <= 0.5f &&
            freshCan.rpm != null && freshCan.rpm <= 350f
        return OemClusterVehicleState(
            speedKph = speedNow,
            rpm = freshCan.rpm.takeUnless { hideParkIdle },
            coolantCelsius = freshCan.coolant,
            gear = when (gearNow) {
                0 -> "P"; 1 -> "R"; 2 -> "N"; 3 -> "D"; 4 -> "S"
                5 -> "M"; 6 -> "W"; 7 -> "E"; 8 -> "B"; else -> null
            },
            fuelFraction = pushed(fuel)?.takeIf { it in 0..100 }?.div(100f),
            lowFuelWarning = pushed(fuelWarning)?.takeIf { it >= 0 && it != 254 && it != 255 }?.let { it != 0 },
            seatBeltWarning = when (seatBelt.fresh(now)) { 0 -> true; 1 -> false; else -> null },
            tirePressureBar = tires.map { reading ->
                // Adapter getter/push values are already scaled to kPa by Qisi.
                // Applying the CAN factor 2.75 again turns 2.3 bar into 6.3 bar.
                pushed(reading)?.takeIf { it in 1..700 && it != 254 && it != 255 }?.div(100f)
            },
            tireWarning = tireWarnings.map { reading ->
                pushed(reading)?.takeIf { it == 0 || it == 1 }?.let { it == 1 }
            },
            positionLights = lights,
            lowBeam = lowBeam.fresh(now)?.let { QisiPositionLightBinder.night(it) },
            driveModeCode = driveMode.fresh(now)?.takeIf { it in 1..10 },
            outsideCelsius = outside.fresh(now), // ATC 93 already returns Celsius as float.
            assistanceStatus = OemClusterAssistanceStatus(
                acc = acc.fresh(now), fcw = fcw.fresh(now), aeb = aeb.fresh(now),
                ldw = ldw.fresh(now), lka = lka.fresh(now),
                fcwWarning = fcwWarning.fresh(now), aebWarning = aebWarning.fresh(now),
                hudFcwDisplay = hudFcwDisplay.fresh(now), hudAdasDisplay = hudAdasDisplay.fresh(now),
                cruiseMode = cruiseMode.fresh(now), aebDisplay = aebDisplay.fresh(now),
            ).validated(),
            // carBrake is not a verified EPB/parking-brake bit. Passenger belt lacks occupancy.
            // WorkingSt and WarningCmd do not establish target distance or detected lane lines.
        ).validated()
    }

    companion object { const val MAX_AGE_MS = 2500L }
}
