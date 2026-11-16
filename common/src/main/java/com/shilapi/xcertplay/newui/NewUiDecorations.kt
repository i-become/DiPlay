package com.shilapi.xcertplay.newui



import androidx.compose.foundation.Image

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Box

import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.runtime.Composable

import androidx.compose.ui.Modifier

import androidx.compose.ui.graphics.Brush

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.layout.ContentScale

import androidx.compose.ui.res.painterResource

import com.shilapi.xcertplay.host.R



@Composable

fun NewUiScreenBackground(modifier: Modifier = Modifier) {

    Box(

        modifier

            .fillMaxSize()

            .background(Brush.verticalGradient(listOf(NewUiColors.Canvas, NewUiColors.CanvasBottom))),

    )

}



@Composable

fun NewUiTopBarGlass(modifier: Modifier = Modifier, content: @Composable () -> Unit) {

    Box(

        modifier = modifier.background(

            brush = Brush.verticalGradient(

                colors = listOf(

                    NewUiColors.TopBarGlassTop,

                    NewUiColors.TopBarGlassBottom,

                    Color.Transparent,

                ),

            ),

        ),

    ) {

        content()

    }

}


