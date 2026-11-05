package com.shilapi.xcertplay

import android.os.SystemClock
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

/** Raw, read-only ADAS values from IFC_2 (55) and IFC_4 (6).
 * The booleans are nullable because an unavailable frame is different from a clear frame.
 */
internal data class Hs5LeadVehicleReading(
    val raw: Int? = null,
    val source: String = "unavailable",
    val laneDetectState: Int? = null,
    val leftLaneDetected: Boolean? = null,
    val rightLaneDetected: Boolean? = null,
) {
    val detected: Boolean? get() = when (raw) { 0 -> false; 1 -> true; else -> null }
}

internal fun interface Hs5LeadVehicleReader {
    fun read(): Hs5LeadVehicleReading
}

internal fun interface Hs5AdasFrameReader {
    fun read(messageId: Int): ByteArray?
}

/** A validated response has its RPC message ID removed, leaving the CAN payload. */
internal object Hs5AdasFrameDecoder {
    fun decode(messageId: Int, raw: ByteArray?): ByteArray? {
        if (raw == null || raw.size !in 9..26 || (raw[0].toInt() and 0xff) != messageId) return null
        return raw.copyOfRange(1, raw.size)
    }

    fun bit(payload: ByteArray?, index: Int, shift: Int, mask: Int): Int? =
        payload?.getOrNull(index)?.let { ((it.toInt() and 0xff) ushr shift) and mask }
}

/** Reflection keeps the vendor RPC classes out of the APK. Calls run on the monitor worker. */
internal class Hs5AdasRpcFrameReader : Hs5AdasFrameReader {
    private var rpc: Any? = null
    private var getMessage: Method? = null

    override fun read(messageId: Int): ByteArray? {
        try {
            if (rpc == null || getMessage == null) {
                val type = Class.forName("alfusos.rpc.RpcManager")
                rpc = type.getMethod("getInstance").invoke(null)
                if (rpc == null) return null
                getMessage = type.getMethod("getMessage", Int::class.javaPrimitiveType, ByteArray::class.java)
            }
            val request = ByteArray(26).also { it[0] = messageId.toByte() }
            return getMessage!!.invoke(rpc, 1872, request) as? ByteArray
        } catch (error: InvocationTargetException) {
            if (error.targetException is LinkageError) throw error.targetException
            return null
        } catch (_: ReflectiveOperationException) {
            return null
        } catch (_: RuntimeException) {
            return null
        } catch (_: LinkageError) {
            return null
        }
    }
}

/** Shares the existing RPM background cadence; no extra ADAS timer is created. */
internal class Hs5OemLeadVehicleReader(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val adasFrameReader: Hs5AdasFrameReader = Hs5AdasRpcFrameReader(),
) : Hs5LeadVehicleReader {
    private var retryAt = Long.MIN_VALUE

    override fun read(): Hs5LeadVehicleReading {
        if (clock() < retryAt) return Hs5LeadVehicleReading()
        return try {
            val ifc2 = Hs5AdasFrameDecoder.decode(55, adasFrameReader.read(55))
            val ifc4 = Hs5AdasFrameDecoder.decode(6, adasFrameReader.read(6))
            val front = Hs5AdasFrameDecoder.bit(ifc2, 1, 3, 1)
            val lane = Hs5AdasFrameDecoder.bit(ifc2, 3, 4, 3)
            val source = if (ifc2 == null && ifc4 == null) "rpc-ifc2-55-ifc4-6-unavailable" else "rpc-ifc2-55-ifc4-6"
            Hs5LeadVehicleReading(
                raw = front,
                source = source,
                laneDetectState = lane,
                leftLaneDetected = lane?.let { it and 1 != 0 },
                rightLaneDetected = lane?.let { it and 2 != 0 },
            )
        } catch (_: LinkageError) {
            retryAt = clock() + 10_000L
            Hs5LeadVehicleReading()
        } catch (_: RuntimeException) {
            Hs5LeadVehicleReading()
        }
    }

}
