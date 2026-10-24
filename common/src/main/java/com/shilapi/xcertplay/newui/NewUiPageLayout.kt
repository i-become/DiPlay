package com.shilapi.xcertplay.newui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun NewUiMainPageTitle(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = NewUiTypography.PageTitle,
        color = NewUiColors.TextPrimary,
        modifier = modifier.padding(bottom = NewUiLayout.PageTitleGap),
    )
}

@Composable
fun NewUiTwoColumnPage(
    modifier: Modifier = Modifier,
    columnGap: Dp = NewUiLayout.TwoColumnGap,
    leftWeight: Float = 1f,
    rightWeight: Float = 1f,
    left: @Composable ColumnScope.() -> Unit,
    right: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(columnGap),
    ) {
        NewUiScrollPanelColumn(
            modifier = Modifier
                .weight(leftWeight)
                .fillMaxHeight(),
            content = left,
        )
        NewUiScrollPanelColumn(
            modifier = Modifier
                .weight(rightWeight)
                .fillMaxHeight(),
            content = right,
        )
    }
}

@Composable
fun NewUiScrollPanelColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = modifier.newUiGlassPanel(cornerRadius = NewUiLayout.PageCardRadius),
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = true)
                .fillMaxWidth()
                .verticalScroll(scroll)
                .padding(vertical = 4.dp),
            content = content,
        )
    }
}

@Composable
fun NewUiSplitPage(
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.42f,
    rightWeight: Float = 0.58f,
    left: @Composable () -> Unit,
    right: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(NewUiLayout.TwoColumnGap),
    ) {
        Column(
            Modifier
                .weight(leftWeight)
                .fillMaxHeight(),
        ) {
            left()
        }
        Column(
            Modifier
                .weight(rightWeight)
                .fillMaxHeight(),
        ) {
            right()
        }
    }
}
