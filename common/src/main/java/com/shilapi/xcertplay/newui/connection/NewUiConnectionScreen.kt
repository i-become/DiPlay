package com.shilapi.xcertplay.newui.connection

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiChevron
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiConnectionProgressBlock
import com.shilapi.xcertplay.newui.NewUiControlTopGloss
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiLoaderIcon
import com.shilapi.xcertplay.newui.NewUiMotion
import com.shilapi.xcertplay.newui.NewUiProgressVisual
import com.shilapi.xcertplay.newui.NewUiSettingIcon
import com.shilapi.xcertplay.newui.NewUiToggle
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.newUiPressScale
import com.shilapi.xcertplay.newui.rememberNewUiReduceMotion
import com.shilapi.xcertplay.newui.newUiAccentGloss
import com.shilapi.xcertplay.newui.newUiGlassTile

@Composable
fun NewUiConnectionScreen(
    snapshot: NewUiConnectionSnapshot,
    onPhoneAction: () -> Unit,
    onConnectionMethod: () -> Unit,
    onDisconnect: () -> Unit,
    onEnterCarPlay: () -> Unit,
    onLocationReportingChanged: (Boolean) -> Unit,
) {
    val progressVisual = when (snapshot.phase) {
        NewUiConnectionPhase.CONNECTED -> NewUiProgressVisual.COMPLETE
        NewUiConnectionPhase.CONNECTING -> NewUiProgressVisual.ACTIVE
        NewUiConnectionPhase.FAILED -> NewUiProgressVisual.FAILED
        else -> NewUiProgressVisual.IDLE
    }
    Row(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(40.dp),
    ) {
        ConnectionInfoPanel(
            modifier = Modifier
                .weight(0.58f)
                .fillMaxHeight(),
            snapshot = snapshot,
            progressVisual = progressVisual,
            onConnectionMethod = onConnectionMethod,
            onLocationReportingChanged = onLocationReportingChanged,
        )
        ConnectionActionsPanel(
            modifier = Modifier
                .weight(0.42f)
                .fillMaxHeight(),
            snapshot = snapshot,
            onEnterCarPlay = onEnterCarPlay,
            onPhoneAction = onPhoneAction,
            onDisconnect = onDisconnect,
        )
    }
}

@Composable
private fun ConnectionInfoPanel(
    snapshot: NewUiConnectionSnapshot,
    progressVisual: NewUiProgressVisual,
    onConnectionMethod: () -> Unit,
    onLocationReportingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelShape = RoundedCornerShape(NewUiLayout.HeroPanelRadius)
    Column(
        modifier = modifier
            .clip(panelShape)
            .newUiGlassTile(cornerRadius = NewUiLayout.HeroPanelRadius)
            .padding(horizontal = 32.dp, vertical = 28.dp),
    ) {
        ConnectionPhaseRow(snapshot)
        Spacer(Modifier.height(8.dp))
        Text(
            text = snapshot.phoneName,
            style = NewUiTypography.PhoneNameHero,
            color = NewUiColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        snapshot.failureBanner?.let { banner ->
            Text(
                text = banner,
                style = NewUiTypography.LinkDetail.copy(fontSize = NewUiTypography.LinkDetail.fontSize),
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        } ?: snapshot.linkSummary?.let { link ->
            Text(
                text = link,
                style = NewUiTypography.LinkDetail.copy(fontSize = NewUiTypography.LinkDetail.fontSize),
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } ?: Spacer(Modifier.height(24.dp))
        ConnectionMethodRow(
            detail = snapshot.connectionMethodLabel,
            onClick = onConnectionMethod,
        )
        Spacer(Modifier.height(24.dp))
        NewUiConnectionProgressBlock(
            litCount = snapshot.progressLitCount,
            stepTitle = snapshot.progressStepTitle,
            stepSubtitle = snapshot.progressStepSubtitle,
            visual = progressVisual,
        )
        Spacer(Modifier.weight(1f))
        LocationReportingRow(
            checked = snapshot.locationReportingEnabled,
            onCheckedChange = onLocationReportingChanged,
        )
    }
}

@Composable
private fun ConnectionPhaseRow(snapshot: NewUiConnectionSnapshot) {
    val (dotColor, statusColor) = when (snapshot.phase) {
        NewUiConnectionPhase.CONNECTED -> NewUiColors.StatusReady to NewUiColors.StatusReadySoft
        NewUiConnectionPhase.CONNECTING -> NewUiColors.Accent to NewUiColors.StatusConnecting
        NewUiConnectionPhase.FAILED -> NewUiColors.HongqiRed to NewUiColors.HongqiRed.copy(alpha = 0.85f)
        NewUiConnectionPhase.NEEDS_SETUP -> NewUiColors.HongqiRed to NewUiColors.TextPrimary
        NewUiConnectionPhase.READY -> NewUiColors.StatusReady to NewUiColors.StatusReadySoft
    }
    val pulse = snapshot.phase == NewUiConnectionPhase.CONNECTING
    val reduceMotion = rememberNewUiReduceMotion()
    val alpha = if (pulse && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "connectPulse")
        val value by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(NewUiMotion.StatusPulseDurationMs, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "dotAlpha",
        )
        value
    } else {
        1f
    }
    val readyGlow = snapshot.phase == NewUiConnectionPhase.READY ||
        snapshot.phase == NewUiConnectionPhase.CONNECTED
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .alpha(alpha)
                .then(
                    if (readyGlow) {
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = NewUiColors.StatusReady.copy(alpha = 0.5f),
                            spotColor = NewUiColors.StatusReady.copy(alpha = 0.5f),
                        )
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(999.dp))
                .background(dotColor),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = snapshot.statusTitle,
            style = NewUiTypography.PhaseStatus,
            color = statusColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConnectionMethodRow(detail: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(NewUiLayout.InsetRowRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(NewUiLayout.CompactRowHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, NewUiColors.Divider.copy(alpha = 0.6f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.new_ui_ic_connection_method),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NewUiColors.Accent),
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.new_ui_connection_method),
                style = NewUiTypography.CompactSettingTitle,
                color = NewUiColors.TextPrimary,
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = NewUiTypography.SettingSubtitle,
                    color = NewUiColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        NewUiChevron(
            tint = NewUiColors.TextSecondary.copy(alpha = 0.7f),
            size = 24.dp,
        )
    }
}

@Composable
private fun LocationReportingRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val shape = RoundedCornerShape(NewUiLayout.InsetRowRadius)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = NewUiLayout.LocationRowHeight)
            .clip(shape)
            .background(Color.White.copy(alpha = 0.04f))
            .border(1.dp, NewUiColors.Divider.copy(alpha = 0.5f), shape)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NewUiSettingIcon(R.drawable.new_ui_ic_stat_location)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.new_ui_location_title),
                style = NewUiTypography.CompactSettingTitle,
                color = NewUiColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.new_ui_location_subtitle),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        NewUiToggle(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun ConnectionActionsPanel(
    snapshot: NewUiConnectionSnapshot,
    onEnterCarPlay: () -> Unit,
    onPhoneAction: () -> Unit,
    onDisconnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        val connecting = snapshot.sessionRunning && !snapshot.isConnected && !snapshot.isFailed
        val enterTitle = when {
            connecting -> stringResource(R.string.connecting_to_your_iphone)
            snapshot.isConnected -> stringResource(R.string.open_carplay)
            snapshot.isFailed -> stringResource(R.string.retry_carplay_connection)
            else -> stringResource(R.string.new_ui_enter_carplay)
        }
        val enterIcon = when {
            snapshot.isFailed -> R.drawable.new_ui_ic_update
            else -> R.drawable.new_ui_ic_apple
        }
        VerticalActionButton(
            modifier = Modifier
                .weight(1.15f)
                .fillMaxWidth(),
            iconRes = enterIcon,
            title = enterTitle,
            subtitle = null,
            primary = true,
            connecting = connecting,
            enabled = snapshot.canConnect && snapshot.hasSelectedPhone && !connecting,
            onClick = onEnterCarPlay,
        )
        VerticalActionButton(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            iconRes = R.drawable.new_ui_ic_phone_switch,
            title = if (snapshot.hasSelectedPhone) {
                stringResource(R.string.new_ui_action_switch_phone)
            } else {
                stringResource(R.string.connect_phone)
            },
            subtitle = if (snapshot.hasSelectedPhone) {
                stringResource(R.string.new_ui_action_switch_phone_sub, snapshot.phoneName)
            } else {
                stringResource(R.string.new_ui_connect_phone_sub)
            },
            primary = false,
            enabled = !connecting,
            onClick = onPhoneAction,
        )
        VerticalActionButton(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            iconRes = R.drawable.new_ui_ic_disconnect,
            title = stringResource(R.string.disconnect),
            subtitle = stringResource(R.string.new_ui_action_disconnect_sub),
            primary = false,
            enabled = snapshot.sessionRunning,
            destructive = true,
            onClick = onDisconnect,
        )
    }
}

