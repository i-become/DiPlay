package com.shilapi.xcertplay.newui



import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.sp



/** Paired HS5 day/night colors (#AARRGGBB), shared by every configuration page. */

object NewUiColors {
    // One application chrome appearance, shared by Compose and legacy View surfaces.
    // Snapshot reads invalidate existing compositions when the vehicle lights change.
    var night by mutableStateOf(true)
        internal set
    private fun tone(dark: Long, light: Long) = Color(if (night) dark else light)


    val BackgroundOverlay get() = tone(0xCC100E23, 0xCCECEAF3)

    val CardNormal get() = tone(0xFF1C1B29, 0xFFF9F8FC)

    val CardSecondary get() = tone(0xFF242230, 0xFFECE9F2)

    val ButtonNormal get() = tone(0xFF343141, 0xFFE2DEEB)

    val PrimaryBlue get() = tone(0xFF6D6986, 0xFFADA4C1)

    val MenuSelected get() = tone(0xFF403B53, 0xFFDDD7E9)

    val TextPrimary get() = tone(0xFFE2E4E6, 0xFF242131)

    val TextSecondary get() = tone(0xFFC4C6CF, 0xFF575263)

    val Divider get() = tone(0xFF464252, 0xFFA7A0B5)

    val SwitchTrackOff get() = tone(0xFF35333E, 0xFFADA6B8)

    val SwitchThumb get() = tone(0xFFE2E4E6, 0xFFF9F8FC)

    val DropdownBackground get() = tone(0xFF292635, 0xFFF3F0F7)

    val DropdownBorder get() = tone(0xFF4B465A, 0xFFA39BAF)



    /** Alpha variants of palette RGB (glass / glow only). */

    val BackgroundVeilLight get() = tone(0x33000000, 0x0C514563)

    val BackgroundVeilMid get() = tone(0x00000000, 0x00000000)

    val BackgroundVeilBottom get() = tone(0x55000000, 0x10514563)

    val CardGlassIdle get() = tone(0xFF1C1B29, 0xFFF9F8FC)

    val CardSheen get() = tone(0x08BDBDBD, 0x66FFFFFF)

    val PrimaryGlowSoft get() = tone(0x186D6986, 0x12514563)

    val PrimaryGlowStrong get() = tone(0x286D6986, 0x20514563)

    val MenuSelectedFade get() = tone(0x00403B53, 0x00DDD7E9)



    val Canvas get() = tone(0xFF100E23, 0xFFE7E3EF)

    val CanvasBottom get() = tone(0xFF050307, 0xFFF3F1F7)

    val LoadingOverlay get() = tone(0xFFE2E4E6, 0xFFE2E4E6)

    val CardFill get() = CardNormal

    val CardFillElevated get() = CardSecondary

    val PillButton get() = ButtonNormal

    val Accent get() = tone(0xFFC4C1D8, 0xFF514563)

    val OnAccent get() = if (night) CanvasBottom else Color(0xFFF9F8FC)

    val PrimaryButtonFill get() = tone(0xFF514C6B, 0xFFDDD7E9)

    val AccentDeep get() = MenuSelected

    val SidebarSelected get() = MenuSelected

    val CardBorderTop get() = tone(0xFF5A556B, 0xFFB9B1C9)

    val CardBorderBottom get() = tone(0xFF353242, 0xFFD0C9DD)

    val CardBorder get() = DropdownBorder

    val CardBorderGlow get() = PrimaryBlue

    val TextMuted get() = TextSecondary

    val SwitchTrackOn get() = if (night) PrimaryBlue else Accent

    val TopBarGlassTop get() = tone(0x66100E23, 0x99E7E3EF)

    val TopBarGlassBottom get() = tone(0x00100E23, 0x00E7E3EF)

    val HongqiRed get() = tone(0xFFC8102E, 0xFFB20D29)

    val Success get() = StatusReady

    val Danger get() = tone(0xFFF87171, 0xFFAC233A)

    val DangerText get() = tone(0xFFFECACA, 0xFFAC233A)

