package com.shilapi.xcertplay.glance

import com.shilapi.xcertplay.iap2.wire.Iap2ParameterList
import com.shilapi.xcertplay.iap2.wire.Iap2WireCodec

/** Signed iAP2 directions: 0 = straight, positive = right, negative = left. */
data class CarPlayLane(
    val index: Int,
    val direction: Int,
    val status: Int,
    val highlightedDirection: Int? = null,
    val directions: List<Int> = listOf(direction),
) {
    // An unhighlighted lane is not necessarily closed to traffic.
    val recommended: Boolean get() = status == 2
}

/** LaneGuidanceInfoUpdate (0x5204), not the vehicle's ADAS lane-detection signals. */
internal object CarPlayLaneGuidance {
    const val MESSAGE_ID = 0x5204
    data class Update(val index: Int, val lanes: List<CarPlayLane>)

    /** An invalid packet clears the strip rather than inventing a straight/recommended lane. */
    fun decode(payload: ByteArray): Update? = runCatching {
        require(payload.size <= 8192)
        val fields = Iap2ParameterList.parse(payload).asList()
        fun scalar(id: Int, size: Int): ByteArray = fields.single { it.id == id }.payload.also {
            require(it.size == size)
        }
        require(Iap2WireCodec.readU16(scalar(0, 2)) == 42)
        val index = Iap2WireCodec.readU16(scalar(1, 2))
        val entries = fields.filter { it.id == 2 }
        require(entries.size <= 16)
        val lanes = entries.map { entry ->
            val lane = Iap2ParameterList.parse(entry.payload).asList()
            fun value(id: Int, size: Int): ByteArray = lane.single { it.id == id }.payload.also {
                require(it.size == size)
            }
            fun angle(bytes: ByteArray): Int = Iap2WireCodec.readI16(bytes).also {
                require(it in -180..180 || it == 1000)
            }
            // Multiple angle TLVs and packed vectors are both valid. 1000 means unknown.
            val angles = lane.filter { it.id == 2 }.flatMap { field ->
                require(field.payload.isNotEmpty() && field.payload.size % 2 == 0)
                require(field.payload.size <= 32)
                field.payload.toList().chunked(2).map { angle(it.toByteArray()) }
            }
            require(angles.isNotEmpty() && angles.size <= 16)
            val directions = angles.filter { it != 1000 }.distinct()
            val highlighted = lane.filter { it.id == 3 }
            require(highlighted.size <= 1)
            CarPlayLane(
                index = Iap2WireCodec.readU16(value(0, 2)),
                direction = directions.firstOrNull() ?: 1000,
                status = value(1, 1)[0].toInt() and 0xff,
                highlightedDirection = highlighted.singleOrNull()?.payload?.also {
                    require(it.size == 2)
                }?.let(::angle)?.takeUnless { it == 1000 },
                directions = directions,
            )
        }
        require(lanes.map { it.index }.distinct().size == lanes.size)
        Update(index, lanes.sortedBy { it.index })
    }.getOrNull()
}

/** Cache future junctions separately; only the phone's current, visible lane block may be shown. */
internal class CarPlayLaneState {
    internal data class Current(
        val index: Int,
        val showing: Boolean,
        val lanes: List<CarPlayLane>,
    )

    private val blocks = linkedMapOf<Int, List<CarPlayLane>>()
    private var currentIndex: Int? = null
    private var showing = false

    fun acceptLanes(payload: ByteArray): Boolean {
        val update = CarPlayLaneGuidance.decode(payload)
        if (update == null) {
            clear()
            return false
        }
        blocks.remove(update.index)
        blocks[update.index] = update.lanes
        if (blocks.size > 8) blocks.remove(blocks.keys.first { it != currentIndex })
        return true
    }

    fun acceptRoute(payload: ByteArray) {
        runCatching {
            val fields = Iap2ParameterList.parse(payload).asList()
            fun optional(id: Int, size: Int): ByteArray? = fields.filter { it.id == id }.let {
                require(it.size <= 1)
                it.singleOrNull()?.payload?.also { bytes -> require(bytes.size == size) }
            }
            val state = optional(1, 1)?.get(0)?.toInt()
            val index = optional(0x10, 2)?.let { Iap2WireCodec.readU16(it) }
            val count = optional(0x11, 2)?.let { Iap2WireCodec.readU16(it) }
            val visible = optional(0x12, 1)?.get(0)?.toInt()
            if (state == 0 || state == 2 || count == 0) clear()
            else {
                if (index != null) currentIndex = index
                if (visible != null) showing = visible == 1
            }
        }.onFailure { clear() }
    }

    fun current(): List<CarPlayLane> = if (showing) blocks[currentIndex].orEmpty() else emptyList()

    /** Metadata and lanes for the route-referenced block, including an empty valid block. */
    fun currentBlock(): Current? = currentIndex?.let { index ->
        if (!showing || !blocks.containsKey(index)) null
        else Current(index, true, blocks[index].orEmpty())
    }

    fun diagnosticState(): String = "index=$currentIndex showing=$showing cached=${blocks.keys} " +
        "lanes=${current().map { "${it.index}:${it.status}:${it.directions}:${it.highlightedDirection}" }}"

    fun clear() {
        blocks.clear()
        currentIndex = null
        showing = false
    }
}
