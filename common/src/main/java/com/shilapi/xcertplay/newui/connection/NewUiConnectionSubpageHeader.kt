package com.shilapi.xcertplay.newui.connection

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import com.shilapi.xcertplay.newui.NewUiSubpageTitle

@Composable
fun NewUiConnectionSubpageHeader(
    @StringRes titleRes: Int,
) {
    NewUiSubpageTitle(titleRes)
}
