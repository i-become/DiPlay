package com.shilapi.xcertplay.newui



import androidx.compose.foundation.background

import androidx.compose.foundation.border

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.BoxScope

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.foundation.layout.fillMaxWidth

import androidx.compose.foundation.layout.height

import androidx.compose.foundation.shape.RoundedCornerShape

import androidx.compose.runtime.Composable

import androidx.compose.ui.Alignment

import androidx.compose.ui.Modifier

import androidx.compose.ui.draw.clip

import androidx.compose.ui.draw.shadow

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.unit.Dp

import androidx.compose.ui.unit.dp



object NewUiSurfaces {

    val hairlineBorder get() = NewUiColors.DropdownBorder

    val hairlineBorderStrong get() = NewUiColors.DropdownBorder

    val glassBorder get() = NewUiColors.DropdownBorder

    val glassBorderSoft get() = NewUiColors.DropdownBorder

    val glassDivider get() = NewUiColors.Divider

    val innerDivider get() = NewUiColors.Divider



    fun panelBrush(): Brush = Brush.verticalGradient(

        listOf(NewUiColors.CardSecondary, NewUiColors.CardPanel),

    )



    fun panelBorderBrush(): Brush = Brush.verticalGradient(
        listOf(NewUiColors.CardBorderTop, NewUiColors.CardBorderBottom),
    )

    fun panelTopSheen(): Brush = Brush.verticalGradient(

        0f to NewUiColors.CardSheen,

        0.45f to Color.Transparent,

    )



    fun panelBottomShade(): Brush = Brush.verticalGradient(

        0f to Color.Transparent,

        0.72f to NewUiColors.BackgroundVeilLight,

        1f to NewUiColors.BackgroundVeilBottom,

    )



    fun glassControlBrush(): Brush = Brush.verticalGradient(

        0f to NewUiColors.ButtonNormal,

        1f to NewUiColors.DropdownBackground,

    )



    fun heroCardBrush(): Brush = panelBrush()



    fun sidebarIdleBrush(): Brush = Brush.verticalGradient(

        0f to NewUiColors.CardGlassIdle,

        1f to Color.Transparent,

    )



    fun sidebarSelectedBrush(): Brush = Brush.horizontalGradient(

        0f to NewUiColors.PrimaryBlue,

        0.48f to NewUiColors.MenuSelected,

        1f to NewUiColors.MenuSelectedFade,

    )



    fun accentGlossBrush(): Brush = Brush.verticalGradient(

        0f to NewUiColors.PrimaryButtonFill,

        0.42f to NewUiColors.PrimaryButtonFill,

        1f to NewUiColors.MenuSelected,

    )



    fun accentGlossHighlight(): Brush = Brush.verticalGradient(

        0f to NewUiColors.CardSheen,

        0.5f to Color.Transparent,

    )



    fun outlineButtonBrush(): Brush = glassControlBrush()

    fun pillGlassBrush(): Brush = glassControlBrush()

    fun segmentSelectedBrush(): Brush = accentGlossBrush()



    fun iconWellBrush(): Brush = Brush.verticalGradient(

        0f to NewUiColors.DropdownBackground,

        1f to NewUiColors.ButtonNormal,

    )



    fun toggleOnBrush(): Brush = Brush.horizontalGradient(

        listOf(NewUiColors.PrimaryBlue, NewUiColors.MenuSelected),

    )



    fun cardSecondaryBrush(): Brush = Brush.verticalGradient(

        0f to NewUiColors.CardSecondary,

        1f to NewUiColors.CardNormal,

    )

}



private fun Modifier.newUiGlassShadow(shape: RoundedCornerShape, accent: Boolean = false): Modifier {

    val ambient = if (accent) NewUiColors.PrimaryGlowSoft else NewUiColors.BackgroundVeilLight

    val spot = if (accent) NewUiColors.PrimaryGlowStrong else NewUiColors.Canvas

    return shadow(

        elevation = if (accent) 10.dp else 6.dp,

        shape = shape,

        ambientColor = ambient,

        spotColor = spot,

    )

}



fun Modifier.newUiPremiumPanel(

    cornerRadius: Dp = NewUiLayout.CardRadius,

): Modifier = newUiGlassTile(cornerRadius)



fun Modifier.newUiGlassTile(

    cornerRadius: Dp = NewUiLayout.CardRadius,

): Modifier {

    val shape = RoundedCornerShape(cornerRadius)

    return this

        .newUiGlassShadow(shape)

        .clip(shape)

        .background(NewUiSurfaces.panelBrush(), shape)

        .border(1.dp, NewUiSurfaces.panelBorderBrush(), shape)

}



@Composable

fun NewUiPremiumPanel(

    modifier: Modifier = Modifier,

    cornerRadius: Dp = NewUiLayout.CardRadius,

    content: @Composable () -> Unit,

) {

    val shape = RoundedCornerShape(cornerRadius)

    Box(

        modifier = modifier

            .newUiGlassShadow(shape)

            .clip(shape)

            .background(NewUiSurfaces.panelBrush(), shape)

            .border(1.dp, NewUiSurfaces.panelBorderBrush(), shape),

    ) {

        NewUiGlassSheenOverlay()

        content()

    }

}



@Composable

fun BoxScope.NewUiGlassSheenOverlay() {

    Box(

        Modifier

            .fillMaxWidth()

            .height(44.dp)

            .align(Alignment.TopCenter)

            .background(NewUiSurfaces.panelTopSheen()),

    )

    Box(

        Modifier

            .fillMaxSize()

            .background(NewUiSurfaces.panelBottomShade()),

    )

}



@Composable

fun BoxScope.NewUiControlTopGloss(strong: Boolean = false) {

    if (!strong) return

    Box(

        Modifier

            .fillMaxWidth()

            .height(38.dp)

            .align(Alignment.TopCenter)

            .background(NewUiSurfaces.accentGlossHighlight()),

    )

}



fun Modifier.newUiGlassPanel(

    cornerRadius: Dp = NewUiLayout.CardRadius,

    glowBorder: Boolean = true,

): Modifier = newUiGlassTile(cornerRadius)



fun Modifier.newUiOutlineControl(cornerRadius: Dp = 14.dp): Modifier {

    val shape = RoundedCornerShape(cornerRadius)

    return this

        .newUiGlassShadow(shape)

        .clip(shape)

        .background(NewUiSurfaces.glassControlBrush(), shape)

        .border(1.dp, NewUiSurfaces.hairlineBorder, shape)

}



fun Modifier.newUiSecondaryChip(cornerRadius: Dp = 12.dp): Modifier =

    newUiOutlineControl(cornerRadius)



fun Modifier.newUiSecondaryCard(cornerRadius: Dp = NewUiLayout.CardRadius): Modifier {

    val shape = RoundedCornerShape(cornerRadius)

    return this

        .newUiGlassShadow(shape)

        .clip(shape)

        .background(NewUiSurfaces.cardSecondaryBrush(), shape)

        .border(1.dp, NewUiSurfaces.hairlineBorder, shape)

}



fun Modifier.newUiAccentGloss(cornerRadius: Dp = NewUiLayout.CardRadius): Modifier {

    val shape = RoundedCornerShape(cornerRadius)

    return this

        .newUiGlassShadow(shape, accent = true)

        .clip(shape)

        .background(NewUiSurfaces.accentGlossBrush(), shape)

        .border(1.dp, NewUiColors.PrimaryBlue, shape)

}


