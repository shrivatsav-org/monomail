package com.shrivatsav.monomail.ui.screens.inbox
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.net.Uri
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.shrivatsav.monomail.data.model.EmailThread
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
private val displayNameRegex = Regex("""^"?([^"<]+?)"?\s*<""")
private val domainRegex = Regex("@(.+)$")

@Composable
fun EmailItem(
    thread: EmailThread,
    contactPhotoUri: Uri? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showSnippet: Boolean = true,
    compactMode: Boolean = false,
    fontSizeScale: Float = 1f,
    isSelected: Boolean = false,
    isBulkMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isUnread = !thread.isRead
    val senderWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Medium
    val subjectWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal
    val senderInitial = thread.from.firstOrNull()?.uppercase() ?: "?"
    val domain by remember(thread.fromEmail) {
        derivedStateOf { domainRegex.find(thread.fromEmail)?.groupValues?.get(1)?.trim() }
    }
    val backgroundColor = when {
        isUnread -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        else -> MaterialTheme.colorScheme.background
    }
    val verticalPad = if (compactMode) 7.dp else 11.dp
    val hapticFeedback = LocalHapticFeedback.current
    val avatarClickAction = if (isBulkMode) onSelectToggle else onClick
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .then(
                if (isBulkMode) {
                    Modifier.combinedClickable(
                        onClick = onSelectToggle,
                        onLongClick = onSelectToggle
                    )
                } else {
                    Modifier.combinedClickable(
                        onClick = onClick,
                        onLongClick = onLongClick
                    )
                }
            )
            .padding(horizontal = 20.dp, vertical = verticalPad),
        verticalAlignment = Alignment.Top
    ) {
        Box {
            SenderAvatar(
                domain = domain,
                senderInitial = senderInitial,
                contactPhotoUri = contactPhotoUri,
                isSelected = isSelected,
                isBulkMode = isBulkMode,
                onClick = avatarClickAction,
                onLongClick = {
                    if (!isBulkMode) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAvatarLongClick()
                    }
                },
                modifier = Modifier.padding(top = 2.dp)
            )
            if (isUnread && !isBulkMode) {
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
        Column(modifier = Modifier.weight(1f)) {
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
            Text(
                text = thread.subject,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = subjectWeight,
                color = MaterialTheme.colorScheme.onSurface.copy(
                    alpha = if (isUnread) 1f else 0.8f
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (showSnippet) {
                Text(
                    text = thread.snippet,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = if (compactMode) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
@Composable
private fun SenderAvatar(
    domain: String?,
    senderInitial: String,
    contactPhotoUri: Uri? = null,
    isSelected: Boolean = false,
    isBulkMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val avatarModifier = modifier
        .size(40.dp)
        .clip(CircleShape)
    val context = LocalContext.current
    val imageUrl = when {
        contactPhotoUri != null -> contactPhotoUri.toString()
        domain != null -> "https://www.google.com/s2/favicons?domain=$domain&sz=128"
        else -> null
    }
    val painter = if (imageUrl != null) {
        rememberAsyncImagePainter(
            model = ImageRequest.Builder(context)
                .data(imageUrl)
                .crossfade(true)
                .build(),
            placeholder = null
        )
    } else null
    val imageSuccess = painter?.state is AsyncImagePainter.State.Success
    Box(
        modifier = avatarModifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isBulkMode) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = "Not selected",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxSize().padding(2.dp)
                )
            }
        } else if (imageSuccess) {
            Image(
                painter = painter,
                contentDescription = "Sender avatar",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = senderInitial,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
private fun displayName(from: String): String {
    val nameMatch = displayNameRegex.find(from)
    return nameMatch?.groupValues?.get(1)?.trim() ?: from.trim()
}
private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val dayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val dateFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val fullDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

private fun formatTimestamp(epochMillis: Long): String {
    if (epochMillis == 0L) return ""
    val zdt = Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault())
    val date = zdt.toLocalDate()
    val today = LocalDate.now(ZoneId.systemDefault())
    return when {
        date == today ->
            timeFormatter.format(zdt)
        date.isAfter(today.minusDays(7)) ->
            dayFormatter.format(zdt)
        date.year == today.year ->
            dateFormatter.format(zdt)
        else ->
            fullDateFormatter.format(zdt)
    }
}
private fun extractDomain(fromEmail: String): String? {
    val parts = fromEmail.split("@")
    return if (parts.size == 2) parts[1].trim() else null
}