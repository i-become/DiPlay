package com.shilapi.xcertplay.newui.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsAboutScreen(
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
            Text(
                stringResource(R.string.new_ui_about_sec4_scan_hint),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp, bottom = NewUiLayout.MainGap),
            )
            CommunityQrSection(qrRes = communityQrRes)
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
private fun CommunityQrSection(@DrawableRes qrRes: Int) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .clip(shape)
            .background(NewUiColors.SwitchThumb, shape),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(qrRes),
            contentDescription = stringResource(R.string.new_ui_about_sec4_scan_hint),
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )
    }
}
