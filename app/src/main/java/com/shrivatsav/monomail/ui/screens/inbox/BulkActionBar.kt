package com.shrivatsav.monomail.ui.screens.inbox

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

@Composable
internal fun BulkActionBar(
    selectedCount: Int,
    currentTab: InboxTab,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleStar: () -> Unit,
    onUnarchive: () -> Unit = {},
    onRestore: () -> Unit = {},
    onReportNotSpam: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val actions = when (currentTab) {
        InboxTab.ARCHIVED -> listOf(
            BulkActionDef(Icons.Rounded.Unarchive, "Unarchive", onUnarchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", onToggleStar),
        )
        InboxTab.TRASH -> listOf(
            BulkActionDef(Icons.Rounded.Restore, "Restore", onRestore),
            BulkActionDef(Icons.Rounded.Delete, "Delete", onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", onToggleStar),
        )
        InboxTab.STARRED -> listOf(
            BulkActionDef(Icons.Rounded.Archive, "Archive", onArchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", onMarkUnread),
            BulkActionDef(Icons.Rounded.StarOutline, "Unstar", onToggleStar),
        )
        InboxTab.SPAM -> listOf(
            BulkActionDef(Icons.Rounded.Report, "Not spam", onReportNotSpam),
            BulkActionDef(Icons.Rounded.Delete, "Delete", onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", onToggleStar),
        )
        else -> listOf(
            BulkActionDef(Icons.Rounded.Archive, "Archive", onArchive),
            BulkActionDef(Icons.Rounded.Delete, "Delete", onDelete),
            BulkActionDef(Icons.Rounded.CheckCircle, "Read", onMarkRead),
            BulkActionDef(Icons.Rounded.MarkEmailUnread, "Unread", onMarkUnread),
            BulkActionDef(Icons.Rounded.Star, "Star", onToggleStar),
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
