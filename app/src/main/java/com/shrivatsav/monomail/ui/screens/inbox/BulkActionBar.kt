package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
internal fun BulkActionBar(
    selectedCount: Int,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onMarkRead: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleStar: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 8.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BulkAction(
                icon = Icons.Outlined.Archive,
                label = "Archive",
                onClick = onArchive
            )
            BulkAction(
                icon = Icons.Outlined.Delete,
                label = "Delete",
                onClick = onDelete
            )
            BulkAction(
                icon = Icons.Outlined.CheckCircle,
                label = "Read",
                onClick = onMarkRead
            )
            BulkAction(
                icon = Icons.Outlined.MarkEmailUnread,
                label = "Unread",
                onClick = onMarkUnread
            )
            BulkAction(
                icon = Icons.Outlined.StarOutline,
                label = "Star",
                onClick = onToggleStar
            )
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
