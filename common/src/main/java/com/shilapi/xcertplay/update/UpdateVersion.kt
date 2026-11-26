package com.shilapi.xcertplay.update

internal object UpdateVersion {
    private val TRIPLE = Regex("^[vV]?(\\d+)\\.(\\d+)\\.(\\d+)(?:[-+][0-9A-Za-z.-]+)?$")

    internal fun parse(value: String): Triple<Int, Int, Int>? =
        TRIPLE.matchEntire(value.trim())?.let { match ->
            Triple(
                match.groupValues[1].toIntOrNull() ?: return null,
                match.groupValues[2].toIntOrNull() ?: return null,
                match.groupValues[3].toIntOrNull() ?: return null,
            )
        }

    internal fun isStable(value: String): Boolean =
        Regex("[vV]?\\d+\\.\\d+\\.\\d+").matches(value.trim()) && parse(value) != null

    internal fun isNewer(remote: String, installed: String): Boolean {
        val target = parse(remote) ?: return false
        val current = parse(installed) ?: return true
        if (target.first != current.first) return target.first > current.first
        if (target.second != current.second) return target.second > current.second
        return target.third > current.third
    }
}
