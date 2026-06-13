package com.shrivatsav.monomail.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary                = Black,
    onPrimary              = White,
    primaryContainer       = LightSurfaceContainerHigh,
    onPrimaryContainer     = Black,
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
    outline                = Black,
    outlineVariant         = LightOutlineVariant,
    inverseSurface         = Black,
    inverseOnSurface       = White,
    scrim                  = Black,
)

private val DarkColors = darkColorScheme(
    primary                = White,
    onPrimary              = Black,
    primaryContainer       = DarkSurfaceContainerHigh,
    onPrimaryContainer     = White,
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
    outline                = White,
    outlineVariant         = DarkOutlineVariant,
    inverseSurface         = White,
    inverseOnSurface       = Black,
    scrim                  = Black,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MonoMailTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialExpressiveTheme(
        colorScheme  = if (darkTheme) DarkColors else LightColors,
        typography   = AppTypography,
        shapes       = MonoMailShapes,
        motionScheme = MotionScheme.expressive(),
        content      = content
    )
}