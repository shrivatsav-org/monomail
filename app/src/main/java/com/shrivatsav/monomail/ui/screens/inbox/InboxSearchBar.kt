package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.settings.SwipeAction

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun InboxSearchBar(
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    query: String,
    onQueryChange: (String) -> Unit,
    onServerSearch: (String) -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onMarkAllRead: () -> Unit,
    onStarredClick: () -> Unit,
    onTrashClick: () -> Unit,
    onScheduledClick: () -> Unit = {},
    scheduledCount: Int = 0,
    isRefreshing: Boolean,
    toastState: InboxViewModel.ToastState?,
    onUndo: () -> Unit,
    onSettings: () -> Unit = {},
    onOpenProfile: () -> Unit,
    isBulkMode: Boolean = false,
    selectedCount: Int = 0,
    onSelectAll: () -> Unit = {},
    onDeselectAll: () -> Unit = {},
    onDone: () -> Unit = {},
) {
    val containerColor by animateColorAsState(
        targetValue = if (toastState != null)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(300),
        label = "SearchBarColor"
    )

    val bulkModeContainerColor by animateColorAsState(
        targetValue = if (isBulkMode) MaterialTheme.colorScheme.secondaryContainer
                      else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(300),
        label = "BulkModeColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (isBulkMode) {
            Surface(
                color = bulkModeContainerColor,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCount == 1) "1 selected" else "$selectedCount selected",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    TextButton(
                        onClick = onSelectAll,
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Select all")
                    }
                    Spacer(Modifier.width(4.dp))
                    IconButton(
                        onClick = onDone,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = "Done",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        } else {
        SearchBar(
            inputField = {
                AnimatedContent(
                    targetState = toastState,
                    label = "SearchBarToastMorph",
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) }
                ) { toast ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                    ) {
                        if (toast != null) {
                            val icon = when (toast.actionType) {
                                InboxViewModel.ActionType.ARCHIVE -> Icons.Outlined.Archive
                                InboxViewModel.ActionType.DELETE -> Icons.Outlined.Delete
                                InboxViewModel.ActionType.EMPTY_TRASH -> Icons.Outlined.Delete
                                InboxViewModel.ActionType.SEND -> Icons.AutoMirrored.Outlined.Send
                                InboxViewModel.ActionType.SNOOZE -> Icons.Outlined.Schedule
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxSize(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Spacer(Modifier.width(16.dp))
                                Icon(
                                    icon, null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    toast.message,
                                    modifier = Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                TextButton(
                                    onClick = onUndo,
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.textButtonColors(
                                        contentColor = MaterialTheme.colorScheme.primary
                                    )
                                ) {
                                    Text("Undo")
                                }
                                Spacer(Modifier.width(12.dp))
                            }
                        } else {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = { onServerSearch(query) },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = {
                                    Text(
                                        if (isRefreshing) "Syncing..." else "Search in mail",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                    )
                                },
                                leadingIcon = {
                                    if (isRefreshing) {
                                        LoadingIndicator(
                                            modifier = Modifier
                                                .padding(start = 8.dp)
                                                .size(40.dp),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    } else {
                                        Icon(
                                            Icons.Outlined.Search,
                                            contentDescription = "Search",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(start = 8.dp)
                                        )
                                    }
                                },
                                trailingIcon = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,

                                    ) {
                                        BadgedBox(
                                            badge = {
                                                if (scheduledCount > 0) {
                                                    Badge(
                                                        containerColor = MaterialTheme.colorScheme.error,
                                                        contentColor = MaterialTheme.colorScheme.onError
                                                    ) {
                                                        Text(
                                                            if (scheduledCount > 99) "99+" else scheduledCount.toString(),
                                                            style = MaterialTheme.typography.labelSmall
                                                        )
                                                    }
                                                }
                                            }
                                        ) {
                                            IconButton(
                                                onClick = onScheduledClick,
                                                modifier = Modifier.size(40.dp)
                                            ) {
                                                Icon(
                                                    Icons.Outlined.CalendarMonth,
                                                    contentDescription = "Scheduled",
                                                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(25.dp)
                                                )
                                            }
                                        }
                                        IconButton(
                                            onClick = onMarkAllRead,
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Icon(
                                                Icons.Outlined.CheckCircle,
                                                contentDescription = "Mark all as read",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                                modifier = Modifier.size(25.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(4.dp))
                                        AvatarButton(
                                            userProfile = userProfile,
                                            onClick = onOpenProfile
                                        )

                                    }
                                }
                            )
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
