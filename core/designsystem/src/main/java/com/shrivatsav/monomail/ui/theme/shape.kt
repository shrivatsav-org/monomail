package com.shrivatsav.monomail.ui.theme
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalCornerStyle = staticCompositionLocalOf { "ROUNDED" }

@Composable
fun cornerShape(baseDp: Dp): RoundedCornerShape {
    return when (LocalCornerStyle.current.uppercase()) {
        "SQUARE" -> when {
            baseDp <= 8.dp -> RoundedCornerShape(0.dp)
            baseDp <= 12.dp -> RoundedCornerShape(2.dp)
            baseDp <= 16.dp -> RoundedCornerShape(4.dp)
            baseDp <= 20.dp -> RoundedCornerShape(6.dp)
            else -> RoundedCornerShape(8.dp)
        }
        "EXTRA_ROUNDED" -> when {
            baseDp <= 8.dp -> RoundedCornerShape(12.dp)
            baseDp <= 12.dp -> RoundedCornerShape(16.dp)
            baseDp <= 16.dp -> RoundedCornerShape(20.dp)
            baseDp <= 20.dp -> RoundedCornerShape(24.dp)
            else -> RoundedCornerShape(28.dp)
        }
        else -> RoundedCornerShape(baseDp)
    }
}
val MonoMailShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small      = RoundedCornerShape(12.dp),
    medium     = RoundedCornerShape(16.dp),
    large      = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

fun getMonoMailShapes(style: String): Shapes = when (style.uppercase()) {
    "SQUARE" -> Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small      = RoundedCornerShape(2.dp),
        medium     = RoundedCornerShape(4.dp),
        large      = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(12.dp),
    )
    "EXTRA_ROUNDED" -> Shapes(
        extraSmall = RoundedCornerShape(12.dp),
        small      = RoundedCornerShape(16.dp),
        medium     = RoundedCornerShape(24.dp),
        large      = RoundedCornerShape(32.dp),
        extraLarge = RoundedCornerShape(48.dp),
    )
    else -> Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small      = RoundedCornerShape(12.dp),
        medium     = RoundedCornerShape(16.dp),
        large      = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(32.dp),
    )
}