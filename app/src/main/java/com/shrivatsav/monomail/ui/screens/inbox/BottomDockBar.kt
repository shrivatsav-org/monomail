package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.settings.DockConfig
import com.shrivatsav.monomail.data.settings.DockTabId
import com.shrivatsav.monomail.data.settings.AppSettings

@Composable
internal fun BottomDockBar(
    currentTab: InboxTab,
    dockConfig: DockConfig,
    unifiedInboxEnabled: Boolean,
    appSettings: AppSettings,
    onTabClick: (InboxTab) -> Unit,
) {
    var showRemainingTabs by remember { mutableStateOf(false) }
    val allTabs = DockTabId.values().filter { it != DockTabId.UNIFIED || unifiedInboxEnabled }
    val primaryIds = dockConfig.primaryTabs
    val remainingIds = allTabs.filter { it !in primaryIds }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.wrapContentSize()
    ) {
        AnimatedVisibility(
            visible = showRemainingTabs,
            enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(tween(180)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(tween(120))
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Column(modifier = Modifier.padding(4.dp)) {
                    remainingIds.forEach { dockTabId ->
                        val tab = dockTabId.toInboxTab()
                        RemainingTabItem(
                            icon = dockTabId.icon(unifiedInboxEnabled),
                            label = dockTabId.label(unifiedInboxEnabled),
                            isActive = tab == currentTab,
                            onClick = {
                                onTabClick(tab)
                                showRemainingTabs = false
                            },
                            scale = appSettings.navScale
                        )
                    }
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                shadowElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    primaryIds.forEach { dockTabId ->
                        val tab = dockTabId.toInboxTab()
                        DockTab(
                            isActive = tab == currentTab,
                            icon = dockTabId.icon(unifiedInboxEnabled),
                            label = dockTabId.label(unifiedInboxEnabled),
                            contentDescription = dockTabId.label(unifiedInboxEnabled),
                            onClick = { onTabClick(tab) },
                            scale = appSettings.navScale
                        )
                    }
                }
            }

            if (remainingIds.isNotEmpty() && currentTab != InboxTab.TRASH && currentTab != InboxTab.SPAM) {
                Surface(
                    shape = CircleShape,
                    color = if (showRemainingTabs) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .size((42 * appSettings.navScale).dp)
                        .clip(CircleShape)
                        .clickable { showRemainingTabs = !showRemainingTabs }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (showRemainingTabs) Icons.Rounded.KeyboardArrowUp
                            else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = if (showRemainingTabs) "Collapse" else "More tabs",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size((22 * appSettings.navScale).dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RemainingTabItem(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    scale: Float,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        onClick = onClick,
        modifier = Modifier.height((42 * scale).dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.onSurface
                       else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                modifier = Modifier.size((22 * scale).dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun DockTabId.toInboxTab(): InboxTab = when (this) {
    DockTabId.UNIFIED -> InboxTab.UNIFIED
    DockTabId.INBOX -> InboxTab.INBOX
    DockTabId.SENT -> InboxTab.SENT
    DockTabId.ARCHIVED -> InboxTab.ARCHIVED
    DockTabId.SNOOZED -> InboxTab.SNOOZED
    DockTabId.STARRED -> InboxTab.STARRED
    DockTabId.TRASH -> InboxTab.TRASH
    DockTabId.SPAM -> InboxTab.SPAM
}

internal fun DockTabId.icon(unifiedInboxEnabled: Boolean): ImageVector = when (this) {
    DockTabId.UNIFIED -> Icons.Rounded.Inbox
    DockTabId.INBOX -> if (unifiedInboxEnabled) Icons.Rounded.AccountCircle else Icons.Rounded.Inbox
    DockTabId.SENT -> Icons.AutoMirrored.Rounded.Send
    DockTabId.ARCHIVED -> Icons.Rounded.Archive
    DockTabId.SNOOZED -> Icons.Rounded.Schedule
    DockTabId.STARRED -> Icons.Rounded.Star
    DockTabId.TRASH -> Icons.Rounded.Delete
    DockTabId.SPAM -> Icons.Rounded.Report
}

internal fun DockTabId.label(unifiedInboxEnabled: Boolean): String = when (this) {
    DockTabId.UNIFIED -> "Unified"
    DockTabId.INBOX -> if (unifiedInboxEnabled) "Primary" else "Inbox"
    DockTabId.SENT -> "Sent"
    DockTabId.ARCHIVED -> "Archived"
    DockTabId.SNOOZED -> "Snoozed"
    DockTabId.STARRED -> "Starred"
    DockTabId.TRASH -> "Trash"
    DockTabId.SPAM -> "Spam"
}
