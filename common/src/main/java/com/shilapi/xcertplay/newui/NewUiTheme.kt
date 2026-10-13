package com.shilapi.xcertplay.newui



import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.darkColorScheme

import androidx.compose.runtime.Composable

import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.TextStyle

import androidx.compose.ui.text.font.FontFamily

import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.sp



/** Fixed DiPlay native config UI palette (#AARRGGBB). */

object NewUiColors {

    val BackgroundOverlay = Color(0xCC071426)

    val CardNormal = Color(0xCC101F33)

    val CardSecondary = Color(0xE6142D4C)

    val ButtonNormal = Color(0xFF142D4C)

    val PrimaryBlue = Color(0xFF087BFF)

    val MenuSelected = Color(0xFF123D7C)

    val TextPrimary = Color(0xFFF2F7FF)

    val TextSecondary = Color(0xFF8FAED5)

    val Divider = Color(0xFF263C5A)

    val SwitchTrackOff = Color(0xFF34445B)

    val SwitchThumb = Color(0xFFFFFFFF)

    val DropdownBackground = Color(0xFF17345A)

    val DropdownBorder = Color(0xFF365578)



    /** Alpha variants of palette RGB (glass / glow only). */

    val BackgroundVeilLight = Color(0x33000000)

    val BackgroundVeilMid = Color(0x00000000)

    val BackgroundVeilBottom = Color(0x55000000)

    val CardGlassIdle = Color(0x66101F33)

    val CardSheen = Color(0x33F2F7FF)

    val PrimaryGlowSoft = Color(0x40087BFF)

    val PrimaryGlowStrong = Color(0x66087BFF)

    val MenuSelectedFade = Color(0x00123D7C)



    val Canvas = Color(0xFF071426)

    val CardFill = CardNormal

    val CardFillElevated = CardSecondary

    val PillButton = ButtonNormal

    val Accent = PrimaryBlue

    val AccentDeep = MenuSelected

    val SidebarSelected = MenuSelected

    val CardBorder = DropdownBorder

    val CardBorderGlow = PrimaryBlue

    val TextMuted = TextSecondary

    val SwitchTrackOn = PrimaryBlue

    val TopBarGlassTop = Color(0xAA071426)

    val TopBarGlassBottom = Color(0x00071426)

    val HongqiRed = Color(0xFFC8102E)

    val Success = PrimaryBlue

    val StatusDotIdle = SwitchTrackOff

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

    val LinkDetail = TextStyle(fontFamily = UiFont, fontSize = 16.sp, fontWeight = FontWeight.Normal)

    val ActionTitle = TextStyle(fontFamily = UiFont, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)

    val ActionSubtitle = TextStyle(fontFamily = UiFont, fontSize = 15.sp, fontWeight = FontWeight.Normal)

    val SettingTitle = TextStyle(fontFamily = UiFont, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

    val SettingSubtitle = TextStyle(fontFamily = UiFont, fontSize = 14.sp, fontWeight = FontWeight.Normal)

}



private val scheme = darkColorScheme(

    primary = NewUiColors.PrimaryBlue,

    onPrimary = NewUiColors.TextPrimary,

    background = NewUiColors.Canvas,

    onBackground = NewUiColors.TextPrimary,

    surface = NewUiColors.CardNormal,

    onSurface = NewUiColors.TextPrimary,

)



@Composable

fun NewUiTheme(content: @Composable () -> Unit) {

    MaterialTheme(

        colorScheme = scheme,

        typography = MaterialTheme.typography.copy(

            bodyLarge = TextStyle(fontFamily = UiFont),

            bodyMedium = TextStyle(fontFamily = UiFont),

            labelLarge = TextStyle(fontFamily = UiFont),

        ),

        content = content,

    )

}


