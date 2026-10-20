package com.shilapi.xcertplay.newui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

fun Modifier.newUiPressScale(
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource,
): Modifier = composed {
    val reduceMotion = rememberNewUiReduceMotion()
    val pressed by interactionSource.collectIsPressedAsState()
    val target = when {
        !enabled || reduceMotion -> 1f
        pressed -> NewUiMotion.ButtonPressScale
        else -> 1f
    }
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(NewUiMotion.ButtonPressDurationMs, easing = NewUiMotion.EaseOut),
        label = "newUiPressScale",
    )
    scale(scale)
}
