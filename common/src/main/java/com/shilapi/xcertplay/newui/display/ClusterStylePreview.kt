package com.shilapi.xcertplay.newui.display

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.shilapi.xcertplay.ClusterDashboardState
import com.shilapi.xcertplay.ClusterStyle
import com.shilapi.xcertplay.renderer
import com.shilapi.xcertplay.customDashboard
import com.shilapi.xcertplay.mapBounds
import com.shilapi.xcertplay.OemClusterVehicleState

/** Small, once-per-card bitmap. Sample values are artwork, never published as vehicle data. */
internal object ClusterStylePreview {
    const val ASPECT = 1920f / 720f

    fun create(context: Context, style: ClusterStyle): Bitmap {
        val bitmap = Bitmap.createBitmap(640, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(14, 24, 35))
        canvas.save()
        canvas.scale(640f / 1920, 240f / 720)
        val map = if (style.customDashboard) style.mapBounds(1920, 720) else android.graphics.RectF(0f, 0f, 1920f, 720f)
        canvas.clipRect(map)
        canvas.translate(map.left, map.top)
        canvas.scale(map.width() / 1920f, map.height() / 720f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 25f; color = Color.rgb(52, 70, 76) }
        for (i in -3..9) {
            canvas.drawLine(i * 270f, 0f, i * 270f + 290f, 720f, paint)
            canvas.drawLine(0f, i * 160f, 1920f, i * 160f + 280f, paint)
        }
        paint.color = Color.rgb(74, 164, 130)
        canvas.drawRoundRect(1100f, 120f, 1320f, 280f, 30f, 30f, paint)
        val center = 960f
        val route = Path().apply { moveTo(center, 640f); lineTo(center, 310f); lineTo(center + 230f, 160f) }
        paint.style = Paint.Style.STROKE
        paint.color = Color.rgb(58, 146, 255)
        paint.strokeWidth = 30f
        canvas.drawPath(route, paint)
        paint.style = Paint.Style.FILL
        paint.color = Color.WHITE
        canvas.drawPath(Path().apply {
            moveTo(center, 500f); lineTo(center - 30f, 560f)
            lineTo(center, 548f); lineTo(center + 30f, 560f); close()
        }, paint)
        canvas.restore()
        if (style.customDashboard) {
            val renderer = style.renderer(context)
            try {
                renderer.draw(canvas, bitmap.width, bitmap.height,
                    ClusterDashboardState(vehicle = OemClusterVehicleState(speedKph = 0f, rpm = 800f)), true)
            } finally { renderer.release() }
        }
        return bitmap
    }
}

