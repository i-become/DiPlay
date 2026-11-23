package com.shilapi.xcertplay.carlink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import com.shilapi.xcertplay.ClusterCopyRegion

/** Keep every selected decoder pixel. Only the final instrument viewport scales the image. */
internal object CarlinkFrameCopy {
    fun pixelBounds(width: Int, height: Int, selection: ClusterCopyRegion): Rect {
        require(width > 0 && height > 0)
        val region = selection.clamped()
        val left = (region.left * width).toInt().coerceIn(0, width - 1)
        val top = (region.top * height).toInt().coerceIn(0, height - 1)
        val right = (region.right * width).toInt().coerceIn(left + 1, width)
        val bottom = (region.bottom * height).toInt().coerceIn(top + 1, height)
        return Rect(left, top, right, bottom)
    }

    fun crop(source: Bitmap, selection: ClusterCopyRegion, reusable: Bitmap?): Bitmap {
        val bounds = pixelBounds(source.width, source.height, selection)
        val left = bounds.left; val top = bounds.top; val right = bounds.right; val bottom = bounds.bottom
        val width = right - left
        val height = bottom - top
        val output = reusable?.takeIf { !it.isRecycled && it.width == width && it.height == height }
            ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(output).drawBitmap(source, Rect(left, top, right, bottom), Rect(0, 0, width, height), null)
        return output
    }
}
