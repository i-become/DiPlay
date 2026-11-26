package com.shilapi.xcertplay.newui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.FrameLayout
import com.shilapi.xcertplay.AppAppearanceRuntime
import com.shilapi.xcertplay.HongqiPositionLights
import com.shilapi.xcertplay.resolveAppNightNow

/** View-system twin of NewUiScreenBackground; no bitmap decoding on startup. */
object NewUiShellBackgroundViews {
    fun install(context: Context, root: FrameLayout) {
        installInto(context, root, atIndex = 0)
    }

    fun installInto(context: Context, parent: FrameLayout, atIndex: Int = parent.childCount) {
        parent.addView(object : View(context) {
            private var unsubscribe: (() -> Unit)? = null
            private fun refreshBackground() {
                val night = context.resolveAppNightNow()
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    if (night) intArrayOf(0xFF100E23.toInt(), 0xFF050307.toInt())
                    else intArrayOf(0xFFE7E3EF.toInt(), 0xFFF3F1F7.toInt()))
            }
            override fun onAttachedToWindow() {
                super.onAttachedToWindow()
                HongqiPositionLights.initialize(context)
                refreshBackground()
                unsubscribe = AppAppearanceRuntime.observeHost { post { if (isAttachedToWindow) refreshBackground() } }
            }
            override fun onDetachedFromWindow() {
                unsubscribe?.invoke()
                unsubscribe = null
                super.onDetachedFromWindow()
            }
        }, atIndex, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT,
        ))
    }
}
