package com.shilapi.xcertplay.hud

import android.content.Intent

/** One instrument-cluster frame in the AMap broadcast vocabulary accepted by the stock AMap adapter. */
internal data class Hs5GuidanceFrame(
    val icon: Int,
    val roundaboutExit: Int,
    val distanceMeters: Int,
    val road: String = "",
    val routeRemainingMeters: Int = -1,
    val routeRemainingSeconds: Int = -1,
    val destination: String = "",
    val junctionType: Int = -1,
    val junctionAngles: List<Int> = emptyList(),
    val exitInfo: String = "",
    val exitAngle: Int = 1000,
    val laneGuidanceIndex: Int = -1,
    val laneGuidanceShowing: Boolean = false,
    val laneGuidanceTotal: Int = 0,
    val laneGuidanceDescription: String = "",
    val lanes: List<Hs5LaneInfo> = emptyList(),
) {
    companion object {
        private const val LEFT = 2
        private const val RIGHT = 3
        private const val SLIGHT_LEFT = 4
        private const val SLIGHT_RIGHT = 5
        private const val SHARP_LEFT = 6
        private const val SHARP_RIGHT = 7
        private const val U_TURN_LEFT = 8
        private const val STRAIGHT = 9
        private const val ROUNDABOUT_ENTER = 11
        private const val ROUNDABOUT_EXIT = 12
        private const val DESTINATION = 15
        private const val ROUNDABOUT_ENTER_CLOCKWISE = 17
        private const val ROUNDABOUT_EXIT_CLOCKWISE = 18
        private const val U_TURN_RIGHT = 19

        /** Apple iAP2 RouteGuidanceManeuverType -> AMap NEW_ICON (+ ROUNG_ABOUT_NUM). */
        fun from(maneuver: Hs5AppleManeuver): Hs5GuidanceFrame {
            val leftHandTraffic = maneuver.drivingSide == 1
            val distance = maneuver.distanceMeters.coerceAtLeast(0)
            val type = maneuver.type
            val road = maneuver.road
            val remainingMeters = maneuver.remainingMeters?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: -1
            val remainingSeconds = maneuver.remainingSeconds?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt() ?: -1
            if (type in 28..46) {
                val icon = if (leftHandTraffic) ROUNDABOUT_ENTER_CLOCKWISE else ROUNDABOUT_ENTER
                return Hs5GuidanceFrame(
                    icon, type - 27, distance, road, remainingMeters, remainingSeconds,
                    maneuver.destination, maneuver.junctionType, maneuver.junctionAngles, maneuver.exitInfo,
                    maneuver.exitAngle ?: 1000, maneuver.laneGuidanceIndex,
                    maneuver.laneGuidanceShowing, maneuver.laneGuidanceTotal,
                    maneuver.laneGuidanceDescription, maneuver.lanes,
                )
            }
            val icon = when (type) {
                1, 20 -> LEFT
                2, 21 -> RIGHT
                47 -> SHARP_LEFT
                48 -> SHARP_RIGHT
                13, 22, 49, 52 -> SLIGHT_LEFT
                14, 23, 50, 53 -> SLIGHT_RIGHT
                4, 18, 19, 26 -> if (leftHandTraffic) U_TURN_RIGHT else U_TURN_LEFT
                6 -> if (leftHandTraffic) ROUNDABOUT_ENTER_CLOCKWISE else ROUNDABOUT_ENTER
                7 -> if (leftHandTraffic) ROUNDABOUT_EXIT_CLOCKWISE else ROUNDABOUT_EXIT
                10, 12, 24, 25, 27 -> DESTINATION
                3, 5, 8, 9, 11, 51 -> STRAIGHT
                else -> 0 // Unknown/no maneuver must not become a false straight arrow.
            }
            return Hs5GuidanceFrame(
                icon, 0, distance, road, remainingMeters, remainingSeconds,
                maneuver.destination, maneuver.junctionType, maneuver.junctionAngles, maneuver.exitInfo,
                maneuver.exitAngle ?: 1000, maneuver.laneGuidanceIndex,
                maneuver.laneGuidanceShowing, maneuver.laneGuidanceTotal,
                maneuver.laneGuidanceDescription, maneuver.lanes,
            )
        }
    }
}

internal data class Hs5LaneGuidanceExtras(
    val positions: IntArray,
    val statuses: IntArray,
    val highlightedDirections: IntArray,
    val directionOffsets: IntArray,
    val directions: IntArray,
)

internal fun Hs5GuidanceFrame.laneGuidanceExtras(): Hs5LaneGuidanceExtras {
    val lanes = lanes
    val offsets = IntArray(lanes.size + 1)
    lanes.forEachIndexed { index, lane -> offsets[index + 1] = offsets[index] + lane.angles.size }
    return Hs5LaneGuidanceExtras(
        positions = lanes.map { it.position }.toIntArray(),
        statuses = lanes.map { it.status }.toIntArray(),
        highlightedDirections = lanes.map { it.highlightedAngle ?: 1000 }.toIntArray(),
        directionOffsets = offsets,
        directions = lanes.flatMap { it.angles }.toIntArray(),
    )
}

/**
 * Adds the lossless lane model to an AMap navigation broadcast. The stock adapter may ignore
 * these optional extras, while instrument integrations can consume the real CarPlay lane data
 * without having to reconstruct it from the maneuver icon.
 */
internal fun Intent.putLaneGuidanceExtras(frame: Hs5GuidanceFrame) {
    val lanes = frame.lanes
    val extras = frame.laneGuidanceExtras()
    putExtra("LANE_GUIDANCE_COUNT", lanes.size)
    putExtra("LANE_POSITIONS", extras.positions)
    putExtra("LANE_STATUSES", extras.statuses)
    putExtra("LANE_HIGHLIGHTED_DIRECTIONS", extras.highlightedDirections)
    putExtra("LANE_DIRECTION_OFFSETS", extras.directionOffsets)
    putExtra("LANE_DIRECTIONS", extras.directions)
}
