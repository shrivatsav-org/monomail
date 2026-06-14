package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.draw.blur
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.Restore
import coil.compose.AsyncImage
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import java.util.Calendar
import kotlin.math.min

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    userProfile: UserProfile?,
    onEmailClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onCompose: () -> Unit = {},
    onSettings: () -> Unit = {},
    onAddAccount: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val unifiedInboxEnabled by viewModel.unifiedInboxEnabled.collectAsState()

    // Collect appearance settings
    val context = androidx.compose.ui.platform.LocalContext.current
    var threadToDelete by remember { mutableStateOf<String?>(null) }
    val app = context.applicationContext as com.shrivatsav.monomail.MonoMailApp
    val appSettings by app.settingsDataStore.settingsFlow.collectAsState(
        initial = com.shrivatsav.monomail.data.settings.AppSettings()
    )
    val fontSizeScale = when (appSettings.fontScale) {
        com.shrivatsav.monomail.data.settings.FontScale.EXTRA_SMALL -> 0.7f
        com.shrivatsav.monomail.data.settings.FontScale.SMALL -> 0.85f
        com.shrivatsav.monomail.data.settings.FontScale.DEFAULT -> 1f
        com.shrivatsav.monomail.data.settings.FontScale.LARGE -> 1.15f
        com.shrivatsav.monomail.data.settings.FontScale.EXTRA_LARGE -> 1.3f
    }
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    var searchQuery by remember { mutableStateOf("") }
    val currentThreads = (state as? InboxState.Success)?.threads ?: emptyList()
    var longPressedThread by remember { mutableStateOf<EmailThread?>(null) }
    val hapticFeedback = LocalHapticFeedback.current

    val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
    LaunchedEffect(currentTab) {
        listState.scrollToItem(0)
    }

    // Local search filtering
    val localFilteredThreads by remember(searchQuery, currentThreads) {
        derivedStateOf {
            if (searchQuery.isEmpty()) null
            else currentThreads.filter {
                it.subject.contains(searchQuery, ignoreCase = true) ||
                        it.from.contains(searchQuery, ignoreCase = true) ||
                        it.snippet.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    // Pagination trigger — only fire when truly near the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 5
        }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { shouldLoadMore }
            .collect { load -> if (load) viewModel.loadMore() }
    }

    // FAB expand/collapse on scroll
    var isFabExpanded by remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                isFabExpanded = when {
                    index == 0 && offset == 0 -> true
                    previousIndex != index -> previousIndex > index
                    else -> previousOffset > offset
                }
                previousIndex = index
                previousOffset = offset
            }
    }

    val isRefreshing = (state as? InboxState.Success)?.isRefreshing == true

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Main content wrapper with blur when context menu is open
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (longPressedThread != null) Modifier.blur(12.dp)
                        else Modifier
                    )
            ) {
            // Main content — search bar + list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                val toastState by viewModel.toastState.collectAsState()
                val accounts by viewModel.accounts.collectAsState()
                
            InboxSearchBar(
                userProfile = userProfile,
                accounts = accounts,
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onServerSearch = { viewModel.searchServer(it) },
                onSignOut = onSignOut,
                onSwitchAccount = { accountId -> viewModel.switchAccount(accountId) },
                onAddAccount = {
                    onAddAccount()
                },
                onMarkAllRead = { viewModel.markAllAsRead() },
                onStarredClick = { viewModel.switchTab(com.shrivatsav.monomail.ui.screens.inbox.InboxTab.STARRED) },
                onTrashClick = { viewModel.switchTab(com.shrivatsav.monomail.ui.screens.inbox.InboxTab.TRASH) },
                isRefreshing = isRefreshing,
                toastState = toastState,
                onUndo = { viewModel.undoAction() },
                onSettings = onSettings
            )

                when (val s = state) {
                    is InboxState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator(
                                modifier = Modifier.size(48.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    is InboxState.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(32.dp)
                            ) {
                                Text(
                                    text = s.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                                Button(
                                    onClick = { viewModel.refresh() },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onSurface,
                                        contentColor = MaterialTheme.colorScheme.background
                                    ),
                                    shape = MaterialTheme.shapes.large
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    is InboxState.Success -> {
                        val threadsToDisplay = localFilteredThreads ?: s.threads
                        val isSearchActive = localFilteredThreads != null
                        val grouped = remember(threadsToDisplay) { groupThreadsByDate(threadsToDisplay) }

                        PullToRefreshBox(
                            isRefreshing = s.isRefreshing,
                            onRefresh = { viewModel.refresh() },
                            state = pullToRefreshState,
                            modifier = Modifier.fillMaxSize(),
                            indicator = {}
                        ) {
                            if (threadsToDisplay.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = if (isSearchActive) "No results for \"$searchQuery\"" else "No emails",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                        )
                                        if (isSearchActive) {
                                            OutlinedButton(
                                                onClick = { viewModel.searchServer(searchQuery) },
                                                shape = MaterialTheme.shapes.large
                                            ) {
                                                Text("Search server")
                                            }
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(bottom = 100.dp)
                                ) {
                                    grouped.forEach { (dateHeader, threadsForDate) ->
                                        item(key = "header_$dateHeader") {
                                            Text(
                                                text = dateHeader,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                modifier = Modifier
                                                    .animateItem(fadeInSpec = null, fadeOutSpec = null)
                                                    .fillMaxWidth()
                                                    .background(MaterialTheme.colorScheme.background)
                                                    .padding(horizontal = 20.dp, vertical = 10.dp)
                                            )
                                        }

                                        items(
                                            items = threadsForDate,
                                            key = { it.threadId }
                                        ) { thread ->
                                            val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
                                            val dismissState = rememberSwipeToDismissBoxState(
                                                confirmValueChange = { value ->
                                                    val executeAction = { action: com.shrivatsav.monomail.data.settings.SwipeAction ->
                                                        when (action) {
                                                            com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> {
                                                                if (currentTab == InboxTab.ARCHIVED) {
                                                                    viewModel.unarchiveThread(thread.threadId)
                                                                } else {
                                                                    viewModel.archiveThread(thread.threadId)
                                                                }
                                                                true
                                                            }
                                                            com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> {
                                                                viewModel.toggleStar(thread.threadId)
                                                                false
                                                            }
                                                            com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> {
                                                                threadToDelete = thread.threadId
                                                                false
                                                            }
                                                        }
                                                    }
                                                    when (value) {
                                                        SwipeToDismissBoxValue.StartToEnd -> executeAction(appSettings.swipeRightAction)
                                                        SwipeToDismissBoxValue.EndToStart -> executeAction(appSettings.swipeLeftAction)
                                                        else -> false
                                                    }
                                                }
                                            )
                                            
                                            Column(modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null)) {
                                                SwipeToDismissBox(
                                                    state = dismissState,
                                                    enableDismissFromEndToStart = true,
                                                    enableDismissFromStartToEnd = true,
                                                    backgroundContent = {
                                                        val getAction = { value: SwipeToDismissBoxValue ->
                                                            when (value) {
                                                                SwipeToDismissBoxValue.StartToEnd -> appSettings.swipeRightAction
                                                                SwipeToDismissBoxValue.EndToStart -> appSettings.swipeLeftAction
                                                                else -> null
                                                            }
                                                        }
                                                        val action = getAction(dismissState.targetValue) ?: getAction(dismissState.dismissDirection)
                                                        
                                                        val color by animateColorAsState(
                                                            when (action) {
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> MaterialTheme.colorScheme.primaryContainer
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> MaterialTheme.colorScheme.tertiaryContainer
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> MaterialTheme.colorScheme.errorContainer
                                                                else -> Color.Transparent
                                                            }
                                                        )
                                                        Box(
                                                            modifier = Modifier
                                                                .fillMaxSize()
                                                                .background(color)
                                                                .padding(horizontal = 20.dp),
                                                            contentAlignment = if (dismissState.targetValue == SwipeToDismissBoxValue.StartToEnd || dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
                                                        ) {
                                                            when (action) {
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.ARCHIVE -> {
                                                                    Icon(
                                                                        imageVector = if (currentTab == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                                                        contentDescription = if (currentTab == InboxTab.ARCHIVED) "Unarchive" else "Archive",
                                                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                                    )
                                                                }
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.STAR -> {
                                                                    Icon(
                                                                        imageVector = if (thread.isStarred) Icons.Outlined.StarOutline else Icons.Filled.Star,
                                                                        contentDescription = if (thread.isStarred) "Unstar" else "Star",
                                                                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                                                                    )
                                                                }
                                                                com.shrivatsav.monomail.data.settings.SwipeAction.DELETE -> {
                                                                    Icon(
                                                                        imageVector = Icons.Outlined.Delete,
                                                                        contentDescription = "Delete",
                                                                        tint = MaterialTheme.colorScheme.onErrorContainer
                                                                    )
                                                                }
                                                                else -> {}
                                                            }
                                                        }
                                                    }
                                                ) {
                                                    EmailItem(
                                                        thread = thread,
                                                        onClick = { onEmailClick(thread.threadId) },
                                                        onLongClick = {
                                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            longPressedThread = thread
                                                        },
                                                        showSnippet = appSettings.showSnippet,
                                                        compactMode = appSettings.compactList,
                                                        fontSizeScale = fontSizeScale
                                                    )
                                                }
                                                if (appSettings.showDividers) {
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.outlineVariant,
                                                        thickness = 1.dp,
                                                        modifier = Modifier.padding(horizontal = 20.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // "Search server" CTA at bottom when search is active
                                    if (isSearchActive && s.threads.isNotEmpty()) {
                                        item(key = "search_server_cta") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 24.dp, vertical = 28.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                OutlinedButton(
                                                    onClick = { viewModel.searchServer(searchQuery) },
                                                    shape = MaterialTheme.shapes.large
                                                ) {
                                                    Text("Search server for older emails")
                                                }
                                            }
                                        }
                                    }
                                    
                                    if (s.isLoadingMore) {
                                        item(key = "loading_more") {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(16.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.material3.CircularProgressIndicator(
                                                    modifier = Modifier.size(24.dp),
                                                    color = MaterialTheme.colorScheme.primary,
                                                    strokeWidth = 2.dp
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } // End blur Box

            // Custom Floating Docked Toolbar
            if (longPressedThread == null) {
                val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                // Pill-shaped navigation bar
                androidx.compose.material3.Surface(
                    modifier = Modifier.height(64.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (unifiedInboxEnabled) {
                            IconButton(onClick = { viewModel.switchTab(InboxTab.UNIFIED) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Inbox, 
                                    contentDescription = "Unified Inbox",
                                    tint = if (currentTab == InboxTab.UNIFIED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.switchTab(InboxTab.INBOX) }) {
                            Icon(
                                imageVector = if (unifiedInboxEnabled) Icons.Outlined.AccountCircle else Icons.Outlined.Inbox, 
                                contentDescription = "Primary Inbox",
                                tint = if (currentTab == InboxTab.INBOX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { viewModel.switchTab(InboxTab.SENT) }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Send, 
                                contentDescription = "Sent",
                                tint = if (currentTab == InboxTab.SENT) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                        IconButton(onClick = { viewModel.switchTab(InboxTab.ARCHIVED) }) {
                            Icon(
                                imageVector = Icons.Outlined.Archive, 
                                contentDescription = "Archived",
                                tint = if (currentTab == InboxTab.ARCHIVED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }

                // Distinct Compose FAB
                FloatingActionButton(
                    onClick = onCompose,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = androidx.compose.foundation.shape.CircleShape,
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Compose")
                }
            }
        }

        // ── Long-press context menu overlay ──────────────────────────
        var displayedThreadForMenu by remember { mutableStateOf<EmailThread?>(null) }
        LaunchedEffect(longPressedThread) {
            if (longPressedThread != null) displayedThreadForMenu = longPressedThread
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = longPressedThread != null,
            enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)),
            exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200))
        ) {
            val thread = displayedThreadForMenu ?: return@AnimatedVisibility
            // Dismiss scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.7f))
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { longPressedThread = null }
            )

            // Context menu card
            Box(
                modifier = Modifier
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .animateEnterExit(
                            enter = androidx.compose.animation.scaleIn(
                                initialScale = 0.9f, 
                                animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                            ),
                            exit = androidx.compose.animation.scaleOut(
                                targetScale = 0.9f, 
                                animationSpec = androidx.compose.animation.core.tween(200)
                            )
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // The selected email preview
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        EmailItem(
                            thread = thread,
                            onClick = { 
                                longPressedThread = null
                                onEmailClick(thread.threadId) 
                            },
                            onLongClick = {},
                            modifier = Modifier
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Action row
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 8.dp,
                        shadowElevation = 8.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Star
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.toggleStar(thread.threadId)
                                        longPressedThread = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (thread.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    contentDescription = if (thread.isStarred) "Unstar" else "Star",
                                    tint = if (thread.isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (thread.isStarred) "Unstar" else "Star",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Archive / Unarchive
                            val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (currentTab == InboxTab.ARCHIVED) {
                                            viewModel.unarchiveThread(thread.threadId)
                                        } else {
                                            viewModel.archiveThread(thread.threadId)
                                        }
                                        longPressedThread = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentTab == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                    contentDescription = "Archive",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (currentTab == InboxTab.ARCHIVED) "Unarchive" else "Archive",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Mark read / unread
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (thread.isRead) {
                                            viewModel.markThreadAsUnread(thread.threadId)
                                        } else {
                                            viewModel.markThreadAsRead(thread.threadId)
                                        }
                                        longPressedThread = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (thread.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.CheckCircle,
                                    contentDescription = if (thread.isRead) "Mark unread" else "Mark read",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (thread.isRead) "Unread" else "Read",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            // Delete / Restore
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        if (currentTab == InboxTab.TRASH) {
                                            viewModel.restoreThread(thread.threadId)
                                        } else {
                                            threadToDelete = thread.threadId
                                        }
                                        longPressedThread = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) {
                                Icon(
                                    imageVector = if (currentTab == InboxTab.TRASH) Icons.Outlined.Restore else Icons.Outlined.Delete,
                                    contentDescription = if (currentTab == InboxTab.TRASH) "Restore" else "Delete",
                                    tint = if (currentTab == InboxTab.TRASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (currentTab == InboxTab.TRASH) "Restore" else "Delete",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (currentTab == InboxTab.TRASH) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (threadToDelete != null) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { threadToDelete = null },
            title = { androidx.compose.material3.Text("Move to Trash?") },
            text = { androidx.compose.material3.Text("Are you sure you want to move this conversation to the trash?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    viewModel.deleteThread(threadToDelete!!)
                    threadToDelete = null
                }) {
                    androidx.compose.material3.Text("Move to Trash")
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { threadToDelete = null }) {
                    androidx.compose.material3.Text("Cancel")
                }
            }
        )
    }
}
}

// ── Search bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InboxSearchBar(
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
    isRefreshing: Boolean,
    toastState: InboxViewModel.ToastState?,
    onUndo: () -> Unit,
    onSettings: () -> Unit = {}
) {
    var showProfileModal by remember { mutableStateOf(false) }
    var showSwitchAccountDialog by remember { mutableStateOf(false) }

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (toastState != null) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = androidx.compose.animation.core.tween(300),
        label = "SearchBarColor"
    )

    SearchBar(
        inputField = {
            androidx.compose.animation.AnimatedContent(
                targetState = toastState,
                label = "SearchBarToastMorph",
                transitionSpec = {
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                }
            ) { toast ->
                if (toast != null) {
                    val icon = when (toast.actionType) {
                        InboxViewModel.ActionType.ARCHIVE -> Icons.Outlined.Archive
                        InboxViewModel.ActionType.DELETE -> Icons.Outlined.Delete
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = toast.message,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                        androidx.compose.material3.TextButton(
                            onClick = onUndo,
                            colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                "Undo",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
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
                                text = if (isRefreshing) "Syncing..." else "Search in mail",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            )
                        },
                        leadingIcon = {
                            if (isRefreshing) {
                                LoadingIndicator(
                                    modifier = Modifier.padding(start = 16.dp).size(40.dp),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = "Search",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(start = 16.dp)
                                )
                            }
                        },
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 12.dp)
                            ) {
                                IconButton(
                                    onClick = onMarkAllRead,
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.CheckCircle,
                                        contentDescription = "Mark all as read",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier.size(25.dp)
                                    )
                                }
        
                                Spacer(modifier = Modifier.width(4.dp))
        
                                AvatarButton(
                                    userProfile = userProfile,
                                    modifier = Modifier.pointerInput(accounts, userProfile) {
                                        detectVerticalDragGestures { change, dragAmount ->
                                            change.consume()
                                            if (Math.abs(dragAmount) > 10f && accounts.size > 1 && userProfile != null) {
                                                val currentIndex = accounts.indexOfFirst { it.id == userProfile.id }
                                                if (currentIndex != -1) {
                                                    val newIndex = if (dragAmount > 0) {
                                                        (currentIndex + 1) % accounts.size
                                                    } else {
                                                        if (currentIndex - 1 < 0) accounts.size - 1 else currentIndex - 1
                                                    }
                                                    onSwitchAccount(accounts[newIndex].id)
                                                }
                                            }
                                        }
                                    },
                                    onClick = { showProfileModal = true }
                                )
                            }
                        }
                    )
                }
            } // closes AnimatedContent
        },
        expanded = false,
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = SearchBarDefaults.colors(
            containerColor = containerColor
        ),
        shape = MaterialTheme.shapes.extraLarge,
        windowInsets = WindowInsets(0.dp)
    ) {}

    if (showProfileModal && userProfile != null) {
        ProfileModal(
            userProfile = userProfile,
            accounts = accounts,
            onDismiss = { showProfileModal = false },
            onSignOut = {
                showProfileModal = false
                onSignOut()
            },
            onSwitchAccount = onSwitchAccount,
            onAddAccount = onAddAccount,
            onSwitchAccountClick = {
                showProfileModal = false
                showSwitchAccountDialog = true
            },
            onTrashClick = {
                showProfileModal = false
                onTrashClick()
            },
            onStarredClick = {
                showProfileModal = false
                onStarredClick()
            },
            onSettings = {
                showProfileModal = false
                onSettings()
            }
        )
    }

    if (showSwitchAccountDialog && userProfile != null) {
        SwitchAccountDialog(
            userProfile = userProfile,
            accounts = accounts,
            onDismiss = { showSwitchAccountDialog = false },
            onSwitchAccount = { accountId ->
                showSwitchAccountDialog = false
                onSwitchAccount(accountId)
            },
            onAddAccount = {
                showSwitchAccountDialog = false
                onAddAccount()
            }
        )
    }
}

// ── Avatar button ────────────────────────────────────────────────────────────

@Composable
private fun AvatarButton(
    userProfile: UserProfile?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
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
                text = initial,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.background
            )
        }
    }
}

// ── Switch Account Dialog ──────────────────────────────────────────────────

@Composable
private fun SwitchAccountDialog(
    userProfile: UserProfile,
    accounts: List<UserProfile>,
    onDismiss: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 20.dp),
            ) {
                Text(
                    text = "Switch Account",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    accounts.forEach { account ->
                        val isCurrent = account.id == userProfile.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { if (!isCurrent) onSwitchAccount(account.id) }
                                .background(if (isCurrent) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (!account.photoUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = account.photoUrl,
                                    contentDescription = "Profile photo",
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(CircleShape)
                                        .background(if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = account.displayName.firstOrNull()?.uppercase() ?: "?",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isCurrent) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.background
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = account.displayName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = account.email,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Current account",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable(onClick = onAddAccount)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Add another account",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

// ── Profile Modal ────────────────────────────────────────────────────────────

@Composable
private fun ProfileModal(
    userProfile: UserProfile,
    accounts: List<UserProfile>,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onSwitchAccountClick: () -> Unit,
    onTrashClick: () -> Unit,
    onStarredClick: () -> Unit,
    onSettings: () -> Unit = {}
) {
    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 20.dp),
            ) {
                // ── Header: Avatar + Name + Email ────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large avatar
                    if (!userProfile?.photoUrl.isNullOrEmpty()) {
                        AsyncImage(
                            model = userProfile!!.photoUrl,
                            contentDescription = "Profile photo",
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        val initial = userProfile?.displayName?.firstOrNull()?.uppercase() ?: "?"
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.onSurface),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.background
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = userProfile?.displayName ?: "Account",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Email in a subtle pill
                    Box(
                        modifier = Modifier
                            .background(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = userProfile?.email ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))



                // ── Menu Items ───────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                ) {
                    // Starred
                    ProfileMenuItem(
                        icon = Icons.Outlined.Star,
                        label = "Starred",
                        onClick = onStarredClick
                    )

                    // Trash
                    ProfileMenuItem(
                        icon = Icons.Outlined.Delete,
                        label = "Trash",
                        onClick = onTrashClick
                    )

                    // Settings
                    ProfileMenuItem(
                        icon = Icons.Outlined.Settings,
                        label = "Settings",
                        onClick = onSettings
                    )

                    // Switch account
                    ProfileMenuItem(
                        icon = Icons.Outlined.AccountCircle, // Use a profile icon
                        label = "Switch account",
                        onClick = {
                            onDismiss()
                            onSwitchAccountClick()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // ── Sign out button ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    OutlinedButton(
                        onClick = onSignOut,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        )
                    ) {
                        Text(
                            text = "Sign out",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileMenuItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ── Date grouping ────────────────────────────────────────────────────────────

private fun groupThreadsByDate(threads: List<EmailThread>): List<Pair<String, List<EmailThread>>> {
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }

    val groups = linkedMapOf<String, MutableList<EmailThread>>()

    for (thread in threads) {
        val cal = Calendar.getInstance().apply { timeInMillis = thread.date }
        val label = when {
            isSameDay(cal, today) -> "Today"
            isSameDay(cal, yesterday) -> "Yesterday"
            else -> "Earlier"
        }
        groups.getOrPut(label) { mutableListOf() }.add(thread)
    }

    return groups.map { (k, v) -> k to v.toList() }
}

private fun isSameDay(a: Calendar, b: Calendar): Boolean =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)