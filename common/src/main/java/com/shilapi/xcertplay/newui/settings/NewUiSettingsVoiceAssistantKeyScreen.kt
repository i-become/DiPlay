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
import com.shilapi.xcertplay.VoiceAssistantKeyTarget
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiCompactToggleRow
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSingleChoicePanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsVoiceAssistantKeyScreen(
    customVoiceKeyEnabled: Boolean,
    singleTarget: VoiceAssistantKeyTarget,
    doubleTarget: VoiceAssistantKeyTarget,
    onCustomVoiceKeyEnabledChanged: (Boolean) -> Unit,
    onSingleTargetChanged: (VoiceAssistantKeyTarget) -> Unit,
    onDoubleTargetChanged: (VoiceAssistantKeyTarget) -> Unit,
    onBack: () -> Unit,
) {
    val options = VoiceAssistantKeyTarget.entries.map { targetLabel(it) }
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.settings_voice_assistant_key_title)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
        ) {
            Text(
                stringResource(R.string.settings_voice_assistant_key_description),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
            )
            NewUiCompactToggleRow(
                title = stringResource(R.string.settings_voice_assistant_key_custom_toggle),
                subtitle = stringResource(R.string.settings_voice_assistant_key_custom_toggle_sub),
                checked = customVoiceKeyEnabled,
                onCheckedChange = onCustomVoiceKeyEnabledChanged,
            )
            if (customVoiceKeyEnabled) {
                Text(
                    stringResource(R.string.settings_voice_assistant_key_single_label),
                    style = NewUiTypography.SettingTitle,
                    color = NewUiColors.TextPrimary,
                    modifier = Modifier.padding(top = NewUiLayout.MainGap, bottom = NewUiLayout.RowGap),
                )
                NewUiSingleChoicePanel(
                    options = options,
                    selectedIndex = VoiceAssistantKeyTarget.entries.indexOf(singleTarget).coerceAtLeast(0),
                    onSelect = { index ->
                        onSingleTargetChanged(VoiceAssistantKeyTarget.entries[index])
                    },
                )
                Text(
                    stringResource(R.string.settings_voice_assistant_key_double_label),
                    style = NewUiTypography.SettingTitle,
                    color = NewUiColors.TextPrimary,
                    modifier = Modifier.padding(top = NewUiLayout.MainGap, bottom = NewUiLayout.RowGap),
                )
                NewUiSingleChoicePanel(
                    options = options,
                    selectedIndex = VoiceAssistantKeyTarget.entries.indexOf(doubleTarget).coerceAtLeast(0),
                    onSelect = { index ->
                        onDoubleTargetChanged(VoiceAssistantKeyTarget.entries[index])
                    },
                    showDividerOnLast = false,
                )
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
        )
    }
}

@Composable
private fun targetLabel(target: VoiceAssistantKeyTarget): String = when (target) {
    VoiceAssistantKeyTarget.HONGQI -> stringResource(R.string.settings_voice_assistant_key_action_hongqi)
    VoiceAssistantKeyTarget.SIRI -> stringResource(R.string.settings_voice_assistant_key_action_siri)
}
