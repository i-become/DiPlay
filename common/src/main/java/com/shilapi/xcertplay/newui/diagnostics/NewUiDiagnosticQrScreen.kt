package com.shilapi.xcertplay.newui.diagnostics

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.backup.SettingsQrCode
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiPanel
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography
import androidx.compose.foundation.layout.fillMaxSize

@Composable
fun NewUiDiagnosticQrScreen(
    shareUrl: String?,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_diag_qr_title)
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
            verticalArrangement = Arrangement.spacedBy(NewUiLayout.RowGap),
        ) {
            NewUiPanel {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.new_ui_diag_qr_sub),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        textAlign = TextAlign.Center,
                    )
                    if (shareUrl.isNullOrBlank()) {
                        Text(
                            stringResource(R.string.new_ui_diag_qr_no_network),
                            style = NewUiTypography.SettingTitle,
                            color = NewUiColors.TextPrimary,
                            modifier = Modifier.padding(top = 20.dp),
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        val sizePx = with(LocalDensity.current) { 220.dp.roundToPx() }
                        val bitmap = SettingsQrCode.encode(shareUrl, sizePx)
                        val shape = RoundedCornerShape(12.dp)
                        Column(
                            modifier = Modifier
                                .padding(top = 20.dp)
                                .clip(shape)
                                .background(Color.White, shape)
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.new_ui_diag_qr_title),
                                modifier = Modifier.size(220.dp),
                            )
                        }
                        Text(
                            stringResource(R.string.new_ui_diag_qr_hint),
                            style = NewUiTypography.SettingSubtitle,
                            color = NewUiColors.TextMuted,
                            modifier = Modifier.padding(top = 16.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_diagnostics,
            onBack = onBack,
        )
    }
}
