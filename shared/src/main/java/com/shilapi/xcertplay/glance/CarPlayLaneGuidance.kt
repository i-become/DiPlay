package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.wire.Iap2Parameter
import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import com.shilapi.xcertplay.iap2.wire.Iap2WireCodec

/** Raw metadata angles are signed degrees, with zero pointing forward. */
data class CarPlayLane(
    val index: Int,
    val direction: Int,
    val status: Int,
    val highlightedDirection: Int? = null,
    val directions: List<Int> = listOf(direction),
) {
    /** Observed rendering policy; status values are not official traffic restrictions. */
    val selectedDirection: Int? get() = when {
        status <= 0 -> null
        highlightedDirection != null -> highlightedDirection.takeIf { it in directions && it in -180..180 }
        status == 2 -> directions.distinct().singleOrNull()?.takeIf { it in -180..180 }
        else -> null
    }
    val recommended: Boolean get() = selectedDirection != null
}

/** Complete CSM parameter payloads only, after the existing authenticated iAP2 transport. */
internal object CarPlayLaneGuidance {
    const val MESSAGE_ID = 0x5204
    data class Update(val index: Int, val lanes: List<CarPlayLane>)

    fun fields(payload: ByteArray): List<Iap2Parameter> {
        require(payload.size <= 16384) { "payload-too-large" }
        return Iap2ParameterList.parse(payload).asList()
    }

    fun scalar(fields: List<Iap2Parameter>, id: Int, size: Int, required: Boolean = false): ByteArray? {
        val values = fields.filter { it.id == id }
        require(values.size <= 1) { "duplicate-singleton:$id" }
        val value = values.singleOrNull()?.payload
        require(!required || value != null) { "missing-tag:$id" }
        require(value == null || value.size == size) { "wrong-length:$id" }
        return value
    }

    fun acceptsComponent(fields: List<Iap2Parameter>): Boolean {
        val ids = fields.filter { it.id == 0 }.flatMap {
            val bytes = it.payload
            require(bytes.isNotEmpty() && bytes.size % 2 == 0) { "invalid-component-list" }
            bytes.indices.step(2).map { offset -> Iap2WireCodec.readU16(bytes, offset) }
        }
        return ids.isEmpty() || 42 in ids
    }

    fun decode(payload: ByteArray): Update? = runCatching { parse(fields(payload)) }.getOrNull()

    fun parse(fields: List<Iap2Parameter>): Update? {
        if (!acceptsComponent(fields)) return null
        val index = Iap2WireCodec.readU16(scalar(fields, 1, 2, true)!!)
        require(index != 65535) { "invalid-event-index" }
        val lanes = mutableListOf<CarPlayLane>()
        fun lane(parts: List<Iap2Parameter>) {
            val position = Iap2WireCodec.readU16(scalar(parts, 0, 2, true)!!)
            val status = scalar(parts, 1, 1, true)!![0].toInt() and 255
            require(status in 0..2) { "unsupported-status" }
            val angles = parts.filter { it.id == 2 }.flatMap {
                val bytes = it.payload
                require(bytes.isNotEmpty() && bytes.size % 2 == 0) { "invalid-angle-length" }
                bytes.indices.step(2).map { offset -> Iap2WireCodec.readU16(bytes, offset).toShort().toInt() }
            }
            require(angles.size in 1..16) { "unsupported-angle-count" }
            val highlight = scalar(parts, 3, 2)?.let { Iap2WireCodec.readI16(it) }
            lanes += CarPlayLane(position, angles.first(), status, highlight, angles)
            require(lanes.size <= 12) { "unsupported-lane-count" }
        }
        fields.filter { it.id == 2 }.forEach { container ->
            val parts = mutableListOf<Iap2Parameter>()
            fields(container.payload).forEach { field ->
                if (field.id !in 0..3) return@forEach
                if (field.id == 0 && parts.isNotEmpty()) { lane(parts); parts.clear() }
                parts += field
            }
            if (parts.isNotEmpty()) lane(parts)
        }
        require(lanes.isNotEmpty()) { "missing-lanes" }
        val sorted = lanes.sortedBy { it.index }
        val start = sorted.first().index
        require(start in 0..1 && sorted.map { it.index } == (start until start + sorted.size).toList()) {
            "noncontiguous-positions"
        }
        return Update(index, sorted)
    }
}

