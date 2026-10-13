package com.shilapi.xcertplay.newui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSingleChoicePanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

enum class NewUiAudioChannelKind {
    MEDIA,
    NAVIGATION,
}

@Composable
fun NewUiSettingsAudioChannelScreen(
    kind: NewUiAudioChannelKind,
    selectedChannel: Int,
    onBack: () -> Unit,
    onSelect: (Int) -> Unit,
    onPreview: (Int) -> Unit,
) {
    val titleRes = when (kind) {
        NewUiAudioChannelKind.MEDIA -> R.string.contrib_audio_home_media_channel_label
        NewUiAudioChannelKind.NAVIGATION -> R.string.contrib_audio_home_nav_channel_label
    }
    val autoLabel = stringResource(R.string.auto_label)
    val options = AirPlayPersistence.AUDIO_CHANNELS.map { channel ->
        if (channel == 0) autoLabel else channel.toString()
    }
    val selectedIndex = AirPlayPersistence.AUDIO_CHANNELS.indexOf(selectedChannel).coerceAtLeast(0)
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(titleRes)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.contrib_audio_home_nav_channel_note),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiSingleChoicePanel(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    onSelect(AirPlayPersistence.AUDIO_CHANNELS.elementAt(index))
                },
                showDividerOnLast = false,
            )
            if (selectedChannel != 0) {
                NewUiSecondaryActionButton(
                    label = stringResource(R.string.new_ui_audio_channel_preview),
                    onClick = { onPreview(selectedChannel) },
                    modifier = Modifier
                        .padding(top = NewUiLayout.MainGap),
                )
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
        )
    }
}
