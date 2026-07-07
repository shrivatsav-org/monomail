package com.shrivatsav.monomail.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun systemTextStyle() = TextStyle(fontFamily = FontFamily.Default)

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    displayMedium = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 45.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp
    ),
    displaySmall = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 36.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.15.sp
    ),
    titleSmall = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W600,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W400,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W400,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    bodySmall = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W400,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    labelSmall = TextStyle(
        fontFamily = GoogleSansRoundedFamily,
        fontWeight = FontWeight.W500,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

/** Same structure as [AppTypography] but uses [FontFamily.Default] (system font). */
val SystemTypography = Typography(
    displayLarge = AppTypography.displayLarge.copy(fontFamily = FontFamily.Default),
    displayMedium = AppTypography.displayMedium.copy(fontFamily = FontFamily.Default),
    displaySmall = AppTypography.displaySmall.copy(fontFamily = FontFamily.Default),
    headlineLarge = AppTypography.headlineLarge.copy(fontFamily = FontFamily.Default),
    headlineMedium = AppTypography.headlineMedium.copy(fontFamily = FontFamily.Default),
    headlineSmall = AppTypography.headlineSmall.copy(fontFamily = FontFamily.Default),
    titleLarge = AppTypography.titleLarge.copy(fontFamily = FontFamily.Default),
    titleMedium = AppTypography.titleMedium.copy(fontFamily = FontFamily.Default),
    titleSmall = AppTypography.titleSmall.copy(fontFamily = FontFamily.Default),
    bodyLarge = AppTypography.bodyLarge.copy(fontFamily = FontFamily.Default),
    bodyMedium = AppTypography.bodyMedium.copy(fontFamily = FontFamily.Default),
    bodySmall = AppTypography.bodySmall.copy(fontFamily = FontFamily.Default),
    labelLarge = AppTypography.labelLarge.copy(fontFamily = FontFamily.Default),
    labelMedium = AppTypography.labelMedium.copy(fontFamily = FontFamily.Default),
    labelSmall = AppTypography.labelSmall.copy(fontFamily = FontFamily.Default),
)