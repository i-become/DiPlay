package com.shilapi.xcertplay.newui.settings

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.shilapi.xcertplay.AirPlayPersistence
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.newui.NewUiColors
import com.shilapi.xcertplay.newui.NewUiLayout
import com.shilapi.xcertplay.newui.NewUiSecondaryActionButton
import com.shilapi.xcertplay.newui.NewUiSubpageBottomBar
import com.shilapi.xcertplay.newui.NewUiSubpageTitle
import com.shilapi.xcertplay.newui.NewUiTypography

@Composable
fun NewUiSettingsVehicleIconScreen(
    iconRevision: Int,
    onBack: () -> Unit,
    onChooseImage: () -> Unit,
    onResetDefault: () -> Unit,
) {
    val context = LocalContext.current
    val customFile = remember(iconRevision) { AirPlayPersistence.loadCustomAirPlayIconFile(context) }
    val customBitmap = remember(iconRevision, customFile) {
        customFile?.let { BitmapFactory.decodeFile(it.absolutePath) }
    }
    val shape = RoundedCornerShape(16.dp)
    Column(Modifier.fillMaxSize()) {
        NewUiSubpageTitle(R.string.new_ui_settings_vehicle_icon)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = NewUiLayout.RowGap),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                if (customBitmap != null) {
                    Image(
                        bitmap = customBitmap.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shape)
                            .border(1.dp, NewUiColors.CardBorder, shape),
                    )
                } else {
                    Image(
                        painter = painterResource(R.raw.ic_car_home),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(shape)
                            .background(NewUiColors.PillButton)
                            .border(1.dp, NewUiColors.CardBorder, shape),
                    )
                }
                Spacer(Modifier.size(16.dp))
                Column {
                    Text(
                        stringResource(R.string.car_button_icon),
                        style = NewUiTypography.SettingTitle,
                        color = NewUiColors.TextPrimary,
                    )
                    Text(
                        stringResource(
                            if (customBitmap != null) {
                                R.string.car_button_icon_custom
                            } else {
                                R.string.default_icon
                            },
                        ),
                        style = NewUiTypography.SettingSubtitle,
                        color = NewUiColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Text(
                stringResource(R.string.car_button_description),
                style = NewUiTypography.SettingSubtitle,
                color = NewUiColors.TextMuted,
            )
            if (customBitmap != null) {
                NewUiSecondaryActionButton(
                    label = stringResource(R.string.default_icon),
                    onClick = onResetDefault,
                )
            }
        }
        NewUiSubpageBottomBar(
            backLabelRes = R.string.new_ui_back_to_settings,
            onBack = onBack,
            primaryLabelRes = R.string.choose_image,
            onPrimary = onChooseImage,
        )
    }
}
