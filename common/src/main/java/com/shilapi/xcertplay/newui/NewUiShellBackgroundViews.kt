package com.shilapi.xcertplay.newui

import android.content.Context
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import com.shilapi.xcertplay.host.R

/** View-system twin of [NewUiScreenBackground] for [CarPlayHostActivity] and other non-Compose screens. */
object NewUiShellBackgroundViews {
    fun install(context: Context, root: FrameLayout) {
        installInto(context, root, atIndex = 0)
    }

    fun installInto(context: Context, parent: FrameLayout, atIndex: Int = parent.childCount) {
        val image = ImageView(context).apply {
            setImageResource(R.drawable.new_ui_shell_background)
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
    }
}
