package com.shilapi.xcertplay

import android.view.Display

/** Picks a developer-simulated presentation display for cluster map debugging. */
internal object SimulatedClusterPresentationPick {
    private const val MIN_WIDTH = 480
    private const val MIN_HEIGHT = 270

    fun pick(mainDisplayId: Int, displays: Array<Display>, sizeOf: (Display) -> android.graphics.Point): Display? {
        val candidates = displays.filter { it.displayId != mainDisplayId && it.isValid }
        if (candidates.isEmpty()) return null
        val landscape = candidates.map { display ->
            display to sizeOf(display)
        }.filter { (_, size) ->
            size.x >= MIN_WIDTH && size.y >= MIN_HEIGHT && size.x > size.y
        }
        val best = (landscape.ifEmpty {
            candidates.map { display -> display to sizeOf(display) }
        }).maxByOrNull { (_, size) -> size.x.toLong() * size.y }
        return best?.first
    }
}
