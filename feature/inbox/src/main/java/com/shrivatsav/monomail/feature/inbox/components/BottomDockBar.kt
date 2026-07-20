package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*

import com.shrivatsav.monomail.model.InboxTab

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.core.data.settings.DockConfig
import com.shrivatsav.monomail.core.data.settings.DockTabId
import com.shrivatsav.monomail.core.data.settings.AppSettings
import com.shrivatsav.monomail.ui.theme.MonoTween

@Composable
internal fun BottomDockBar(
    currentTab: InboxTab,
    dockConfig: DockConfig,
    unifiedInboxEnabled: Boolean,
    appSettings: AppSettings,
    onTabClick: (InboxTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showRemainingTabs by remember { mutableStateOf(false) }
    val allTabs = DockTabId.entries.filter { it != DockTabId.UNIFIED }
    val primaryIds = dockConfig.primaryTabs
    val remainingIds = allTabs.filter { it !in primaryIds }
    val filteredRemainingIds = remainingIds.filter { it.toInboxTab() != currentTab }
    var dockRowHeightPx by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    Box(
        modifier = modifier.wrapContentSize()
    ) {
        AnimatedVisibility(
            visible = showRemainingTabs && filteredRemainingIds.isNotEmpty(),
            enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(MonoTween.fadeIn),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut(MonoTween.fadeOut),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(bottom = androidx.compose.ui.unit.max(0.dp, (dockRowHeightPx / density.density).dp - 12.dp))
        ) {
            Surface(
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
                shape = cornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 8.dp,
            ) {
                Row(modifier = Modifier.padding(4.dp).horizontalScroll(rememberScrollState())) {
                    filteredRemainingIds.forEach { dockTabId ->
                        val tab = dockTabId.toInboxTab()
                        RemainingTabItem(
                            icon = dockTabId.icon(unifiedInboxEnabled),
                            label = dockTabId.label(unifiedInboxEnabled),
                            isActive = tab == currentTab,
                            onClick = {
                                onTabClick(tab)
                                showRemainingTabs = false
                            },
                            scale = appSettings.navScale,
                            modifier = Modifier.width((76 * appSettings.navScale).dp)
                        )
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(end = 72.dp)
                .onGloballyPositioned { coordinates ->
                    dockRowHeightPx = coordinates.size.height.toFloat()
                }
                .horizontalScroll(rememberScrollState())
                .padding(vertical = 12.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PrimaryDockRow(
                currentTab = currentTab,
                primaryIds = primaryIds,
                unifiedInboxEnabled = unifiedInboxEnabled,
                appSettings = appSettings,
                onTabClick = { tab ->
                    onTabClick(tab)
                    showRemainingTabs = false
                },
            )
            if (filteredRemainingIds.isNotEmpty()) {
                MoreTabsButton(
                    showRemainingTabs = showRemainingTabs,
                    navScale = appSettings.navScale,
                    onClick = { showRemainingTabs = !showRemainingTabs }
                )
            }
        }
    }
}


@Composable
private fun PrimaryDockRow(
    currentTab: InboxTab,
    primaryIds: List<DockTabId>,
    unifiedInboxEnabled: Boolean,
    appSettings: AppSettings,
    onTabClick: (InboxTab) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = cornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier.padding(6.dp),
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
    }
}

@Composable
private fun MoreTabsButton(
    showRemainingTabs: Boolean,
    navScale: Float,
    onClick: () -> Unit,
) {
    Surface(
        shape = cornerShape(24.dp),
        color = if (showRemainingTabs) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
        onClick = onClick,
        modifier = Modifier
            .size((42 * navScale).dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (showRemainingTabs) Icons.Rounded.KeyboardArrowUp
                else Icons.Rounded.KeyboardArrowDown,
                contentDescription = if (showRemainingTabs) "Collapse" else "More tabs",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size((22 * navScale).dp)
            )
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
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = cornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        onClick = onClick,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                style = MaterialTheme.typography.labelSmall,
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
    DockTabId.INBOX -> if (unifiedInboxEnabled) Icons.Rounded.Home else Icons.Rounded.Inbox
    DockTabId.SENT -> Icons.AutoMirrored.Rounded.Send
    DockTabId.ARCHIVED -> Icons.Rounded.Archive
    DockTabId.SNOOZED -> Icons.Rounded.Schedule
    DockTabId.STARRED -> Icons.Rounded.Star
    DockTabId.TRASH -> Icons.Rounded.Delete
    DockTabId.SPAM -> Icons.Rounded.Report
}

internal fun DockTabId.label(unifiedInboxEnabled: Boolean): String = when (this) {
    DockTabId.UNIFIED -> "Unified"
    DockTabId.INBOX -> if (unifiedInboxEnabled) "All Mail" else "Inbox"
    DockTabId.SENT -> "Sent"
    DockTabId.ARCHIVED -> "Archived"
    DockTabId.SNOOZED -> "Snoozed"
    DockTabId.STARRED -> "Starred"
    DockTabId.TRASH -> "Trash"
    DockTabId.SPAM -> "Spam"
}