/** Route-selected event cache, independent of maneuver availability and packet arrival order. */
internal class CarPlayLaneState(private val nanoTime: () -> Long = System::nanoTime) {
    internal data class Current(val index: Int, val showing: Boolean, val lanes: List<CarPlayLane>)
    private val blocks = linkedMapOf<Int, List<CarPlayLane>>()
    private var currentIndex: Int? = null
    private var showing = false
    private var lastRouteNs: Long? = null
    private var rawState: Int? = null
    private var rawVisibility: Int? = null
    private var reason = "no-route-update"

    private fun expire() {
        if (lastRouteNs?.let { nanoTime() - it >= 30_000_000_000L } == true) {
            clear(); reason = "route-expired"
        }
    }

    fun acceptLanes(payload: ByteArray): Boolean {
        expire()
        return runCatching {
            val update = CarPlayLaneGuidance.parse(CarPlayLaneGuidance.fields(payload))
                ?: run { reason = "other-component"; return true }
            blocks.remove(update.index)
            blocks[update.index] = update.lanes
            while (blocks.size > 32) blocks.remove(blocks.keys.first { it != currentIndex })
            reason = "none"
            true
        }.getOrElse { reason = it.message ?: "invalid-lane-packet"; false }
    }

    fun acceptRoute(payload: ByteArray) {
        expire()
        runCatching {
            val fields = CarPlayLaneGuidance.fields(payload)
            if (!CarPlayLaneGuidance.acceptsComponent(fields)) { reason = "other-component"; return }
            fun number(id: Int, size: Int): Int? = CarPlayLaneGuidance.scalar(fields, id, size)?.let {
                if (size == 1) it[0].toInt() and 255 else Iap2WireCodec.readU16(it)
            }
            // Validate the complete update before committing any field.
            val state = number(1, 1)
            val index = number(0x10, 2)
            val count = number(0x11, 2)
            val visible = number(0x12, 1)
            if (state == 0 || count == 0) clear() else {
                if (index != null) currentIndex = index
                if (visible != null) showing = visible == 1
                lastRouteNs = nanoTime()
            }
            if (state != null) rawState = state
            if (visible != null) rawVisibility = visible
            reason = if (visible != null && visible !in 0..1) "abnormal-visibility:$visible" else "none"
        }.onFailure { reason = it.message ?: "invalid-route-packet" }
    }

    fun current(): List<CarPlayLane> = currentBlock()?.lanes.orEmpty()

    fun currentBlock(): Current? {
        expire()
        if (!showing || lastRouteNs == null) return null
        val index = currentIndex ?: return null
        val lanes = blocks[index] ?: return null
        // Preserve raw signed angles in the cache, but never invent unsupported geometry.
        if (lanes.any { lane -> lane.directions.any { it !in -180..180 } }) {
            reason = "unsupported-render-angle"
            return null
        }
        if (lanes.any { it.highlightedDirection != null && (it.status == 0 || it.highlightedDirection !in it.directions) }) {
            reason = "ignored-invalid-highlight"
        }
        return Current(index, true, lanes)
    }

    fun diagnosticState(): String {
        val current = current()
        return "component=42 index=$currentIndex showing=$showing rawState=$rawState rawVisibility=$rawVisibility " +
            "cached=${blocks.keys} fresh=${lastRouteNs != null} sourceType=${if (current.isEmpty()) "none" else "metadata"} " +
            "rejectReason=$reason lanes=${current.map { "${it.index}:${it.status}:${it.directions}:${it.highlightedDirection}" }}"
    }

    fun clear() {
        blocks.clear(); currentIndex = null; showing = false; lastRouteNs = null
        rawState = null; rawVisibility = null
    }
}
