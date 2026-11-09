package com.shilapi.xcertplay.hud

/** Next-turn instruction for the dashboard overlay. Icons use the AMap NEW_ICON vocabulary. */
data class ClusterTurnGuidance(
    val icon: Int,
    val roundaboutExit: Int,
    val distanceMeters: Int,
    val road: String,
    /** Route-level arrival/duration/distance for the info strip below the card. */
    val arrivalEpochSeconds: Long? = null,
    val remainingSeconds: Long? = null,
    val remainingMeters: Long? = null,
    val lanes: List<com.shilapi.xcertplay.glance.CarPlayLane> = emptyList(),
) {
    companion object {
        internal fun from(frame: Hs5GuidanceFrame): ClusterTurnGuidance {
            return ClusterTurnGuidance(frame.icon, frame.roundaboutExit, frame.distanceMeters, frame.road,
                lanes = frame.lanes.map { lane -> com.shilapi.xcertplay.glance.CarPlayLane(
                    lane.position, lane.angles.first(), lane.status, lane.highlightedAngle, lane.angles) })
        }
    }
}
