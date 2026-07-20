package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*

import com.shrivatsav.monomail.model.InboxTab

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

internal data class BulkActionDef(
    val icon: ImageVector,
    val label: String,
    val onClick: () -> Unit
)

internal data class BulkActionBarCallbacks(
    val onArchive: () -> Unit,
    val onDelete: () -> Unit,
    val onMarkRead: () -> Unit,
    val onMarkUnread: () -> Unit,
    val onToggleStar: () -> Unit,
    val onUnarchive: () -> Unit = {},
    val onRestore: () -> Unit = {},
    val onReportNotSpam: () -> Unit = {},
)

@Composable
internal fun BulkActionBar(
    currentTab: InboxTab,
    callbacks: BulkActionBarCallbacks,
    modifier: Modifier = Modifier
) {
    val actions = when (currentTab) {
        InboxTab.ARCHIVED -> listOf(
            BulkActionDef(Icons.Rounded.Unarchive, "Unarchive", callbacks.onUnarchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", callbacks.onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", callbacks.onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", callbacks.onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", callbacks.onToggleStar),
        )
        InboxTab.TRASH -> listOf(
            BulkActionDef(Icons.Rounded.Restore, "Restore", callbacks.onRestore),
            BulkActionDef(Icons.Rounded.Delete, "Delete", callbacks.onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", callbacks.onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", callbacks.onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", callbacks.onToggleStar),
        )
        InboxTab.STARRED -> listOf(
            BulkActionDef(Icons.Rounded.Archive, "Archive", callbacks.onArchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", callbacks.onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", callbacks.onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", callbacks.onMarkUnread),
            BulkActionDef(Icons.Rounded.StarOutline, "Unstar", callbacks.onToggleStar),
        )
        InboxTab.SPAM -> listOf(
            BulkActionDef(Icons.Rounded.Delete, "Delete", callbacks.onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", callbacks.onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", callbacks.onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", callbacks.onToggleStar),
        )
        else -> listOf(
            BulkActionDef(Icons.Rounded.Archive, "Archive", callbacks.onArchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", callbacks.onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", callbacks.onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", callbacks.onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", callbacks.onToggleStar),
        )
    }

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            actions.forEach { action ->
                BulkAction(
                    icon = action.icon,
                    label = action.label,
                    onClick = action.onClick
                )
            }
        }
    }
}

@Composable
private fun BulkAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            contentDescription = label,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}
