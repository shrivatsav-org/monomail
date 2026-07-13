package com.shrivatsav.monomail.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Centralized motion tokens for consistent animations across the app.
 */
object MonoSpring {
    // For press feedback, FAB, and bouncy elements
    fun <T> bouncy() = spring<T>(
        dampingRatio = 0.65f,
        stiffness = 800f
    )
    
    // For layout shifts, expanding areas
    fun <T> gentle() = spring<T>(
        dampingRatio = 0.8f,
        stiffness = 400f
    )
    
    // For quick toggles, chips, selection states
    fun <T> snappy() = spring<T>(
        dampingRatio = 0.75f,
        stiffness = 1200f
    )
}

object MonoTween {
    // Standard content appearance
    val fadeIn = tween<Float>(durationMillis = 250, easing = LinearOutSlowInEasing)
    
    // Standard content removal
    val fadeOut = tween<Float>(durationMillis = 200, easing = FastOutSlowInEasing)
    
    // Screen transitions
    fun <T> slide() = tween<T>(durationMillis = 350, easing = FastOutSlowInEasing)
}

object MonoStagger {
    // Delay for staggered list items
    const val delay = 30
    const val maxDelay = 300
}
