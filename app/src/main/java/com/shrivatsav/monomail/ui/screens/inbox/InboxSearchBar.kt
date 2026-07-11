package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.compose.foundation.layout.offset
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.settings.SwipeAction

data class SearchBarActions(
    val onMarkAllRead: () -> Unit,
    val onScheduledClick: () -> Unit = {},
    val scheduledCount: Int = 0,
    val onUndo: () -> Unit,
    val onOpenProfile: () -> Unit
)

data class BulkSelectionState(
    val isBulkMode: Boolean = false,
    val selectedCount: Int = 0,
    val totalCount: Int = 0,
    val onSelectAll: () -> Unit = {},
    val onDeselectAll: () -> Unit = {},
    val onDone: () -> Unit = {}
)

data class SearchDisplayState(
    val isRefreshing: Boolean = false,
    val unifiedInboxEnabled: Boolean = false,
    val accounts: List<UserProfile> = emptyList(),
    val userProfile: UserProfile? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InboxSearchBar(
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    query: String,
    onQueryChange: (String) -> Unit,
    onServerSearch: (String) -> Unit,
    actions: SearchBarActions,
    isRefreshing: Boolean,
    toastState: InboxViewModel.ToastState?,
    bulkSelection: BulkSelectionState = BulkSelectionState(),
    unifiedInboxEnabled: Boolean = false,
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            bulkSelection.isBulkMode -> MaterialTheme.colorScheme.secondaryContainer
            toastState != null -> MaterialTheme.colorScheme.secondaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainer
        },
        animationSpec = tween(300),
        label = "SearchBarColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        SearchBar(
            inputField = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    if (bulkSelection.isBulkMode) {
                        BulkSelectionContent(bulkSelection)
                    } else {
                        AnimatedContent(
                            targetState = toastState,
                            label = "SearchBarToastMorph",
                            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
                        ) { toast ->
                            if (toast != null) {
                                ToastContent(toast, actions)
                            } else {
                                SearchInputContent(query, onQueryChange, onServerSearch, actions, SearchDisplayState(isRefreshing, unifiedInboxEnabled, accounts, userProfile))
                            }
                        }
                    }
                }
            },
            expanded = false,
            onExpandedChange = {},
            modifier = Modifier.fillMaxWidth(),
            colors = SearchBarDefaults.colors(containerColor = containerColor),
            shape = MaterialTheme.shapes.extraLarge,
            windowInsets = WindowInsets(0.dp)
        ) {}
    }
}

@Composable
private fun BulkSelectionContent(bulkSelection: BulkSelectionState) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnimatedContent(
            targetState = bulkSelection.selectedCount,
            label = "selectedCount",
            modifier = Modifier.weight(1f),
            transitionSpec = {
                (fadeIn(tween(200)) + scaleIn(tween(200))).togetherWith(
                    fadeOut(tween(150)) + scaleOut(tween(150))
                )
            }
        ) { count ->
            Text(
                text = if (count == 1) "1 selected" else "$count selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (bulkSelection.selectedCount < bulkSelection.totalCount) {
                TextButton(
                    onClick = bulkSelection.onSelectAll,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Select all") }
            } else {
                TextButton(
                    onClick = bulkSelection.onDeselectAll,
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Deselect all") }
            }
            Spacer(Modifier.width(4.dp))
            IconButton(onClick = bulkSelection.onDone, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Done", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ToastContent(toast: InboxViewModel.ToastState, actions: SearchBarActions) {
    val icon = when (toast.actionType) {
        InboxViewModel.ActionType.ARCHIVE -> Icons.Rounded.Archive
        InboxViewModel.ActionType.DELETE -> Icons.Rounded.Delete
        InboxViewModel.ActionType.EMPTY_TRASH -> Icons.Rounded.Delete
        InboxViewModel.ActionType.SEND -> Icons.AutoMirrored.Rounded.Send
        InboxViewModel.ActionType.SNOOZE -> Icons.Rounded.Schedule
        InboxViewModel.ActionType.UNARCHIVE -> Icons.Rounded.Unarchive
        InboxViewModel.ActionType.RESTORE -> Icons.Rounded.Restore
    }
    Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.width(16.dp))
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            toast.message, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        TextButton(
            onClick = actions.onUndo, shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) { Text("Undo") }
        Spacer(Modifier.width(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchInputContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onServerSearch: (String) -> Unit,
    actions: SearchBarActions,
    display: SearchDisplayState
) {
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onServerSearch(query) },
        expanded = false,
        onExpandedChange = {},
        placeholder = {
            Text(
                if (display.isRefreshing) "Syncing..." else if (display.unifiedInboxEnabled) "Search all accounts..." else "Search in mail",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            )
        },
        leadingIcon = {
            if (display.isRefreshing) {
                LoadingIndicator(modifier = Modifier.padding(start = 8.dp).size(40.dp), color = MaterialTheme.colorScheme.onSurface)
            } else {
                Icon(Icons.Rounded.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp))
            }
        },
        trailingIcon = { SearchTrailingIcon(actions, display) }
    )
}

@Composable
private fun SearchTrailingIcon(actions: SearchBarActions, display: SearchDisplayState) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        BadgedBox(badge = {
            if (actions.scheduledCount > 0) {
                Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
                    Text(if (actions.scheduledCount > 99) "99+" else actions.scheduledCount.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }
        }) {
            IconButton(onClick = actions.onScheduledClick, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.CalendarMonth, contentDescription = "Scheduled", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(25.dp))
            }
        }
        IconButton(onClick = actions.onMarkAllRead, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = "Mark all as read", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(25.dp))
        }
        Spacer(Modifier.width(4.dp))
        if (display.unifiedInboxEnabled && display.accounts.size > 1) {
            StackedAccountAvatars(accounts = display.accounts, onClick = actions.onOpenProfile)
        } else {
            AvatarButton(userProfile = display.userProfile, onClick = actions.onOpenProfile)
        }
    }
}

@Composable
private fun StackedAccountAvatars(
    accounts: List<UserProfile>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val maxVisible = 3
    val visible = accounts.take(maxVisible)
    val overflow = accounts.size - maxVisible

    Box(
        modifier = modifier
            .size(30.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        visible.forEachIndexed { index, account ->
            val offsetX = index * 10
            val size = 22
            Box(
                modifier = Modifier
                    .offset(x = offsetX.dp)
                    .size(size.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
            ) {
                if (!account.photoUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = account.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                } else {
                    val initial = account.displayName.firstOrNull()?.uppercase() ?: "?"
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            initial,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.background,
                            fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.7f
                        )
                    }
                }
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .offset(x = (-2).dp, y = 2.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize * 0.6f,
                    color = MaterialTheme.colorScheme.background
                )
            }
        }
    }
}

@Composable
private fun AvatarButton(
    userProfile: UserProfile?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (userProfile != null && !userProfile.photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = userProfile.photoUrl,
            contentDescription = "Profile",
            modifier = modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        )
    } else {
        val initial = userProfile?.displayName?.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                initial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.background
            )
        }
    }
}
