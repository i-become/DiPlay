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

import androidx.compose.material3.Text

import androidx.compose.runtime.Composable

import androidx.compose.runtime.remember

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.dp



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

fun NewUiChevron(modifier: Modifier = Modifier, tint: Color = NewUiColors.TextSecondary) {

    Text(

        text = "›",

        color = tint,

        style = NewUiTypography.ActionTitle,

        modifier = modifier,

    )

}



@Composable

fun NewUiToggle(

    checked: Boolean,

    onCheckedChange: (Boolean) -> Unit,

    modifier: Modifier = Modifier,

) {

    val trackWidth = 68.dp

    val trackHeight = 38.dp

    val thumbSize = 32.dp

    Box(

        modifier = modifier

            .width(trackWidth)

            .height(trackHeight)

            .clip(CircleShape)

            .background(

                if (checked) {

                    NewUiSurfaces.toggleOnBrush()

                } else {

                    Brush.linearGradient(

                        listOf(NewUiColors.SwitchTrackOff, NewUiColors.SwitchTrackOff),

                    )

                },

            )

            .border(

                1.dp,

                if (checked) NewUiColors.PrimaryBlue else NewUiSurfaces.glassBorderSoft,

                CircleShape,

            )

            .clickable(

                interactionSource = remember { MutableInteractionSource() },

                indication = null,

                onClick = { onCheckedChange(!checked) },

            )

            .padding(2.dp),

        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,

    ) {

        Box(

            Modifier

                .size(thumbSize)

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


