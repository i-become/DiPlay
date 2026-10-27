package com.shilapi.xcertplay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Display
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/** Places the complete dashboard above the OEM's application windows on display 1.
 * Does not alter the OEM process, instrument mode, or the phone's CarPlay session. */
internal class Hs5ClusterForeground {
    private var manager: WindowManager? = null
    private var root: FrameLayout? = null
    var mode = "activity"
        private set

    fun attach(context: Context, display: Display, dashboard: View,
               dispatchKey: (KeyEvent) -> Boolean): Boolean {
        if (display.displayId == Display.DEFAULT_DISPLAY || !Settings.canDrawOverlays(context)) {
            mode = "activity (cluster overlay unavailable)"
            return false
        }
        val wm = context.createDisplayContext(display).getSystemService(WindowManager::class.java)
        val frame = object : FrameLayout(context) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean =
                if (event.keyCode in Hs5ClusterActivity.CLOSE_KEYS) dispatchKey(event)
                else super.dispatchKeyEvent(event)
        }.apply { addView(dashboard, FrameLayout.LayoutParams(-1, -1)) }
        return try {
            wm.addView(frame, parameters())
            manager = wm
            root = frame
            mode = "overlay display=${display.displayId} type=${parameters().type}"
            true
        } catch (error: RuntimeException) {
            // Secondary-display overlay policy varies by firmware. Preserve the working
            // Activity path if the OS refuses an overlay; never fail the CarPlay session.
            if (frame.isAttachedToWindow) runCatching { wm.removeViewImmediate(frame) }
            frame.removeView(dashboard)
            mode = "activity (overlay refused: ${error.javaClass.simpleName})"
            false
        }
    }

    fun close() {
        val view = root ?: return
        root = null
        val wm = manager
        manager = null
        runCatching { wm?.removeViewImmediate(view) }
        view.removeAllViews()
    }

    fun setVisible(visible: Boolean) {
        val view = root ?: return
        val params = view.layoutParams as WindowManager.LayoutParams
        params.alpha = if (visible) 1f else 0f
        val hiddenFlags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        params.flags = if (visible) params.flags and hiddenFlags.inv() else params.flags or hiddenFlags
        manager?.updateViewLayout(view, params)
    }

    companion object {
        fun parameters() = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START; title = "DiPlay HS5 dashboard" }
    }
}