    val StatusDotIdle get() = SwitchTrackOff

    val CardPanel get() = tone(0xFF1C1B29, 0xFFF9F8FC)

    val StatusReady get() = tone(0xFFC4CCD0, 0xFF415A50)

    val StatusReadySoft get() = tone(0xFFE2E4E6, 0xFF415A50)

    val StatusConnecting get() = tone(0xFFCAC7DB, 0xFF514563)

    val ProgressDotIdle get() = tone(0xFF575460, 0xFFACA5B9)

    val ProgressDotActive get() = tone(0xFFC4C1D8, 0xFF514563)

    val ActionPanelFill get() = tone(0xFF242230, 0xFFECE9F2)

}



private val UiFont = FontFamily.SansSerif



object NewUiTypography {

    val BrandHongqi = TextStyle(fontFamily = UiFont, fontSize = 26.sp, fontWeight = FontWeight.Bold)

    val BrandModel = TextStyle(fontFamily = UiFont, fontSize = 22.sp, fontWeight = FontWeight.Medium)

    val BrandProduct = TextStyle(fontFamily = UiFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

    val BrandTagline = TextStyle(fontFamily = UiFont, fontSize = 15.sp, fontWeight = FontWeight.Normal)

    val TopTime = TextStyle(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.Medium)

    val NavItem = TextStyle(fontFamily = UiFont, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)

    val StatusLabel = TextStyle(fontFamily = UiFont, fontSize = 18.sp, fontWeight = FontWeight.Medium)

    val PhoneName = TextStyle(fontFamily = UiFont, fontSize = 32.sp, fontWeight = FontWeight.Bold)

    val PhoneNameHero = TextStyle(fontFamily = UiFont, fontSize = 52.sp, fontWeight = FontWeight.Bold)

    val PageTitle = TextStyle(fontFamily = UiFont, fontSize = 32.sp, fontWeight = FontWeight.Bold)

    val NavTab = TextStyle(fontFamily = UiFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

    val PrimaryActionTitle = TextStyle(fontFamily = UiFont, fontSize = 34.sp, fontWeight = FontWeight.Bold)

    val SecondaryActionTitle = TextStyle(fontFamily = UiFont, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)

    val CompactSettingTitle = TextStyle(fontFamily = UiFont, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)

    val ProgressLabel = TextStyle(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Medium)

    val ProgressCounter = TextStyle(fontFamily = UiFont, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)

    val ProgressStepTitle = TextStyle(fontFamily = UiFont, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)

    val PhaseStatus = TextStyle(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.Medium)

    val LinkDetail = TextStyle(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.Normal)

    val ActionTitle = TextStyle(fontFamily = UiFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

    val ActionSubtitle = TextStyle(fontFamily = UiFont, fontSize = 15.sp, fontWeight = FontWeight.Normal)

    val SettingTitle = TextStyle(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    val SettingSubtitle = TextStyle(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Normal)

}



private fun scheme() = (if (NewUiColors.night) darkColorScheme() else lightColorScheme()).copy(

    primary = NewUiColors.Accent,

    onPrimary = NewUiColors.OnAccent,

    background = NewUiColors.Canvas,

    onBackground = NewUiColors.TextPrimary,

    surface = NewUiColors.CardNormal,

    onSurface = NewUiColors.TextPrimary,
    secondary = NewUiColors.Accent,
    onSecondary = NewUiColors.OnAccent,
    tertiary = NewUiColors.Accent,
    onTertiary = NewUiColors.OnAccent,
    surfaceVariant = NewUiColors.CardSecondary,
    onSurfaceVariant = NewUiColors.TextSecondary,
    outline = NewUiColors.Divider,

)



@Composable

fun NewUiTheme(content: @Composable () -> Unit) {

    MaterialTheme(

        colorScheme = scheme(),

        typography = MaterialTheme.typography.copy(

            bodyLarge = TextStyle(fontFamily = UiFont),

            bodyMedium = TextStyle(fontFamily = UiFont),

            labelLarge = TextStyle(fontFamily = UiFont),

        ),

        content = content,

    )

}


