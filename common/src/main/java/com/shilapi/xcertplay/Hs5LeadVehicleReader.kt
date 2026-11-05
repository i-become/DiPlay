package com.shilapi.xcertplay

import android.os.SystemClock
import com.hs5.vehicle.sdk.can.CanOemSignalProbe
import com.hs5.vehicle.sdk.can.CanRpcProbe
import com.hs5.vehicle.sdk.can.CanSignalRuleDecoder
import org.json.JSONObject

internal data class Hs5LeadVehicleReading(
    val raw: Int? = null,
    val source: String = "unavailable",
) {
    // Detection flag only; ACC mode and FCW warning do not establish object presence.
    val detected: Boolean? get() = when (raw) { 0 -> false; 1 -> true; else -> null }
}

internal fun interface Hs5LeadVehicleReader {
    fun read(): Hs5LeadVehicleReading
}

/** Shares the existing RPM background cadence while a custom cluster is visible; no extra timer. */
internal class Hs5OemLeadVehicleReader(
    private val clock: () -> Long = SystemClock::elapsedRealtime,
    private val oemSignal: () -> Int? = { CanOemSignalProbe.parseIntSignal("ObjectDetection") },
    private val decodeRule: (JSONObject?) -> CanSignalRuleDecoder.RuleSample = {
        CanSignalRuleDecoder.decodeRuleFromCanJson("Rule_ObjectDetection", it)
    },
    private val readFrame: (Int) -> JSONObject = { CanRpcProbe.probe(intArrayOf(it)) },
) : Hs5LeadVehicleReader {
    private var retryAt = Long.MIN_VALUE
    private var messageId: Int? = null

    override fun read(): Hs5LeadVehicleReading {
        if (clock() < retryAt) return Hs5LeadVehicleReading()
        return try {
            val oem = oemSignal()
            if (oem == 0 || oem == 1) return Hs5LeadVehicleReading(oem, "oem-ObjectDetection")
            val id = messageId ?: decodeRule(null).messageId.takeIf { it in 0..255 }?.also { messageId = it }
            if (id == null) {
                retryAt = clock() + 10_000L
                Hs5LeadVehicleReading(oem, "ObjectDetection-rule-unavailable")
            } else {
                val decoded = decodeRule(readFrame(id))
                Hs5LeadVehicleReading(decoded.raw, "rpc-ObjectDetection-frame-$id")
            }
        } catch (_: LinkageError) {
            retryAt = clock() + 10_000L
            Hs5LeadVehicleReading()
        } catch (_: RuntimeException) {
            Hs5LeadVehicleReading()
        }
    }
}
