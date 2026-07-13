package com.shrivatsav.monomail.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

fun Modifier.shimmer(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "shimmer_transition")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_translate"
    )

    val color1 = MaterialTheme.colorScheme.surfaceContainerHigh
    val color2 = MaterialTheme.colorScheme.surfaceContainer
    
    val brush = Brush.linearGradient(
        colors = listOf(color1, color2, color1),
        start = Offset(0f, 0f),
        end = Offset(translateAnim, translateAnim)
    )

    this.then(Modifier.background(brush))
}

@Composable
fun EmailItemSkeleton(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Avatar skeleton
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .shimmer()
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            // Sender line
            Row(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .weight(0.4f)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
                Spacer(modifier = Modifier.weight(0.4f))
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .weight(0.2f)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Subject line
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Snippet line
            Box(
                modifier = Modifier
                    .height(14.dp)
                    .fillMaxWidth(0.95f)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
        }
    }
}

@Composable
fun DetailSkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        // Subject skeleton
        Box(
            modifier = Modifier
                .height(32.dp)
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(8.dp))
                .shimmer()
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sender info skeleton
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .shimmer()
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column {
                Box(
                    modifier = Modifier
                        .height(16.dp)
                        .fillMaxWidth(0.4f)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .height(14.dp)
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmer()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Body skeleton paragraphs
        for (i in 0..3) {
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .height(16.dp)
                    .fillMaxWidth(if (i % 2 == 0) 0.8f else 0.6f)
                    .clip(RoundedCornerShape(4.dp))
                    .shimmer()
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
