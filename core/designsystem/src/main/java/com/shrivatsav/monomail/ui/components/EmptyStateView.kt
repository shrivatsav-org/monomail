package com.shrivatsav.monomail.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.ui.theme.MonoOpacity

@Composable
fun EmptyStateView(
    illustration: IllustrationType,
    title: String,
    subtitle: String,
    ctaText: String? = null,
    onCtaClick: (() -> Unit)? = null,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        MonoIllustration(type = illustration, size = 160.dp)
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
            textAlign = TextAlign.Center
        )
        
        if (ctaText != null && onCtaClick != null) {
            Spacer(modifier = Modifier.height(32.dp))
            if (isError) {
                Button(onClick = onCtaClick) {
                    Text(text = ctaText)
                }
            } else {
                OutlinedButton(onClick = onCtaClick) {
                    Text(text = ctaText)
                }
            }
        }
    }
}
