package com.shilapi.xcertplay.newui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsAboutScreen(
    @DrawableRes paymentQrRes: Int = R.drawable.new_ui_about_payment_qr,
    @DrawableRes communityQrRes: Int = R.drawable.new_ui_about_douyin_qr,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = NewUiLayout.ScreenPaddingBottom),
    ) {
        Text(
            stringResource(R.string.about),
            style = NewUiTypography.ActionTitle,
            color = NewUiColors.TextPrimary,
            modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
        )
        Text(
            stringResource(R.string.new_ui_about_doc_title),
            style = NewUiTypography.ActionTitle,
            color = NewUiColors.TextPrimary,
            modifier = Modifier.padding(bottom = NewUiLayout.MainGap),
        )
        AboutSection(
            titleRes = R.string.new_ui_about_sec1_title,
            bodyRes = R.string.new_ui_about_sec1_body,
        )
        AboutSection(
            titleRes = R.string.new_ui_about_sec2_title,
            bodyRes = R.string.new_ui_about_sec2_body,
        )
        AboutSection(
            titleRes = R.string.new_ui_about_sec3_title,
            bodyRes = R.string.new_ui_about_sec3_body,
        )
        AboutSection(
            titleRes = R.string.new_ui_about_sec4_title,
            bodyRes = R.string.new_ui_about_sec4_body,
        )
        DualQrSection(
            paymentQrRes = paymentQrRes,
            communityQrRes = communityQrRes,
            paymentCaptionRes = R.string.new_ui_about_payment_caption,
            communityCaptionRes = R.string.new_ui_about_sec4_scan_hint,
        )
        Text(
            stringResource(R.string.new_ui_about_thanks),
            style = NewUiTypography.SettingSubtitle,
            color = NewUiColors.TextPrimary,
            modifier = Modifier.padding(top = NewUiLayout.MainGap),
        )
    }
}

@Composable
private fun AboutSection(
    titleRes: Int,
    bodyRes: Int,
) {
    Text(
        stringResource(titleRes),
        style = NewUiTypography.SettingTitle.copy(fontWeight = FontWeight.SemiBold),
        color = NewUiColors.TextPrimary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
    Text(
        stringResource(bodyRes),
        style = NewUiTypography.SettingSubtitle,
        color = NewUiColors.TextSecondary,
    )
}

@Composable
private fun DualQrSection(
    @DrawableRes paymentQrRes: Int,
    @DrawableRes communityQrRes: Int,
    @StringRes paymentCaptionRes: Int,
    @StringRes communityCaptionRes: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QrColumn(
            qrRes = paymentQrRes,
            contentDescriptionRes = paymentCaptionRes,
            captionRes = paymentCaptionRes,
            modifier = Modifier.weight(1f),
        )
        QrColumn(
            qrRes = communityQrRes,
            contentDescriptionRes = communityCaptionRes,
            captionRes = communityCaptionRes,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QrColumn(
    @DrawableRes qrRes: Int,
    @StringRes contentDescriptionRes: Int,
    @StringRes captionRes: Int,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(shape)
                .background(NewUiColors.SwitchThumb, shape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(qrRes),
                contentDescription = stringResource(contentDescriptionRes),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(8.dp),
            )
        }
        Text(
            stringResource(captionRes),
            style = NewUiTypography.SettingSubtitle,
            color = NewUiColors.TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
