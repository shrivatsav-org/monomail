package com.shrivatsav.monomail.feature.inbox.components

import com.shrivatsav.monomail.feature.inbox.*

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import com.shrivatsav.monomail.ui.theme.cornerShape
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
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.core.data.settings.SwipeAction

data class SearchBarActions(
    val onMarkAllRead: () -> Unit,
    val onScheduledClick: () -> Unit = {},
    val scheduledCount: Int = 0,
    val onUndo: () -> Unit,
    val onOpenProfile: () -> Unit,
    val showMarkAllRead: Boolean = true
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
    bulkSelection: BulkSelectionState = BulkSelectionState(),
    unifiedInboxEnabled: Boolean = false,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val containerColor by animateColorAsState(
        targetValue = when {
            bulkSelection.isBulkMode -> MaterialTheme.colorScheme.secondaryContainer
            // toastState removed; handled as Top Sheet in InboxScreen
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
                        // No toast overlay; always show search input
                        SearchInputContent(query, onQueryChange, onServerSearch, actions, SearchDisplayState(isRefreshing, unifiedInboxEnabled, accounts, userProfile), onFocusChange)
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
                    shape = cornerShape(24.dp),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) { Text("Select all") }
            } else {
                TextButton(
                    onClick = bulkSelection.onDeselectAll,
                    shape = cornerShape(24.dp),
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SearchInputContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onServerSearch: (String) -> Unit,
    actions: SearchBarActions,
    display: SearchDisplayState,
    onFocusChange: ((Boolean) -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is FocusInteraction.Focus -> onFocusChange?.invoke(true)
                is FocusInteraction.Unfocus -> onFocusChange?.invoke(false)
            }
        }
    }
    SearchBarDefaults.InputField(
        query = query,
        onQueryChange = onQueryChange,
        onSearch = { onServerSearch(query) },
        expanded = false,
        onExpandedChange = {},
        interactionSource = interactionSource,
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
        if (actions.scheduledCount > 0) {
            BadgedBox(badge = {
                Badge(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) {
                    Text(if (actions.scheduledCount > 99) "99+" else actions.scheduledCount.toString(), style = MaterialTheme.typography.labelSmall)
                }
            }) {
                IconButton(onClick = actions.onScheduledClick, modifier = Modifier.size(40.dp)) {
                    Icon(Icons.Rounded.CalendarMonth, contentDescription = "Scheduled", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(25.dp))
                }
            }
        }
        if (actions.showMarkAllRead) {
            IconButton(onClick = actions.onMarkAllRead, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Rounded.CheckCircle, contentDescription = "Mark all as read", tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f), modifier = Modifier.size(25.dp))
            }
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
