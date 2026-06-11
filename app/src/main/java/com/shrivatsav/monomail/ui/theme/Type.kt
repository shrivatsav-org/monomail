package com.shrivatsav.monomail.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.shrivatsav.monomail.R

val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage   = "com.google.android.gms",
    certificates      = R.array.com_google_android_gms_fonts_certs
)

val GoogleSans = FontFamily(
    Font(googleFont = GoogleFont("Google Sans"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Google Sans"), fontProvider = provider, weight = FontWeight.Medium),
    Font(googleFont = GoogleFont("Google Sans"), fontProvider = provider, weight = FontWeight.Bold),
)

val Roboto = FontFamily(
    Font(googleFont = GoogleFont("Roboto"), fontProvider = provider, weight = FontWeight.Normal),
    Font(googleFont = GoogleFont("Roboto"), fontProvider = provider, weight = FontWeight.Medium),
)

val MonoMailTypography = Typography(
    // Google Sans — headings, titles, UI labels, buttons
    displayLarge   = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 57.sp, lineHeight = 64.sp),
    displayMedium  = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 45.sp, lineHeight = 52.sp),
    displaySmall   = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Normal, fontSize = 36.sp, lineHeight = 44.sp),
    headlineLarge  = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold,   fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold,   fontSize = 28.sp, lineHeight = 36.sp),
    headlineSmall  = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold,   fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge     = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Bold,   fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium    = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall     = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge     = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium    = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall     = TextStyle(fontFamily = GoogleSans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp),

    // Roboto — body text, snippets, metadata
    bodyLarge  = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall  = TextStyle(fontFamily = Roboto, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp),
)