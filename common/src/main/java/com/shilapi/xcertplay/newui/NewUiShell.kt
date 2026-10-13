package com.shilapi.xcertplay.newui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.annotation.DrawableRes
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiSurfaces

@Composable
fun NewUiShell(
    destination: NewUiDestination,
    showHongqiBranding: Boolean,
    topBarStatus: NewUiTopBarStatus,
    onDestinationSelected: (NewUiDestination) -> Unit,
    onOpenHeadUnitHome: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NewUiScreenBackground(Modifier.fillMaxSize())
        Column(
            Modifier.fillMaxSize(),
        ) {
            Column(Modifier.fillMaxWidth()) {
                NewUiTopBarGlass(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    NewUiTopBar(
                        showHongqiBranding = showHongqiBranding,
                        status = topBarStatus,
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxWidth(),
                    thickness = 1.dp,
                    color = NewUiColors.Divider,
                )
            }
            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = NewUiLayout.MainGap, bottom = NewUiLayout.ScreenPaddingBottom),
            ) {
                val metrics = NewUiWindowMetrics.from(maxWidth, maxHeight)
                CompositionLocalProvider(LocalNewUiWindowMetrics provides metrics) {
                    Row(Modifier.fillMaxSize()) {
                        NewUiSidebar(
                            destination = destination,
                            onDestinationSelected = onDestinationSelected,
                            onOpenHeadUnitHome = onOpenHeadUnitHome,
                            sidebarWidth = metrics.sidebarWidth,
                            itemHeight = metrics.sidebarItemHeight,
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(
                                    start = NewUiLayout.ContentPaddingStart,
                                    end = if (metrics.compactWidth) {
                                        NewUiLayout.ContentPaddingEnd - 8.dp
                                    } else {
                                        NewUiLayout.ContentPaddingEnd
                                    },
                                    top = NewUiLayout.MainGap,
                                ),
                        ) {
                            Box(Modifier.weight(1f).fillMaxWidth()) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewUiSidebar(
    destination: NewUiDestination,
    onDestinationSelected: (NewUiDestination) -> Unit,
    onOpenHeadUnitHome: () -> Unit,
    sidebarWidth: Dp,
    itemHeight: Dp,
) {
    Column(
        modifier = Modifier
            .width(sidebarWidth)
            .fillMaxHeight()
            .padding(
                start = NewUiLayout.SidebarPaddingStart,
                top = NewUiLayout.SidebarPaddingTop,
                bottom = NewUiLayout.SidebarPaddingTop,
            ),
        verticalArrangement = Arrangement.spacedBy(NewUiLayout.SidebarItemGap),
    ) {
        NewUiDestination.entries.forEach { item ->
            NewUiSidebarItem(
                item = item,
                selected = item == destination,
                itemHeight = itemHeight,
                onClick = { onDestinationSelected(item) },
            )
        }
        Spacer(Modifier.weight(1f))
        NewUiSidebarHeadUnitItem(
            itemHeight = itemHeight,
            onClick = onOpenHeadUnitHome,
        )
    }
}

@Composable
private fun NewUiSidebarHeadUnitItem(
    itemHeight: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(NewUiLayout.SidebarSelectedRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(shape)
            .background(NewUiSurfaces.sidebarIdleBrush(), shape)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_dp_home),
                contentDescription = null,
                colorFilter = ColorFilter.tint(NewUiColors.TextSecondary),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.new_ui_nav_head_unit),
                style = NewUiTypography.NavItem,
                color = NewUiColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NewUiSidebarItem(
    item: NewUiDestination,
    selected: Boolean,
    itemHeight: Dp,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(NewUiLayout.SidebarSelectedRadius)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(itemHeight)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .shadow(
                            elevation = 8.dp,
                            shape = shape,
                            ambientColor = NewUiColors.PrimaryGlowSoft,
                            spotColor = NewUiColors.PrimaryGlowStrong,
                        )
                        .background(NewUiSurfaces.sidebarSelectedBrush(), shape)
                        .border(1.dp, NewUiColors.PrimaryBlue, shape)
                } else {
                    Modifier.background(NewUiSurfaces.sidebarIdleBrush(), shape)
                },
            )
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(item.iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (selected) NewUiColors.TextPrimary else NewUiColors.TextSecondary,
                ),
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(item.labelRes),
                style = NewUiTypography.NavItem,
                color = if (selected) NewUiColors.TextPrimary else NewUiColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun NewUiTopBar(
    showHongqiBranding: Boolean,
    status: NewUiTopBarStatus,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeight(NewUiLayout.TopBarToDividerHeight)
            .testTag(NewUiTestTags.TopBarBand),
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(NewUiLayout.TopBarHeight)
                .padding(horizontal = NewUiLayout.TopBarPaddingH),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start,
            ) {
                if (showHongqiBranding) {
                    Image(
                        painter = painterResource(R.drawable.new_ui_hongqi_badge),
                        contentDescription = stringResource(R.string.new_ui_brand_hongqi),
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .height(NewUiLayout.TopBarHeight)
                            .aspectRatio(NewUiLayout.TopBarHongqiBadgeAspectRatio),
                    )
                    TopBarDivider()
                }
                Text(
                    text = stringResource(R.string.new_ui_product_name),
                    style = NewUiTypography.BrandProduct,
                    color = NewUiColors.TextPrimary,
                )
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                NewUiTopBarStatIcon(
                    iconRes = R.drawable.new_ui_ic_stat_location,
                    active = status.locationActive,
                )
                Spacer(Modifier.width(14.dp))
                NewUiTopBarStatIcon(
                    iconRes = R.drawable.new_ui_ic_stat_bluetooth,
                    active = status.bluetoothActive,
                )
                Spacer(Modifier.width(14.dp))
                NewUiTopBarStatIcon(
                    iconRes = R.drawable.new_ui_ic_stat_wifi,
                    active = status.wifiConnected,
                )
                Spacer(Modifier.width(14.dp))
                NewUiTopBarStatIcon(
                    iconRes = R.drawable.new_ui_ic_stat_signal,
                    active = status.hotspotActive,
                )
                Spacer(Modifier.width(18.dp))
                Text(
                    text = status.timeText,
                    style = NewUiTypography.TopTime,
                    color = NewUiColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun NewUiTopBarStatIcon(
    @DrawableRes iconRes: Int,
    active: Boolean,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        colorFilter = ColorFilter.tint(
            if (active) NewUiColors.TextPrimary else NewUiColors.TextSecondary.copy(alpha = 0.4f),
        ),
    )
}

@Composable
private fun TopBarDivider() {
    Text(
        text = "|",
        color = NewUiColors.TextSecondary,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
}