@Composable
private fun VerticalActionButton(
    iconRes: Int,
    title: String,
    subtitle: String?,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    connecting: Boolean = false,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(NewUiLayout.ActionButtonRadius)
    val alpha = when {
        connecting && primary -> 0.7f
        !enabled -> if (primary) 0.7f else 0.45f
        else -> 1f
    }
    Box(
        modifier = modifier
            .heightIn(min = NewUiLayout.ActionButtonMinHeight)
            .newUiPressScale(enabled = enabled, interactionSource = interactionSource)
            .alpha(alpha)
            .then(
                if (primary) {
                    if (connecting) {
                        Modifier
                            .clip(shape)
                            .background(NewUiColors.PrimaryBlue.copy(alpha = 0.4f))
                    } else {
                        Modifier
                            .shadow(
                                elevation = 16.dp,
                                shape = shape,
                                ambientColor = NewUiColors.PrimaryGlowSoft,
                                spotColor = NewUiColors.PrimaryGlowStrong,
                            )
                            .newUiAccentGloss(cornerRadius = NewUiLayout.ActionButtonRadius)
                    }
                } else {
                    Modifier
                        .clip(shape)
                        .background(NewUiColors.ActionPanelFill)
                        .border(1.dp, NewUiColors.Divider, shape)
                },
            )
            .clip(shape)
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        if (primary && !connecting) {
            NewUiControlTopGloss(strong = true)
        }
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    when {
                        destructive -> Color(0xFFF87171)
                        primary -> NewUiColors.TextPrimary
                        else -> NewUiColors.Accent
                    },
                ),
                modifier = Modifier.size(if (primary) 52.dp else 34.dp),
            )
            Spacer(Modifier.width(20.dp))
            if (subtitle == null) {
                Text(
                    title,
                    style = NewUiTypography.PrimaryActionTitle,
                    color = NewUiColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = NewUiTypography.SecondaryActionTitle,
                        color = if (destructive) Color(0xFFFECACA) else NewUiColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        subtitle,
                        style = NewUiTypography.ActionSubtitle,
                        color = NewUiColors.TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (connecting && primary) {
                NewUiLoaderIcon(
                    tint = NewUiColors.TextPrimary.copy(alpha = 0.6f),
                    size = 36.dp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            } else {
                NewUiChevron(
                    tint = NewUiColors.TextSecondary.copy(alpha = if (enabled) 0.6f else 0.35f),
                    size = if (primary) 36.dp else 28.dp,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}
