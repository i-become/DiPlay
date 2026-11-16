package com.shilapi.xcertplay.newui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.host.R

/** Pill-shaped back control for the CarPlay waiting screen (matches New UI subpage chrome). */
internal class NewUiWaitingBackButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {
    private val icon: ImageView
    private val label: TextView

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        val density = resources.displayMetrics.density
        val padH = (24 * density).toInt()
        val padV = (18 * density).toInt()
        setPadding(padH, padV, padH, padV)
        val ripple = RippleDrawable(
            ColorStateList.valueOf(0x33555267),
            ContextCompat.getDrawable(context, R.drawable.new_ui_waiting_back_button_bg),
            null,
        )
        background = ripple
        icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_dp_back)
            contentDescription = null
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        val iconSize = (24 * density).toInt()
        addView(icon, LayoutParams(iconSize, iconSize))
        label = TextView(context).apply {
            setTextColor(ContextCompat.getColor(context, R.color.new_ui_text_primary))
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            isAllCaps = false
        }
        addView(
            label,
            LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginStart = (10 * density).toInt()
            },
        )
    }

    fun setLabel(text: CharSequence) {
        label.text = text
    }

    fun applyLayout(textSizeSp: Float, heightPx: Int, widthPx: Int) {
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, textSizeSp)
        val iconPx = (textSizeSp * 1.35f * resources.displayMetrics.scaledDensity).toInt().coerceIn(
            (20 * resources.displayMetrics.density).toInt(),
            (28 * resources.displayMetrics.density).toInt(),
        )
        icon.layoutParams = (icon.layoutParams as LayoutParams).apply {
            width = iconPx
            height = iconPx
        }
        layoutParams = layoutParams.apply {
            height = heightPx
            width = widthPx
        }
    }
}
