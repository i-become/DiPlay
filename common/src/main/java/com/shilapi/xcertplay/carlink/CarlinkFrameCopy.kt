package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.shilapi.xcertplay.ClusterCopyRegion

/** Keep every selected decoder pixel. Only the final instrument viewport scales the image. */
internal object CarlinkFrameCopy {
    fun crop(source: Bitmap, selection: ClusterCopyRegion, reusable: Bitmap?): Bitmap {
        val region = selection.clamped()
        val left = (region.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (region.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (region.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (region.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        val width = right - left
        val height = bottom - top
        val output = reusable?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, Rect(left, top, right, bottom), Rect(0, 0, width, height), null)
        return output
    }
}
