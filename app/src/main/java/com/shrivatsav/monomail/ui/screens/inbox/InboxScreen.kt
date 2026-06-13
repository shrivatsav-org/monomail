package com.shrivatsav.monomail.ui.screens.inbox

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
    onCompose: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    val pullToRefreshState = rememberPullToRefreshState()

    var searchQuery by remember { mutableStateOf("") }
    val currentThreads = (state as? InboxState.Success)?.threads ?: emptyList()

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

    // ── Reveal offset: follows the pull gesture, settles based on refresh state ──
    val loaderHeight = 56.dp
    val density = LocalDensity.current
    val loaderHeightPx = with(density) { loaderHeight.toPx() }

    val revealOffset = remember { Animatable(0f) }

    // While actively pulling, follow the gesture's progress (clamped to loader height)
    LaunchedEffect(pullToRefreshState.distanceFraction) {
        if (!isRefreshing) {
            val target = min(pullToRefreshState.distanceFraction, 1f) * loaderHeightPx
            revealOffset.snapTo(target)
        }
    }

    // When refreshing starts/ends, animate to the resting position
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            revealOffset.animateTo(loaderHeightPx, animationSpec = tween(250))
        } else {
            revealOffset.animateTo(0f, animationSpec = tween(250))
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Loader anchored at the top, sits behind the shifting content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(loaderHeight)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center
            ) {
                if (revealOffset.value > 0f) {
                    LoadingIndicator(
                        modifier = Modifier.size(36.dp),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Main content — search bar + list — shifts down to reveal the loader
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = revealOffset.value
                    }
                    .background(MaterialTheme.colorScheme.background)
            ) {
                InboxSearchBar(
                    userProfile = userProfile,
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    onServerSearch = { viewModel.searchServer(it) },
                    onSignOut = onSignOut,
                    onMarkAllRead = { viewModel.markAllAsRead() },
                    onStarredClick = { viewModel.switchTab(InboxTab.STARRED) }
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
                                    onClick = { viewModel.loadInbox() },
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
                        val grouped = groupThreadsByDate(threadsToDisplay)

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
                                                    .animateItem()
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
                                                    if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                                                        if (currentTab == InboxTab.ARCHIVED) {
                                                            viewModel.unarchiveThread(thread.threadId)
                                                        } else {
                                                            viewModel.archiveThread(thread.threadId)
                                                        }
                                                        true
                                                    } else {
                                                        false
                                                    }
                                                }
                                            )
                                            
                                            SwipeToDismissBox(
                                                state = dismissState,
                                                modifier = Modifier.animateItem(),
                                                enableDismissFromEndToStart = false,
                                                backgroundContent = {
                                                    val color by animateColorAsState(
                                                        when (dismissState.targetValue) {
                                                            SwipeToDismissBoxValue.Settled -> Color.Transparent
                                                            else -> MaterialTheme.colorScheme.primaryContainer
                                                        }
                                                    )
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxSize()
                                                            .background(color)
                                                            .padding(horizontal = 20.dp),
                                                        contentAlignment = Alignment.CenterStart
                                                    ) {
                                                        Icon(
                                                            imageVector = if ((state as? InboxState.Success)?.currentTab == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                                            contentDescription = if ((state as? InboxState.Success)?.currentTab == InboxTab.ARCHIVED) "Unarchive" else "Archive",
                                                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                                                        )
                                                    }
                                                }
                                            ) {
                                                EmailItem(
                                                thread = thread,
                                                onClick = { onEmailClick(thread.threadId) },
                                                onStarClick = { viewModel.toggleStar(thread.threadId) },
                                                modifier = Modifier.animateItem()
                                            )}
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
                                }
                            }
                        }
                    }
                }
            }

            // Custom Floating Docked Toolbar
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
                        IconButton(onClick = { viewModel.switchTab(InboxTab.INBOX) }) {
                            Icon(
                                imageVector = Icons.Outlined.Inbox, 
                                contentDescription = "Inbox",
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
                    shape = RoundedCornerShape(16.dp),
                    elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = "Compose")
                }
            }
        }
    }
}

// ── Search bar ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxSearchBar(
    userProfile: UserProfile?,
    query: String,
    onQueryChange: (String) -> Unit,
    onServerSearch: (String) -> Unit,
    onSignOut: () -> Unit,
    onMarkAllRead: () -> Unit,
    onStarredClick: () -> Unit
) {
    var showProfileModal by remember { mutableStateOf(false) }

    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = query,
                onQueryChange = onQueryChange,
                onSearch = { onServerSearch(query) },
                expanded = false,
                onExpandedChange = {},
                placeholder = {
                    Text(
                        text = "Search in mail",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                },
                trailingIcon = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        IconButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Mark all as read",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        AvatarButton(
                            userProfile = userProfile,
                            onClick = { showProfileModal = true }
                        )
                    }
                }
            )
        },
        expanded = false,
        onExpandedChange = {},
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = SearchBarDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        shape = MaterialTheme.shapes.extraLarge,
        windowInsets = WindowInsets(0.dp)
    ) {}

    if (showProfileModal) {
        ProfileModal(
            userProfile = userProfile,
            onDismiss = { showProfileModal = false },
            onSignOut = {
                showProfileModal = false
                onSignOut()
            },
            onStarredClick = {
                showProfileModal = false
                onStarredClick()
            }
        )
    }
}

// ── Avatar button ────────────────────────────────────────────────────────────

@Composable
private fun AvatarButton(
    userProfile: UserProfile?,
    onClick: () -> Unit
) {
    if (userProfile != null && !userProfile.photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = userProfile.photoUrl,
            contentDescription = "Profile",
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
        )
    } else {
        val initial = userProfile?.displayName?.firstOrNull()?.uppercase() ?: "?"
        Box(
            modifier = Modifier
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

// ── Profile Modal ────────────────────────────────────────────────────────────

@Composable
private fun ProfileModal(
    userProfile: UserProfile?,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onStarredClick: () -> Unit
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

                    // Settings
                    ProfileMenuItem(
                        icon = Icons.Outlined.Settings,
                        label = "Settings",
                        onClick = { /* TODO */ }
                    )

                    // Help & feedback
                    ProfileMenuItem(
                        icon = Icons.Outlined.AccountCircle,
                        label = "Manage account",
                        onClick = { /* TODO */ }
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