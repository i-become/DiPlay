package com.shilapi.xcertplay.carlink

import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import kotlin.math.roundToInt

/** SDK fields are presentation strings, not a complete route. Preserve phone summaries verbatim. */
internal object CarlinkNavigation {
    fun distanceMeters(value: String?, unit: String?): Int {
        val number = Regex("[0-9]+(?:[.,][0-9]+)?").find(value.orEmpty())?.value
            ?.replace(',', '.')?.toDoubleOrNull() ?: return 0
        val units = (unit.orEmpty() + " " + value.orEmpty()).lowercase()
        val factor = when {
            "km" in units || "公里" in units || "千米" in units -> 1000.0
            "mile" in units || "英里" in units || unit?.trim()?.lowercase() == "mi" -> 1609.344
            "ft" in units || "英尺" in units -> .3048
            else -> 1.0
        }
        return (number * factor).coerceIn(0.0, Int.MAX_VALUE.toDouble()).roundToInt()
    }

    fun icon(operation: String?): Int {
        val op = operation.orEmpty().lowercase()
        return when {
            listOf("uturn", "u-turn", "u turn", "掉头", "调头").any { it in op } -> 8
            "环岛" in op || "roundabout" in op -> if ("驶出" in op || "exit" in op || "out" in op) 12 else 11
            "到达" in op || "目的地" in op || "arrive" in op || "destination" in op -> 15
            ("sharp" in op || "急" in op) && ("left" in op || "左" in op) -> 6
            ("sharp" in op || "急" in op) && ("right" in op || "右" in op) -> 7
            ("slight" in op || "前方" in op || "靠" in op) && ("left" in op || "左" in op) -> 4
            ("slight" in op || "前方" in op || "靠" in op) && ("right" in op || "右" in op) -> 5
            "left" in op || "左" in op -> 2
            "right" in op || "右" in op -> 3
            "straight" in op || "continue" in op || "直行" in op -> 9
            else -> 0
        }
    }

    fun appleManeuver(icon: Int): Int = when (icon) {
        2 -> 1; 3 -> 2; 4 -> 13; 5 -> 14; 6 -> 47; 7 -> 48; 8 -> 4
        9 -> 3; 11 -> 6; 12 -> 7; 15 -> 10; else -> 0
    }

    fun guidance(navigating: Boolean, distance: String?, unit: String?, operation: String?,
        where: String?, title1: String?, title2: String?, directionIcon: ByteArray?): ClusterTurnGuidance? {
        if (!navigating) return null
        val instruction = listOfNotNull(operation?.takeIf { it.isNotBlank() }, where?.takeIf { it.isNotBlank() })
            .distinct().joinToString(" · ")
        val summary = listOfNotNull(title1?.takeIf { it.isNotBlank() }, title2?.takeIf { it.isNotBlank() })
            .distinct().joinToString("   ")
        return ClusterTurnGuidance(icon(operation), 0, distanceMeters(distance, unit), instruction.ifBlank { summary },
            directionIcon = directionIcon?.takeIf { it.isNotEmpty() }, routeSummary = summary)
    }
}
