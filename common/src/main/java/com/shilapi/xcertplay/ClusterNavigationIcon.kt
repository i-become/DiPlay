package com.shilapi.xcertplay

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/** Decode an SDK maneuver image once per change, with bounded allocation and glyph fallback. */
internal class ClusterNavigationIcon {
    private var source: ByteArray? = null
    private var bitmap: Bitmap? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun draw(canvas: Canvas, bytes: ByteArray?, bounds: RectF): Boolean {
        if (bytes == null) { if (source != null) release(); return false }
        if (source?.contentEquals(bytes) != true) {
            release()
            source = bytes.copyOf()
            if (bytes.size in 1..262144) {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                if (options.outWidth in 1..512 && options.outHeight in 1..512) {
                    bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            }
        }
        val image = bitmap ?: return false
        canvas.drawBitmap(image, null, bounds, paint)
        return true
    }

    fun release() { bitmap?.recycle(); bitmap = null; source = null }
}
