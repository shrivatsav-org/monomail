package com.shrivatsav.monomail.ui.theme

import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)

val LightBackground           = Color(0xFFFFFFFF)
val LightSurface              = Color(0xFFFAFAFA)
val LightSurfaceContainer     = Color(0xFFF3F3F3)
val LightSurfaceContainerHigh = Color(0xFFEEEEEE)
val LightOutlineVariant       = Color(0xFFE2E2E2)
val LightOnSurfaceMuted       = Color(0xFF5C5C5C) // AA-contrast on white surfaces

val DarkBackground           = Color(0xFF000000)
val DarkSurface              = Color(0xFF0D0D0D)
val DarkSurfaceContainer     = Color(0xFF141414)
val DarkSurfaceContainerHigh = Color(0xFF1C1C1C)
val DarkOutlineVariant       = Color(0xFF2A2A2A)
val DarkOnSurfaceMuted       = Color(0xFFA0A0A0) // AA-contrast on black surfaces

// Opacity tokens for consistent text hierarchy
object MonoOpacity {
    const val heading = 1.0f
    const val body = 0.80f
    const val secondary = 0.60f
    const val tertiary = 0.45f
    const val disabled = 0.30f
    const val subtle = 0.15f
    const val whisper = 0.08f
}

// Tinted shadow colors (never pure black/grey)
object MonoShadows {
    val light = Color(0x1A000000)  // 10% black
    val medium = Color(0x26000000) // 15% black
    val strong = Color(0x33000000) // 20% black
    val dark = Color(0x1AFFFFFF)   // 10% white (for dark mode)
    val darkMedium = Color(0x26FFFFFF)
}