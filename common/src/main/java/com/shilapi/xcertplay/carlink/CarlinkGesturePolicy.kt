package com.shilapi.xcertplay.carlink

/** Same distances, direction priority and finger preferences as the CarPlay host. */
internal class CarlinkGesturePolicy {
    enum class Action { SETTINGS, SHOW_CLUSTER, HIDE_CLUSTER }
    data class Decision(val consume: Boolean, val cancelPhone: Boolean = false, val action: Action? = null)
    private var captured = false
    private var tracking = false
    private var fingers = 0
    private var startX = 0f
    private var startY = 0f

    fun accept(mask: Int, count: Int, x: Float, y: Float, settingsFingers: Int,
               clusterEnabled: Boolean, threshold: Float): Decision {
        if (mask == 0) { captured = false; tracking = false }
        if (mask == 5 && !captured &&
            ((settingsFingers > 0 && count == settingsFingers) || (clusterEnabled && count == 3))) {
            captured = true; tracking = true; fingers = count; startX = x; startY = y
            return Decision(true, cancelPhone = true)
        }
        if (!captured) return Decision(false)
        if (mask == 5 && tracking && ((settingsFingers > 0 && count == settingsFingers) ||
                (clusterEnabled && count == 3))) {
            fingers = count; startX = x; startY = y
        }
        if (mask == 6 || count != fingers) tracking = false
        var action: Action? = null
        if (tracking && mask == 2) {
            val dx = x - startX; val dy = y - startY
            action = when {
                fingers == settingsFingers && dy >= threshold && dy >= kotlin.math.abs(dx) * 1.15f -> Action.SETTINGS
                clusterEnabled && fingers == 3 && kotlin.math.abs(dx) >= threshold && kotlin.math.abs(dx) >= kotlin.math.abs(dy) * 1.15f ->
                    if (dx < 0) Action.SHOW_CLUSTER else Action.HIDE_CLUSTER
                else -> null
            }
            if (action != null) tracking = false
        }
        // Consume until final UP/CANCEL, including fingers remaining after an action.
        if (mask == 1 || mask == 3) { captured = false; tracking = false }
        return Decision(true, action = action)
    }
}
