package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*

import com.shrivatsav.monomail.model.InboxTab

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.ui.theme.cornerShape
import kotlinx.coroutines.launch

data class SwipeCallbacks(
    val onThreadToDeleteChange: (String?) -> Unit,
    val onEmailClick: () -> Unit,
    val onLongClick: () -> Unit,
    val isNested: Boolean = false
)

enum class GroupPosition { SOLO, TOP, MIDDLE, BOTTOM }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SwipeableEmailItem(
    modifier: Modifier = Modifier,
    thread: EmailThread,
    tabForSwipe: InboxTab,
    appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings,
    viewModel: InboxViewModel,
    callbacks: SwipeCallbacks,
    selection: SelectionState = SelectionState(),
    unreadPosition: UnreadPosition = UnreadPosition.SOLO,
    groupPosition: GroupPosition = GroupPosition.SOLO
) {
    var optIsRead by remember(thread.isRead) { mutableStateOf(thread.isRead) }
    var optIsStarred by remember(thread.isStarred) { mutableStateOf(thread.isStarred) }

    val displayThread by remember(thread, optIsRead, optIsStarred) {
        derivedStateOf { thread.copy(isRead = optIsRead, isStarred = optIsStarred) }
    }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * appSettings.swipeThreshold }
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        val action = resolveSwipeAction(dismissState, appSettings) ?: return@LaunchedEffect
        when (action) {
            com.shrivatsav.monomail.core.data.settings.SwipeAction.ARCHIVE -> {
                if (tabForSwipe == InboxTab.ARCHIVED) viewModel.unarchiveThread(thread.threadId)
                else if (tabForSwipe == InboxTab.SPAM) viewModel.deleteThread(thread.threadId)
                else viewModel.archiveThread(thread.threadId)
            }
            com.shrivatsav.monomail.core.data.settings.SwipeAction.STAR -> {
                optIsStarred = !optIsStarred
                viewModel.toggleStar(thread.threadId)
            }
            com.shrivatsav.monomail.core.data.settings.SwipeAction.DELETE -> {
                callbacks.onThreadToDeleteChange(thread.threadId)
            }
            com.shrivatsav.monomail.core.data.settings.SwipeAction.READ_UNREAD -> {
                val wasRead = optIsRead
                optIsRead = !wasRead
                if (wasRead) viewModel.markThreadAsUnread(thread.threadId)
                else viewModel.markThreadAsRead(thread.threadId)
            }
        }
        scope.launch { dismissState.snapTo(SwipeToDismissBoxValue.Settled) }
    }
    val shape = cardShape(groupPosition)
    val topPad = if (groupPosition == GroupPosition.SOLO || groupPosition == GroupPosition.TOP) 2.dp else 0.dp
    val bottomPad = if (groupPosition == GroupPosition.SOLO || groupPosition == GroupPosition.BOTTOM) 4.dp else 0.dp

    Column(
        modifier = modifier
            .padding(horizontal = 12.dp)
            .padding(top = topPad, bottom = bottomPad)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
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
                ),
                unreadPosition = unreadPosition
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
                    ),
                    unreadPosition = unreadPosition
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
private fun resolveSwipeAction(
    state: SwipeToDismissBoxState,
    appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings
): com.shrivatsav.monomail.core.data.settings.SwipeAction? {
    return when (state.currentValue) {
        SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
        SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
        else -> null
    }
}
@Composable
private fun cardShape(position: GroupPosition): RoundedCornerShape {
    val r = cornerShape(16.dp)
    return when (position) {
        GroupPosition.SOLO   -> r
        GroupPosition.TOP    -> RoundedCornerShape(r.topStart, r.topEnd, CornerSize(0.dp), CornerSize(0.dp))
        GroupPosition.MIDDLE -> RoundedCornerShape(CornerSize(0.dp), CornerSize(0.dp), CornerSize(0.dp), CornerSize(0.dp))
        GroupPosition.BOTTOM -> RoundedCornerShape(CornerSize(0.dp), CornerSize(0.dp), r.bottomEnd, r.bottomStart)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeBackground(
    dismissState: SwipeToDismissBoxState,
    appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings,
    tabForSwipe: InboxTab,
    optIsStarred: Boolean,
    optIsRead: Boolean
) {
    val isStartToEnd = dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd ||
            (dismissState.targetValue == SwipeToDismissBoxValue.Settled &&
                    dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd)
    val action = when {
        dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
        dismissState.targetValue == SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
        dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
        dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
        else -> null
    }
    val visuals = swipeActionVisuals(action, tabForSwipe, optIsStarred, optIsRead)
    val progress = dismissState.progress

    val bubbleWidth = if (visuals != null) (56f + 80f * progress).dp else 0.dp
    val bubbleHeight = if (visuals != null) 56.dp else 0.dp

    val bubbleColor by animateColorAsState(
        targetValue = when (action) {
            com.shrivatsav.monomail.core.data.settings.SwipeAction.ARCHIVE ->
                MaterialTheme.colorScheme.primaryContainer
            com.shrivatsav.monomail.core.data.settings.SwipeAction.STAR ->
                MaterialTheme.colorScheme.tertiaryContainer
            com.shrivatsav.monomail.core.data.settings.SwipeAction.DELETE ->
                MaterialTheme.colorScheme.errorContainer
            com.shrivatsav.monomail.core.data.settings.SwipeAction.READ_UNREAD ->
                MaterialTheme.colorScheme.secondaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(150),
        label = "bubbleColor"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 16.dp),
        contentAlignment = if (isStartToEnd) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        if (visuals != null) {
            Box(
                modifier = Modifier
                    .width(bubbleWidth)
                    .height(bubbleHeight)
                    .clip(cornerShape(18.dp))
                    .background(bubbleColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = visuals.icon,
                    contentDescription = visuals.label,
                    tint = visuals.tint,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

private data class SwipeVisuals(val icon: androidx.compose.ui.graphics.vector.ImageVector, val label: String, val tint: Color)

@Composable
private fun swipeActionVisuals(
    action: com.shrivatsav.monomail.core.data.settings.SwipeAction?,
    tabForSwipe: InboxTab,
    optIsStarred: Boolean,
    optIsRead: Boolean
): SwipeVisuals? = when (action) {
    com.shrivatsav.monomail.core.data.settings.SwipeAction.ARCHIVE -> SwipeVisuals(
        icon = when (tabForSwipe) { InboxTab.ARCHIVED -> Icons.Rounded.Inbox; InboxTab.SPAM -> Icons.Rounded.Restore; else -> Icons.Rounded.Archive },
        label = when (tabForSwipe) { InboxTab.ARCHIVED -> "Inbox"; InboxTab.SPAM -> "Restore"; else -> "Archive" },
        tint = MaterialTheme.colorScheme.onPrimaryContainer
    )
    com.shrivatsav.monomail.core.data.settings.SwipeAction.STAR -> SwipeVisuals(
        icon = if (optIsStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder,
        label = if (optIsStarred) "Unstar" else "Star",
        tint = MaterialTheme.colorScheme.onTertiaryContainer
    )
    com.shrivatsav.monomail.core.data.settings.SwipeAction.DELETE -> SwipeVisuals(
        icon = Icons.Rounded.Delete,
        label = "Delete",
        tint = MaterialTheme.colorScheme.onErrorContainer
    )
    com.shrivatsav.monomail.core.data.settings.SwipeAction.READ_UNREAD -> SwipeVisuals(
        icon = if (optIsRead) Icons.Rounded.MarkEmailRead else Icons.Rounded.MarkEmailUnread,
        label = if (optIsRead) "Mark Unread" else "Mark Read",
        tint = MaterialTheme.colorScheme.onSecondaryContainer
    )
    else -> null
}
