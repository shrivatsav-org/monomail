package com.shrivatsav.monomail.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary                = Black,
    onPrimary              = White,
    primaryContainer       = Black,
    onPrimaryContainer     = White,
    secondary              = Black,
    onSecondary            = White,
    secondaryContainer     = LightSurfaceContainer,
    onSecondaryContainer   = Black,
    tertiary               = Black,
    onTertiary             = White,
    tertiaryContainer      = LightSurfaceContainerHigh,
    onTertiaryContainer    = Black,
    background             = LightBackground,
    onBackground           = Black,
    surface                = LightSurface,
    onSurface              = Black,
    surfaceVariant         = LightSurfaceContainer,
    onSurfaceVariant       = Black,
    surfaceContainer       = LightSurfaceContainer,
    surfaceContainerHigh   = LightSurfaceContainerHigh,
    surfaceContainerHighest= Color(0xFFE0E0E0),
    surfaceContainerLow    = Color(0xFFF6F6F6),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceBright          = Color(0xFFFFFFFF),
    surfaceDim             = Color(0xFFE8E8E8),
    surfaceTint            = Color.Transparent,
    outline                = Black,
    outlineVariant         = LightOutlineVariant,
    inverseSurface         = Black,
    inverseOnSurface       = White,
    scrim                  = Black,
    error                  = Black,
    onError                = White,
    errorContainer         = LightSurfaceContainerHigh,
    onErrorContainer       = Black,
)

private val DarkColors = darkColorScheme(
    primary                = White,
    onPrimary              = Black,
    primaryContainer       = White,
    onPrimaryContainer     = Black,
    secondary              = White,
    onSecondary            = Black,
    secondaryContainer     = DarkSurfaceContainer,
    onSecondaryContainer   = White,
    tertiary               = White,
    onTertiary             = Black,
    tertiaryContainer      = DarkSurfaceContainerHigh,
    onTertiaryContainer    = White,
    background             = DarkBackground,
    onBackground           = White,
    surface                = DarkSurface,
    onSurface              = White,
    surfaceVariant         = DarkSurfaceContainer,
    onSurfaceVariant       = White,
    surfaceContainer       = DarkSurfaceContainer,
    surfaceContainerHigh   = DarkSurfaceContainerHigh,
    surfaceContainerHighest= Color(0xFF242424),
    surfaceContainerLow    = Color(0xFF0F0F0F),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceBright          = Color(0xFF2A2A2A),
    surfaceDim             = Color(0xFF0A0A0A),
    surfaceTint            = Color.Transparent,
    outline                = White,
    outlineVariant         = DarkOutlineVariant,
    inverseSurface         = White,
    inverseOnSurface       = Black,
    scrim                  = Black,
    error                  = White,
    onError                = Black,
    errorContainer         = DarkSurfaceContainerHigh,
    onErrorContainer       = White,
)


/** Extra tokens Material3's ColorScheme doesn't have a slot for. */
data class MonoMailExtendedColors(
    val onSurfaceMuted: Color,
)

private val LightExtendedColors = MonoMailExtendedColors(onSurfaceMuted = LightOnSurfaceMuted)
private val DarkExtendedColors  = MonoMailExtendedColors(onSurfaceMuted = DarkOnSurfaceMuted)

val LocalMonoMailExtendedColors = staticCompositionLocalOf { LightExtendedColors }

/** Access via `MonoMailTheme.extendedColors.onSurfaceMuted` inside composables. */
object MonoMailTheme {
    val extendedColors: MonoMailExtendedColors
        @Composable get() = LocalMonoMailExtendedColors.current
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MonoMailTheme(
    themeMode: String = "SYSTEM",
    useSystemFont: Boolean = false,
    cornerStyle: String = "ROUNDED",
    monochrome: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        "LIGHT" -> false
        "DARK"  -> true
        else    -> isSystemInDarkTheme()
    }
    val colorScheme = if (monochrome) {
        if (darkTheme) DarkColors else LightColors
    } else if (Build.VERSION.SDK_INT >= 31) {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        if (darkTheme) DarkColors else LightColors
    }
    val extendedColors = if (darkTheme) DarkExtendedColors else LightExtendedColors
    val typography = if (useSystemFont) SystemTypography else AppTypography
    val shapes = getMonoMailShapes(cornerStyle)

    CompositionLocalProvider(
        LocalMonoMailExtendedColors provides extendedColors,
        LocalCornerStyle provides cornerStyle
    ) {
        MaterialExpressiveTheme(
            colorScheme  = colorScheme,
            typography   = typography,
            shapes       = shapes,
            motionScheme = MotionScheme.expressive(),
            content      = content
        )
    }
}
