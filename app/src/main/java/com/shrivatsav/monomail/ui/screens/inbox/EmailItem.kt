package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.shrivatsav.monomail.data.model.EmailThread
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star

@Composable
fun EmailItem(
    thread: EmailThread,
    onClick: () -> Unit,
    onStarClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUnread = !thread.isRead
    val senderWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Bold
    val subjectWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold
    val senderInitial = thread.from.firstOrNull()?.uppercase() ?: "?"
    val domain = extractDomain(thread.fromEmail)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            // Sender favicon / initial avatar
            SenderAvatar(
                domain = domain,
                senderInitial = senderInitial,
                modifier = Modifier.padding(top = 2.dp)
            )
            
            // Unread indicator dot as a badge on the avatar
            if (isUnread) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Email text content
        Column(modifier = Modifier.weight(1f)) {
            // Sender name + message count + timestamp
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = displayName(thread.from),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = senderWeight,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    // Message count badge for threads with multiple messages
                    if (thread.messageCount > 1) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${thread.messageCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    shape = RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatTimestamp(thread.date),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isUnread) 0.75f else 0.45f
                    )
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Subject
                Text(
                    text = thread.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = subjectWeight,
                    color = MaterialTheme.colorScheme.onSurface.copy(
                        alpha = if (isUnread) 1f else 0.8f
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Star icon
                androidx.compose.material3.IconButton(
                    onClick = onStarClick,
                    modifier = Modifier.size(24.dp)
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = if (thread.isStarred) Icons.Filled.Star else Icons.Outlined.Star,
                        contentDescription = if (thread.isStarred) "Unstar" else "Star",
                        tint = if (thread.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Snippet
            Text(
                text = thread.snippet,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 32.dp)
            )
        }
    }
}

@Composable
private fun SenderAvatar(
    domain: String?,
    senderInitial: String,
    modifier: Modifier = Modifier
) {
    val avatarModifier = modifier
        .size(40.dp)
        .clip(CircleShape)

    val fallback: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = senderInitial,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }

    if (domain != null) {
        SubcomposeAsyncImage(
            model = "https://www.google.com/s2/favicons?domain=$domain&sz=128",
            contentDescription = "Sender avatar",
            modifier = avatarModifier
        ) {
            when (painter.state) {
                is AsyncImagePainter.State.Loading,
                is AsyncImagePainter.State.Error -> fallback()
                else -> SubcomposeAsyncImageContent()
            }
        }
    } else {
        Box(modifier = avatarModifier) { fallback() }
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

/** Extract a friendly display name from "Name <email>" or plain email. */
private fun displayName(from: String): String {
    val nameMatch = Regex("""^"?([^"<]+?)"?\s*<""").find(from)
    return nameMatch?.groupValues?.get(1)?.trim() ?: from.trim()
}

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - epochMillis
    val cal = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val today = Calendar.getInstance()
    return when {
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                cal.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) ->
            SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))
        diff < TimeUnit.DAYS.toMillis(7) ->
            SimpleDateFormat("EEE", Locale.getDefault()).format(Date(epochMillis))
        cal.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
            SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(epochMillis))
        else ->
            SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date(epochMillis))
    }
}

private fun extractDomain(fromEmail: String): String? {
    val parts = fromEmail.split("@")
    return if (parts.size == 2) parts[1].trim() else null
}