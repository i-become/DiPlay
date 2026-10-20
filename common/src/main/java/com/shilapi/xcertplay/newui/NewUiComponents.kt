package com.shilapi.xcertplay.newui



import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.clickable

import androidx.compose.foundation.interaction.MutableInteractionSource

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxScope

import androidx.compose.foundation.layout.Row

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.layout.offset

import androidx.compose.foundation.layout.padding

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.shape.CircleShape

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R



@Composable

fun NewUiGlassCard(

    modifier: Modifier = Modifier,

    accentBorder: Boolean = true,

    content: @Composable BoxScope.() -> Unit,

) {

    val shape = RoundedCornerShape(NewUiLayout.CardRadius)

    Box(

        modifier = modifier.newUiGlassTile(cornerRadius = NewUiLayout.CardRadius),

    ) {

        NewUiGlassSheenOverlay()

        content()

    }

}



@Composable

fun NewUiPrimaryCard(

    modifier: Modifier = Modifier,

    content: @Composable BoxScope.() -> Unit,

) {

    Box(

        modifier = modifier.newUiAccentGloss(),

        content = content,

    )

}



@Composable
fun NewUiChevron(
    modifier: Modifier = Modifier,
    tint: Color = NewUiColors.TextSecondary,
    size: Dp = 24.dp,
) {
    Icon(
        painter = painterResource(R.drawable.new_ui_ic_chevron_right),
        contentDescription = null,
        tint = tint,
        modifier = modifier.size(size),
    )
}

@Composable
fun NewUiLoaderIcon(
    modifier: Modifier = Modifier,
    tint: Color = NewUiColors.TextSecondary,
    size: Dp = 36.dp,
) {
    val reduceMotion = rememberNewUiReduceMotion()
    val spin = rememberInfiniteTransition(label = "newUiLoader")
    val animatedRotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(NewUiMotion.LoaderSpinDurationMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "loaderRotation",
    )
    val rotation = if (reduceMotion) 0f else animatedRotation
    Icon(
        painter = painterResource(R.drawable.new_ui_ic_loader),
        contentDescription = null,
        tint = tint,
        modifier = modifier
            .size(size)
            .rotate(rotation),
    )
}



@Composable
fun NewUiToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val reduceMotion = rememberNewUiReduceMotion()
    val duration = if (reduceMotion) 0 else NewUiMotion.ToggleDurationMs
    val trackColor by animateColorAsState(
        targetValue = if (checked) NewUiColors.SwitchTrackOn else NewUiColors.SwitchTrackOff,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "toggleTrack",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 28.dp else 0.dp,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "toggleThumb",
    )
    val trackWidth = 68.dp
    val trackHeight = 38.dp
    val thumbSize = 32.dp
    val inset = 3.dp
    Box(
        modifier = modifier
            .width(trackWidth)
            .height(trackHeight)
            .clip(CircleShape)
            .background(trackColor)
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            Modifier
                .padding(inset)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .shadow(if (checked) 2.dp else 0.dp, CircleShape, clip = false)
                .clip(CircleShape)
                .background(NewUiColors.SwitchThumb),
        )
    }
}



@Composable

fun NewUiStatusDot(connected: Boolean, modifier: Modifier = Modifier) {

    Box(

        modifier = modifier

            .size(10.dp)

            .clip(CircleShape)

            .background(if (connected) NewUiColors.Success else NewUiColors.StatusDotIdle),

    )

}


