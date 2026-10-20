package com.shilapi.xcertplay.newui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R

@Composable
fun NewUiShell(
    destination: NewUiDestination,
    showHs5Branding: Boolean,
    topBarStatus: NewUiTopBarStatus,
    onDestinationSelected: (NewUiDestination) -> Unit,
    onOpenHeadUnitHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NewUiScreenBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize()) {
            NewUiTopTabHeader(
                destination = destination,
                showHs5Branding = showHs5Branding,
                topBarStatus = topBarStatus,
                onDestinationSelected = onDestinationSelected,
                onOpenHeadUnitHome = onOpenHeadUnitHome,
            )
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = NewUiColors.Divider.copy(alpha = 0.9f),
            )
            androidx.compose.foundation.layout.BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(
                        start = NewUiLayout.ScreenPaddingH,
                        end = NewUiLayout.ScreenPaddingH,
                        top = NewUiLayout.PageContentPaddingTop,
                        bottom = NewUiLayout.ScreenPaddingBottom,
                    ),
            ) {
                val metrics = NewUiWindowMetrics.from(maxWidth, maxHeight)
                CompositionLocalProvider(LocalNewUiWindowMetrics provides metrics) {
                    Box(Modifier.fillMaxSize()) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun NewUiTopTabHeader(
    destination: NewUiDestination,
    showHs5Branding: Boolean,
    topBarStatus: NewUiTopBarStatus,
    onDestinationSelected: (NewUiDestination) -> Unit,
    onOpenHeadUnitHome: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(NewUiLayout.TopBarToDividerHeight)
            .padding(horizontal = NewUiLayout.TopBarPaddingH)
            .testTag(NewUiTestTags.TopBarBand),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NewUiLayout.TopBarBrandGap),
        ) {
            if (showHs5Branding) {
                NewUiBrandLockup()
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(NewUiLayout.NavTabGap),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.height(NewUiLayout.TopBarHeight),
            ) {
                NewUiDestination.entries.forEach { item ->
                    NewUiTopNavTab(
                        label = stringResource(item.labelRes),
                        selected = item == destination,
                        onClick = { onDestinationSelected(item) },
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(NewUiLayout.TopBarHeight)
                    .width(NewUiLayout.TopBarHomeHitWidth)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenHeadUnitHome),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_dp_home),
                    contentDescription = stringResource(R.string.new_ui_nav_head_unit),
                    colorFilter = ColorFilter.tint(NewUiColors.TextSecondary),
                    modifier = Modifier.size(NewUiLayout.TopBarHomeIconSize),
                )
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = topBarStatus.timeText,
                style = NewUiTypography.TopTime,
                color = NewUiColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun NewUiBrandLockup() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .width(NewUiLayout.HongqiAccentBarWidth)
                .height(NewUiLayout.HongqiAccentBarHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(NewUiColors.HongqiRed),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(R.string.new_ui_product_name),
            style = NewUiTypography.BrandHongqi,
            color = NewUiColors.TextPrimary,
        )
        Text(
            text = " ${stringResource(R.string.new_ui_brand_hs5)}",
            style = NewUiTypography.BrandHongqi.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Normal),
            color = NewUiColors.TextSecondary,
        )
    }
}

@Composable
private fun NewUiTopNavTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val reduceMotion = rememberNewUiReduceMotion()
    val duration = if (reduceMotion) 0 else NewUiMotion.NavTabDurationMs
    val shape = RoundedCornerShape(NewUiLayout.NavTabRadius)
    val background by animateColorAsState(
        targetValue = if (selected) NewUiColors.Accent else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "navTabBg",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) NewUiColors.TextPrimary else NewUiColors.TextSecondary,
        animationSpec = tween(duration, easing = NewUiMotion.EaseOut),
        label = "navTabLabel",
    )
    Box(
        modifier = Modifier
            .height(NewUiLayout.NavTabHeight)
            .clip(shape)
            .background(background, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = NewUiLayout.NavTabPaddingH),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = NewUiTypography.NavTab,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
