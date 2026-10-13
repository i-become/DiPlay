package com.shilapi.xcertplay.newui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.shilapi.xcertplay.host.R

@Composable
fun NewUiPlaceholderScreen(destination: NewUiDestination) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.new_ui_section_coming_soon, stringResource(destination.labelRes)),
            color = NewUiColors.TextSecondary,
            fontSize = 18.sp,
        )
    }
}
