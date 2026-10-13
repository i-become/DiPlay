package com.shilapi.xcertplay.newui.connection

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiChevron
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiControlTopGloss
import com.shilapi.xcertplay.newui.NewUiGlassCard
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.newUiWindowMetrics
import com.shilapi.xcertplay.newui.NewUiSettingIcon
import com.shilapi.xcertplay.newui.NewUiStatusDot
import com.shilapi.xcertplay.newui.NewUiToggle
import com.shilapi.xcertplay.newui.NewUiTypography
import com.shilapi.xcertplay.newui.NewUiGlassSheenOverlay
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
    val metrics = newUiWindowMetrics()
    val heroMin = metrics.heroHeight
    val actionMin = metrics.actionRowHeight
    val locationMin = metrics.locationRowHeight
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
    ) {
        NewUiGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1.15f)
                .heightIn(min = heroMin),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp, vertical = 22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        NewUiStatusDot(connected = snapshot.isConnected)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = snapshot.statusTitle,
                            style = NewUiTypography.StatusLabel,
                            color = NewUiColors.TextPrimary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = snapshot.phoneName,
                        style = NewUiTypography.PhoneName,
                        color = NewUiColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    snapshot.linkSummary?.let { link ->
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = link,
                            style = NewUiTypography.LinkDetail,
                            color = NewUiColors.TextSecondary,
                        )
                    }
                }
                ConnectionMethodChip(
                    detail = snapshot.connectionMethodLabel,
                    onClick = onConnectionMethod,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(min = actionMin),
            horizontalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
        ) {
            InternalActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                iconRes = R.drawable.new_ui_ic_carplay_tile,
                title = stringResource(R.string.new_ui_enter_carplay),
                subtitle = when {
                    snapshot.isConnected -> stringResource(R.string.new_ui_action_carplay_connected_sub)
                    snapshot.sessionRunning -> stringResource(R.string.connecting_to_your_iphone)
                    else -> stringResource(R.string.new_ui_action_carplay_idle_sub)
                },
                primary = true,
                enabled = snapshot.canConnect,
                onClick = onEnterCarPlay,
            )
            InternalActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
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
                onClick = onPhoneAction,
            )
            InternalActionButton(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                iconRes = R.drawable.new_ui_ic_disconnect,
                title = stringResource(R.string.disconnect),
                subtitle = stringResource(R.string.new_ui_action_disconnect_sub),
                primary = false,
                enabled = snapshot.sessionRunning,
                onClick = onDisconnect,
            )
        }

        NewUiGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
                .heightIn(min = locationMin),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 22.dp),
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
                        style = NewUiTypography.SettingTitle,
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
                NewUiToggle(
                    checked = snapshot.locationReportingEnabled,
                    onCheckedChange = onLocationReportingChanged,
                )
            }
        }
    }
}

@Composable
private fun ConnectionMethodChip(detail: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .newUiGlassTile(cornerRadius = 14.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            painter = painterResource(R.drawable.new_ui_ic_connection_method),
            contentDescription = null,
            colorFilter = ColorFilter.tint(NewUiColors.Accent),
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                stringResource(R.string.new_ui_connection_method),
                style = NewUiTypography.SettingTitle,
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
        Spacer(Modifier.width(4.dp))
        NewUiChevron(tint = NewUiColors.TextSecondary)
    }
}

@Composable
private fun InternalActionButton(
    iconRes: Int,
    title: String,
    subtitle: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(NewUiLayout.CardRadius)
    Box(
        modifier = modifier
            .then(
                if (primary) {
                    Modifier.newUiAccentGloss(cornerRadius = NewUiLayout.CardRadius)
                } else {
                    Modifier.newUiGlassTile(cornerRadius = NewUiLayout.CardRadius)
                },
            )
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        if (!primary) {
            NewUiGlassSheenOverlay()
        }
        NewUiControlTopGloss(strong = primary)
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(iconRes),
                contentDescription = null,
                colorFilter = ColorFilter.tint(
                    if (primary) NewUiColors.TextPrimary else NewUiColors.TextSecondary,
                ),
                modifier = Modifier.size(34.dp),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp),
            ) {
                Text(
                    title,
                    style = NewUiTypography.SettingTitle,
                    color = when {
                        !enabled -> NewUiColors.TextMuted
                        else -> NewUiColors.TextPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    subtitle,
                    style = NewUiTypography.ActionSubtitle,
                    color = when {
                        !enabled -> NewUiColors.TextMuted
                        primary -> NewUiColors.TextPrimary
                        else -> NewUiColors.TextSecondary
                    },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            NewUiChevron(
                tint = when {
                    !enabled -> NewUiColors.TextMuted
                    primary -> NewUiColors.TextPrimary
                    else -> NewUiColors.TextSecondary
                },
            )
        }
    }
}
