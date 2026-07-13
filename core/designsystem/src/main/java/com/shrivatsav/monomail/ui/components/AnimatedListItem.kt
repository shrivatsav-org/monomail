package com.shrivatsav.monomail.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.shrivatsav.monomail.ui.theme.MonoStagger
import com.shrivatsav.monomail.ui.theme.MonoTween
import kotlinx.coroutines.delay

@Composable
fun AnimatedListItem(
    index: Int,
    itemKey: String = "",
    animatedItemsTracker: MutableSet<String>? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    if (itemKey.isNotEmpty() && animatedItemsTracker?.contains(itemKey) == true) {
        Box(modifier = modifier) { content() }
        return
    }

    var isVisible by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        val delayTime = (index * MonoStagger.delay).coerceAtMost(MonoStagger.maxDelay).toLong()
        delay(delayTime)
        isVisible = true
        if (itemKey.isNotEmpty()) {
            animatedItemsTracker?.add(itemKey)
        }
    }
    
    val alpha by animateFloatAsState(
        targetValue = if (isVisible) 1f else 0f,
        animationSpec = MonoTween.fadeIn,
        label = "listItemAlpha"
    )
    
    Box(
        modifier = modifier
            .alpha(alpha)
    ) {
        content()
    }
}
