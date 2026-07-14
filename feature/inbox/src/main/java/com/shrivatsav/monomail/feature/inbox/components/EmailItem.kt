package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.ui.theme.MonoOpacity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
private val displayNameRegex = Regex("""^"?([^"<]+?)"?\s*<""")

data class SelectionState(
    val isSelected: Boolean = false,
    val isBulkMode: Boolean = false,
    val onSelectToggle: () -> Unit = {},
    val onRangeSelect: () -> Unit = {},
    val onAvatarLongClick: () -> Unit = {}
)

@Composable
fun EmailItem(
    thread: EmailThread,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    showSnippet: Boolean = true,
    compactMode: Boolean = false,
    selection: SelectionState = SelectionState(),
    modifier: Modifier = Modifier
) {
    val isUnread = !thread.isRead
    val senderInitial = thread.from.firstOrNull()?.uppercase() ?: "?"
    val backgroundColor = when {
        isUnread -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = MonoOpacity.subtle)
        else -> MaterialTheme.colorScheme.background
    }
    val verticalPad = if (compactMode) 7.dp else 11.dp
    val hapticFeedback = LocalHapticFeedback.current
    val avatarClickAction = if (selection.isBulkMode) selection.onSelectToggle else onClick
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val rowScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "rowScale"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(rowScale)
            .background(backgroundColor)
            .then(
                if (selection.isBulkMode) {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
                        onClick = selection.onRangeSelect,
                        onLongClick = selection.onSelectToggle
                    )
                } else {
                    Modifier.combinedClickable(
                        interactionSource = interactionSource,
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
                senderInitial = senderInitial,
                isSelected = selection.isSelected,
                isBulkMode = selection.isBulkMode,
                onClick = avatarClickAction,
                onLongClick = {
                    if (!selection.isBulkMode) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        selection.onAvatarLongClick()
                    }
                },
                modifier = Modifier.padding(top = 2.dp)
            )
            UnreadDot(isUnread = isUnread, isBulkMode = selection.isBulkMode)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            EmailItemSenderInfo(thread = thread, isUnread = isUnread)
            EmailItemSubject(thread = thread, isUnread = isUnread)
            if (showSnippet) {
                EmailItemSnippet(thread = thread, compactMode = compactMode)
            }
        }
    }
}

@Composable
private fun BoxScope.UnreadDot(isUnread: Boolean, isBulkMode: Boolean) {
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

@Composable
private fun EmailItemSenderInfo(thread: EmailThread, isUnread: Boolean) {
    val senderWeight = if (isUnread) FontWeight.ExtraBold else FontWeight.Medium
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
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
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
                alpha = if (isUnread) 0.85f else MonoOpacity.tertiary
            )
        )
    }
}

@Composable
private fun EmailItemSubject(thread: EmailThread, isUnread: Boolean) {
    Text(
        text = thread.subject,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (isUnread) FontWeight.Bold else FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurface.copy(
            alpha = if (isUnread) 1f else MonoOpacity.secondary
        ),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun EmailItemSnippet(thread: EmailThread, compactMode: Boolean) {
    Text(
        text = thread.snippet,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = MonoOpacity.secondary),
        maxLines = if (compactMode) 1 else 2,
        overflow = TextOverflow.Ellipsis
    )
}
@Composable
private fun SenderAvatar(
    senderInitial: String,
    isSelected: Boolean = false,
    isBulkMode: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val avatarModifier = modifier
        .size(40.dp)
        .clip(CircleShape)
    Box(
        modifier = avatarModifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = !isBulkMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Text(
                text = senderInitial,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        AnimatedVisibility(
            visible = isBulkMode,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(3.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = "Not selected",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    modifier = Modifier.size(32.dp)
                )
            }
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
