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
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.model.EmailThread
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableEmailItem(
    modifier: Modifier = Modifier,
    thread: EmailThread,
    tabForSwipe: InboxTab,
    appSettings: com.shrivatsav.monomail.data.settings.AppSettings,
    viewModel: InboxViewModel,
    onThreadToDeleteChange: (String?) -> Unit,
    onEmailClick: () -> Unit,
    onLongClick: () -> Unit,
    fontSizeScale: Float,
    isNested: Boolean = false,
    isSelected: Boolean = false,
    isBulkMode: Boolean = false,
    onSelectToggle: () -> Unit = {},
    onRangeSelect: () -> Unit = {},
    onAvatarLongClick: () -> Unit = {}
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
        val action = when (dismissState.currentValue) {
            SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
            SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
            else -> return@LaunchedEffect
        }
        when (action) {
            com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> {
                if (tabForSwipe == InboxTab.ARCHIVED) viewModel.unarchiveThread(thread.threadId)
                else if (tabForSwipe == InboxTab.SPAM) viewModel.reportNotSpam(thread.threadId)
                else viewModel.archiveThread(thread.threadId)
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> {
                optIsStarred = !optIsStarred
                viewModel.toggleStar(thread.threadId)
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> {
                onThreadToDeleteChange(thread.threadId)
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            }
            com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD -> {
                val wasRead = optIsRead
                optIsRead = !wasRead
                if (wasRead) viewModel.markThreadAsUnread(thread.threadId)
                else viewModel.markThreadAsRead(thread.threadId)
                scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
            }
        }
    }

    Column(
        modifier = modifier.let { if (isNested) it.padding(start = 32.dp) else it }
    ) {
        if (isBulkMode) {
            EmailItem(
                thread = displayThread,
                onClick = onEmailClick,
                onLongClick = onLongClick,
                showSnippet = appSettings.showSnippet,
                compactMode = appSettings.compactList,
                fontSizeScale = fontSizeScale,
                isSelected = isSelected,
                isBulkMode = true,
                onSelectToggle = onSelectToggle,
                onRangeSelect = onRangeSelect,
                onAvatarLongClick = onAvatarLongClick
            )
        } else {
        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromEndToStart = true,
            enableDismissFromStartToEnd = true,
            backgroundContent = {
                val getAction = { v: SwipeToDismissBoxValue ->
                    when (v) {
                        SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
                        SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
                        else -> null
                    }
                }
                val action =
                    getAction(dismissState.targetValue) ?: getAction(dismissState.dismissDirection)
                val color by animateColorAsState(
                    when (action) {
                        com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer
                        com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> MaterialTheme.colorScheme.tertiaryContainer
                        com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
                        com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD -> MaterialTheme.colorScheme.secondaryContainer
                        else -> Color.Transparent
                    },
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
                    when (action) {
                        com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE ->
                            Icon(
                                if (tabForSwipe == InboxTab.ARCHIVED) Icons.Rounded.Inbox else if (tabForSwipe == InboxTab.SPAM) Icons.Rounded.Restore else Icons.Rounded.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.STAR ->
                            Icon(
                                if (optIsStarred) Icons.Rounded.Star else Icons.Rounded.Star,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.DELETE ->
                            Icon(
                                Icons.Rounded.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD ->
                            Icon(
                                if (optIsRead) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        else -> {}
                    }
                }
            }
        ) {
            EmailItem(
                thread = displayThread,
                onClick = onEmailClick,
                onLongClick = onLongClick,
                showSnippet = appSettings.showSnippet,
                compactMode = appSettings.compactList,
                fontSizeScale = fontSizeScale,
                isSelected = isSelected,
                isBulkMode = false,
                onSelectToggle = onSelectToggle,
                onRangeSelect = onRangeSelect,
                onAvatarLongClick = onAvatarLongClick
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
