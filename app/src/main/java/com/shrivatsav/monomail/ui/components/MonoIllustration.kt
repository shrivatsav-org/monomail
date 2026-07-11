package com.shrivatsav.monomail.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class IllustrationType {
    ENVELOPE,
    INBOX_ZERO,
    SEARCH_EMPTY,
    SHIELD,
    PAPER_PLANE,
    CONNECTION,
    ERROR_CLOUD
}

/**
 * Reusable Canvas-drawn illustrations.
 * Uses filled silhouettes for bolder visual weight as requested.
 */
@Composable
fun MonoIllustration(
    type: IllustrationType,
    modifier: Modifier = Modifier,
    size: Dp = 160.dp,
    animated: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.onSurface
    val glowColor = primaryColor.copy(alpha = 0.05f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    var isDrawn by remember { mutableStateOf(!animated) }
    
    LaunchedEffect(Unit) {
        if (animated) {
            isDrawn = true
        }
    }
    
    val drawProgress by animateFloatAsState(
        targetValue = if (isDrawn) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "illustrationProgress"
    )

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        // Glow background
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val radius = size.toPx() * 0.4f * drawProgress
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    center = center,
                    radius = if (radius > 0) radius else 1f
                ),
                center = center,
                radius = radius
            )
        }
        
        // Foreground silhouette
        Canvas(modifier = Modifier.size(size * 0.6f)) {
            val width = size.toPx() * 0.6f
            val height = width
            
            // Apply scale based on draw progress for a pop-in effect
            val scale = 0.8f + (0.2f * drawProgress)
            val alpha = drawProgress
            
            drawContext.transform.scale(scale, scale, Offset(width / 2, height / 2))
            
            when (type) {
                IllustrationType.ENVELOPE -> {
                    val rect = Rect(width * 0.1f, height * 0.25f, width * 0.9f, height * 0.75f)
                    drawRoundRect(
                        color = primaryColor.copy(alpha = alpha),
                        topLeft = rect.topLeft,
                        size = rect.size,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(16f, 16f)
                    )
                    // Flap line (drawn with background color to carve out silhouette)
                    val flapPath = Path().apply {
                        moveTo(width * 0.1f, height * 0.25f)
                        lineTo(width * 0.5f, height * 0.55f)
                        lineTo(width * 0.9f, height * 0.25f)
                    }
                    drawPath(
                        path = flapPath,
                        color = surfaceColor,
                        style = Stroke(width = 8f)
                    )
                }
                IllustrationType.INBOX_ZERO -> {
                    // Checkmark in a circle
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha),
                        center = Offset(width / 2, height / 2),
                        radius = width * 0.4f
                    )
                    val checkPath = Path().apply {
                        moveTo(width * 0.35f, height * 0.5f)
                        lineTo(width * 0.45f, height * 0.6f)
                        lineTo(width * 0.65f, height * 0.4f)
                    }
                    drawPath(
                        path = checkPath,
                        color = surfaceColor,
                        style = Stroke(width = 12f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }
                IllustrationType.SEARCH_EMPTY -> {
                    drawCircle(
                        color = primaryColor.copy(alpha = alpha),
                        center = Offset(width * 0.45f, height * 0.45f),
                        radius = width * 0.3f,
                        style = Stroke(width = 16f)
                    )
                    drawLine(
                        color = primaryColor.copy(alpha = alpha),
                        start = Offset(width * 0.65f, height * 0.65f),
                        end = Offset(width * 0.85f, height * 0.85f),
                        strokeWidth = 16f,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
                IllustrationType.SHIELD -> {
                    val shieldPath = Path().apply {
                        moveTo(width * 0.5f, height * 0.1f)
                        lineTo(width * 0.85f, height * 0.25f)
                        lineTo(width * 0.85f, height * 0.55f)
                        quadraticBezierTo(width * 0.85f, height * 0.8f, width * 0.5f, height * 0.9f)
                        quadraticBezierTo(width * 0.15f, height * 0.8f, width * 0.15f, height * 0.55f)
                        lineTo(width * 0.15f, height * 0.25f)
                        close()
                    }
                    drawPath(
                        path = shieldPath,
                        color = primaryColor.copy(alpha = alpha)
                    )
                }
                IllustrationType.PAPER_PLANE -> {
                    val planePath = Path().apply {
                        moveTo(width * 0.9f, height * 0.1f)
                        lineTo(width * 0.1f, height * 0.45f)
                        lineTo(width * 0.45f, height * 0.55f)
                        lineTo(width * 0.9f, height * 0.1f)
                        moveTo(width * 0.9f, height * 0.1f)
                        lineTo(width * 0.55f, height * 0.9f)
                        lineTo(width * 0.45f, height * 0.55f)
                    }
                    drawPath(
                        path = planePath,
                        color = primaryColor.copy(alpha = alpha)
                    )
                }
                IllustrationType.CONNECTION -> {
                    drawCircle(color = primaryColor.copy(alpha = alpha), radius = width * 0.15f, center = Offset(width * 0.2f, height * 0.5f))
                    drawCircle(color = primaryColor.copy(alpha = alpha), radius = width * 0.15f, center = Offset(width * 0.8f, height * 0.5f))
                    drawLine(
                        color = primaryColor.copy(alpha = alpha),
                        start = Offset(width * 0.35f, height * 0.5f),
                        end = Offset(width * 0.65f, height * 0.5f),
                        strokeWidth = 12f
                    )
                }
                IllustrationType.ERROR_CLOUD -> {
                    val cloudPath = Path().apply {
                        moveTo(width * 0.25f, height * 0.6f)
                        cubicTo(width * 0.1f, height * 0.6f, width * 0.1f, height * 0.8f, width * 0.25f, height * 0.8f)
                        lineTo(width * 0.75f, height * 0.8f)
                        cubicTo(width * 0.9f, height * 0.8f, width * 0.9f, height * 0.6f, width * 0.75f, height * 0.6f)
                        cubicTo(width * 0.75f, height * 0.4f, width * 0.5f, height * 0.3f, width * 0.45f, height * 0.5f)
                        cubicTo(width * 0.35f, height * 0.4f, width * 0.2f, height * 0.45f, width * 0.25f, height * 0.6f)
                        close()
                    }
                    drawPath(
                        path = cloudPath,
                        color = primaryColor.copy(alpha = alpha)
                    )
                }
            }
        }
    }
}
