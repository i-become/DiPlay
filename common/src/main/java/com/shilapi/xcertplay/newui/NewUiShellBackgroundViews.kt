package com.shilapi.xcertplay.newui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.shilapi.xcertplay.host.R
import java.lang.ref.WeakReference
import java.util.concurrent.Executors

/** View-system twin of [NewUiScreenBackground] for [CarPlayHostActivity] and other non-Compose screens. */
object NewUiShellBackgroundViews {
    private val loader = Executors.newSingleThreadExecutor { task -> Thread(task, "diplay-background-image").apply { isDaemon = true } }
    private val main = Handler(Looper.getMainLooper())
    private val imageLock = Any()
    private var bitmap: Bitmap? = null
    private var loading = false
    private val waiting = mutableListOf<WeakReference<ImageView>>()

    fun install(context: Context, root: FrameLayout) {
        installInto(context, root, atIndex = 0)
    }

    fun installInto(context: Context, parent: FrameLayout, atIndex: Int = parent.childCount) {
        val image = ImageView(context).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            contentDescription = null
        }
        val veil = View(context).apply {
            setBackgroundResource(R.drawable.new_ui_background_veil)
        }
        parent.addView(image, atIndex, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        parent.addView(veil, atIndex + 1, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
        loadImage(context.applicationContext, image)
    }

    private fun loadImage(context: Context, image: ImageView) {
        synchronized(imageLock) {
            bitmap?.let { image.setImageBitmap(it); return }
            waiting.add(WeakReference(image))
            if (loading) return
            loading = true
        }
        loader.execute {
            // CENTER_CROP handles sizing; density upscaling during decode wastes
            // memory and blocked the cold-boot main thread on Android 9.
            val decoded = runCatching {
                BitmapFactory.decodeResource(context.resources, R.drawable.new_ui_shell_background,
                    BitmapFactory.Options().apply { inScaled = false })
            }.getOrNull()
            val targets = synchronized(imageLock) {
                bitmap = decoded
                loading = false
                waiting.toList().also { waiting.clear() }
            }
            if (decoded != null) main.post { targets.forEach { it.get()?.setImageBitmap(decoded) } }
        }
    }
}
