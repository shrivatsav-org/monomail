package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.model.EmailThread
import kotlinx.coroutines.launch

data class SwipeCallbacks(
    val onThreadToDeleteChange: (String?) -> Unit,
    val onEmailClick: () -> Unit,
    val onLongClick: () -> Unit,
    val isNested: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableEmailItem(
    modifier: Modifier = Modifier,
    thread: EmailThread,
    tabForSwipe: InboxTab,
    appSettings: com.shrivatsav.monomail.data.settings.AppSettings,
    viewModel: InboxViewModel,
    callbacks: SwipeCallbacks,
    selection: SelectionState = SelectionState()
) {
    var optIsRead by remember(thread.isRead) { mutableStateOf(thread.isRead) }
    var optIsStarred by remember(thread.isStarred) { mutableStateOf(thread.isStarred) }

    val displayThread by remember(thread, optIsRead, optIsStarred) {
        derivedStateOf { thread.copy(isRead = optIsRead, isStarred = optIsStarred) }
    }

    val dismissState = rememberSwipeToDismissBoxState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        val action = resolveSwipeAction(dismissState, appSettings) ?: return@LaunchedEffect
        when (action) {
            com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> {
                if (tabForSwipe == InboxTab.ARCHIVED) viewModel.unarchiveThread(thread.threadId)
                else if (tabForSwipe == InboxTab.SPAM) viewModel.reportNotSpam(thread.threadId)
                else viewModel.archiveThread(thread.threadId)
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> {
                optIsStarred = !optIsStarred
                viewModel.toggleStar(thread.threadId)
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> {
                callbacks.onThreadToDeleteChange(thread.threadId)
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD -> {
                val wasRead = optIsRead
                optIsRead = !wasRead
                if (wasRead) viewModel.markThreadAsUnread(thread.threadId)
                else viewModel.markThreadAsRead(thread.threadId)
            }
        }
        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
    }

    Column(
        modifier = modifier.let { if (callbacks.isNested) it.padding(start = 32.dp) else it }
    ) {
        if (selection.isBulkMode) {
            EmailItem(
                thread = displayThread,
                onClick = callbacks.onEmailClick,
                onLongClick = callbacks.onLongClick,
                showSnippet = appSettings.showSnippet,
                compactMode = appSettings.compactList,
                selection = SelectionState(
                    isSelected = selection.isSelected,
                    isBulkMode = true,
                    onSelectToggle = selection.onSelectToggle,
                    onRangeSelect = selection.onRangeSelect,
                    onAvatarLongClick = selection.onAvatarLongClick
                )
            )
        } else {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = true,
            backgroundContent = {
                SwipeBackground(dismissState, appSettings, tabForSwipe, optIsStarred, optIsRead)
            }
        ) {
            EmailItem(
                thread = displayThread,
                onClick = callbacks.onEmailClick,
                onLongClick = callbacks.onLongClick,
                showSnippet = appSettings.showSnippet,
                compactMode = appSettings.compactList,
                selection = SelectionState(
                    isSelected = selection.isSelected,
                    isBulkMode = false,
                    onSelectToggle = selection.onSelectToggle,
                    onRangeSelect = selection.onRangeSelect,
                    onAvatarLongClick = selection.onAvatarLongClick
                )
            )
        }
        }
        if (appSettings.showDividers) {
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun resolveSwipeAction(
    state: SwipeToDismissBoxState,
    appSettings: com.shrivatsav.monomail.data.settings.AppSettings
): com.shrivatsav.monomail.data.settings.SwipeAction? {
    return when (state.currentValue) {
        SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
        SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    appSettings: com.shrivatsav.monomail.data.settings.AppSettings,
    tabForSwipe: InboxTab,
    optIsStarred: Boolean,
    optIsRead: Boolean
) {
    val getAction = { v: SwipeToDismissBoxValue ->
        when (v) {
            SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
            SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
            else -> null
        }
    }
    val action = getAction(dismissState.targetValue) ?: getAction(dismissState.dismissDirection)
    val color by animateColorAsState(
        swipeActionColor(action),
        animationSpec = tween(200),
        label = "swipeBg"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(color)
            .padding(horizontal = 20.dp),
        contentAlignment = if (
            dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
            dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
        ) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        SwipeActionLabel(action, tabForSwipe, optIsStarred, optIsRead)
    }
}

@Composable
private fun SwipeActionLabel(
    action: com.shrivatsav.monomail.data.settings.SwipeAction?,
    tabForSwipe: InboxTab,
    optIsStarred: Boolean,
    optIsRead: Boolean
) {
    val (icon, label, tint) = swipeActionVisuals(action, tabForSwipe, optIsStarred, optIsRead) ?: return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint)
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            textAlign = if (action == com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD) TextAlign.Center else TextAlign.Unspecified
        )
    }
}

@Composable
private fun swipeActionColor(action: com.shrivatsav.monomail.data.settings.SwipeAction?) =
    when (action) {
        com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer
        com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> MaterialTheme.colorScheme.tertiaryContainer
        com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
        com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD -> MaterialTheme.colorScheme.secondaryContainer
        else -> Color.Transparent
    }

private data class SwipeVisuals(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val tint: Color)

@Composable
private fun swipeActionVisuals(
    action: com.shrivatsav.monomail.data.settings.SwipeAction?,
    tabForSwipe: InboxTab,
    optIsStarred: Boolean,
    optIsRead: Boolean
): SwipeVisuals? = when (action) {
    com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> SwipeVisuals(
        icon = when (tabForSwipe) { InboxTab.ARCHIVED -> Icons.Rounded.Inbox; InboxTab.SPAM -> Icons.Rounded.Restore; else -> Icons.Rounded.Archive },
        label = when (tabForSwipe) { InboxTab.ARCHIVED -> "Inbox"; InboxTab.SPAM -> "Restore"; else -> "Archive" },
        tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
    com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> SwipeVisuals(
        icon = if (optIsStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        label = if (optIsStarred) "Unstar" else "Star",
        tint = MaterialTheme.colorScheme.onTertiaryContainer
    )
    com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> SwipeVisuals(
        icon = Icons.Rounded.Delete,
        label = "Delete",
        tint = MaterialTheme.colorScheme.onErrorContainer
    )
    com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD -> SwipeVisuals(
        icon = if (optIsRead) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread,
        label = if (optIsRead) "Mark\nUnread" else "Mark\nRead",
        tint = MaterialTheme.colorScheme.onSecondaryContainer
    )
    else -> null
}
