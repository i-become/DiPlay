package com.shilapi.xcertplay.newui

import androidx.compose.ui.unit.dp

/** Spacing matched to Superdesign Apple Minimal (1920×720). */
object NewUiLayout {
    val ScreenPaddingH = 40.dp
    val ScreenPaddingBottom = 28.dp
    val TopBarPaddingH = 40.dp
    /** Header band height including bottom divider. */
    val TopBarToDividerHeight = 96.dp
    val TopBarHeight = 72.dp
    /** Head-unit home control in the top bar: tap target (icon stays [TopBarHomeIconSize]). */
    val TopBarHomeHitWidth = 56.dp
    val TopBarHomeIconSize = 24.dp
    val TopBarContentVerticalPadding = 12.dp
    val TopBarBrandGap = 48.dp
    val NavTabPaddingH = 36.dp
    val NavTabHeight = 72.dp
    val NavTabRadius = 18.dp
    val NavTabGap = 8.dp
    val HongqiAccentBarWidth = 4.dp
    val HongqiAccentBarHeight = 36.dp
    val MainGap = 16.dp
    val PageTitleGap = 20.dp
    val PageContentPaddingTop = 28.dp
    val TwoColumnGap = 24.dp
    val PageCardRadius = 28.dp
    val HeroPanelRadius = 32.dp
    val ProgressBlockRadius = 24.dp
    val ActionButtonRadius = 24.dp
    val ActionButtonMinHeight = 100.dp
    val InsetRowRadius = 20.dp
    val CompactRowHeight = 68.dp
    val CardRadius = 16.dp
    /** Legacy sidebar metrics (unused in top-tab shell; kept for adaptive tests). */
    val SidebarWidth = 248.dp
    val SidebarPaddingStart = 16.dp
    val SidebarPaddingTop = 14.dp
    val SidebarItemHeight = 85.dp
    val SidebarItemGap = 8.dp
    val SidebarSelectedRadius = 12.dp
    val SidebarAccentWidth = 4.dp
    val ContentPaddingStart = 0.dp
    val ContentPaddingEnd = 0.dp
    val HeroHeight = 220.dp
    val ActionRowHeight = 132.dp
    val LocationRowHeight = 72.dp
    /** About page side panel: payment + community QR tiles. */
    val AboutQrSize = 260.dp
    val AboutQrInnerPadding = 4.dp
    val RowGap = 14.dp
    val ClusterCardPadding = 24.dp
    val ClusterCardGap = 24.dp
    val ClusterCardWideThreshold = 760.dp
    val ClusterPreviewMaxWidth = 660.dp
    val ClusterSelectionBorder = 2.dp
    val ClusterCardBorder = 1.dp
    const val TopBarHongqiBadgeAspectRatio = 1700f / 500f
}
