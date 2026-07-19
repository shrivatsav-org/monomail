package com.shrivatsav.monomail.feature.inbox

import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import com.shrivatsav.monomail.ui.theme.cornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import com.shrivatsav.monomail.feature.inbox.components.*

import com.shrivatsav.monomail.ui.components.AvatarCircle
import com.shrivatsav.monomail.ui.components.TooltipIconButton
import com.shrivatsav.monomail.model.InboxTab
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import com.shrivatsav.monomail.feature.auth.R as AuthR
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import kotlinx.coroutines.launch

data class InboxNavActions(
    val onEmailClick: (String) -> Unit,
    val onSignOut: () -> Unit,
    val onCompose: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onAddAccount: () -> Unit = {},
    val onScheduledClick: () -> Unit = {},
    val onNavigateToImapSetup: () -> Unit = {}
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    userProfile: UserProfile?,
    navActions: InboxNavActions
) {
    val state by viewModel.state.collectAsState()
    val unifiedInboxEnabled by viewModel.unifiedInboxEnabled.collectAsState()
    val showWelcomePrompt by viewModel.showWelcomePrompt.collectAsState()
    val scheduledCount by viewModel.scheduledCount.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val immediateTab by viewModel.currentTab.collectAsState()

    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> viewModel.setForegroundPollingEnabled(true)
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> viewModel.setForegroundPollingEnabled(false)
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            viewModel.setForegroundPollingEnabled(false)
            lifecycle.removeObserver(observer)
        }
    }

    var threadToDelete by remember { mutableStateOf<String?>(null) }
    var showClearTrashWarning by remember { mutableStateOf(false) }
    var showClearSpamWarning by remember { mutableStateOf(false) }
    var isTrashCountdownActive by remember { mutableStateOf(false) }
    var isSpamCountdownActive by remember { mutableStateOf(false) }
    val appSettings by viewModel.appSettingsState.collectAsState()
    val searchFilters by viewModel.searchFilters.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    val pullToRefreshState = rememberPullToRefreshState()
    val currentThreads = (state as? InboxState.Success)?.threads ?: emptyList()
    val hapticFeedback = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    var longPressedThread by remember { mutableStateOf<EmailThread?>(null) }
    var activeModal by remember { mutableStateOf<ModalType?>(null) }
    var showSnoozePicker by remember { mutableStateOf(false) }
    var snoozeThreadId by remember { mutableStateOf<String?>(null) }
    var showPerformanceWarningDialog by remember { mutableStateOf(false) }
    val onSnoozeSelected: (String) -> Unit = remember { { id -> snoozeThreadId = id; showSnoozePicker = true } }
    val isBulkMode by viewModel.isBulkSelectMode.collectAsState()
    val selectedThreadIds by viewModel.selectedThreadIds.collectAsState()
    val selectedCount by viewModel.selectedCount.collectAsState()
    var isSearchFocused by remember { mutableStateOf(false) }
    val isSearchActive = searchFilters.query.isNotEmpty()
    if (isSearchActive) {
        BackHandler {
            viewModel.clearSearch()
        }
    } else if (isSearchFocused) {
        BackHandler {
            focusManager.clearFocus()
        }
    } else if (isBulkMode) {
        BackHandler { viewModel.exitBulkSelectMode() }
    } else if (immediateTab != InboxTab.INBOX) {
        BackHandler { viewModel.switchTab(InboxTab.INBOX) }
    }

    LaunchedEffect(isSearchActive) {
        if (!isSearchActive) {
            focusManager.clearFocus()
            kotlinx.coroutines.delay(100)
            focusManager.clearFocus()
        }
    }

    var blurForModal by remember { mutableStateOf(false) }
    LaunchedEffect(activeModal) {
        if (activeModal != null) {
            kotlinx.coroutines.delay(80)
            blurForModal = true
        } else {
            blurForModal = false
        }
    }

    val isRefreshing = (state as? InboxState.Success)?.isRefreshing == true
    val navBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(viewModel) {
        viewModel.uiError.collect { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.scheduledEmailEvents.collect { event ->
            val formatted = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(event.scheduledAt))
            snackbarHostState.showSnackbar(
                "Email scheduled for $formatted"
            )
        }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(
            snackbarData = it,
            shape = com.shrivatsav.monomail.ui.theme.cornerShape(12.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionContentColor = MaterialTheme.colorScheme.inverseOnSurface
        ) } },
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val shouldBlur =
                longPressedThread != null || blurForModal || threadToDelete != null || showWelcomePrompt || showClearTrashWarning || showClearSpamWarning

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (shouldBlur) Modifier.blur(12.dp) else Modifier)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                ) {

                    InboxSearchBar(
                        userProfile = userProfile,
                        accounts = accounts,
                        query = searchFilters.query,
                        onQueryChange = { viewModel.updateSearch(searchFilters.copy(query = it)) },
                        onServerSearch = { viewModel.searchServer(it) },
                        onFocusChange = { isSearchFocused = it },
                        actions = SearchBarActions(
                            onMarkAllRead = { viewModel.markAllAsRead() },
                            onScheduledClick = navActions.onScheduledClick,
                            scheduledCount = scheduledCount,
                            onUndo = { viewModel.undoAction() },
                            onOpenProfile = { activeModal = ModalType.PROFILE },
                            showMarkAllRead = appSettings.showMarkAllRead
                        ),
                        isRefreshing = isRefreshing,
                        bulkSelection = BulkSelectionState(
                            isBulkMode = isBulkMode,
                            selectedCount = selectedCount,
                            totalCount = if (isSearchActive) searchResults.size else currentThreads.size,
                            onSelectAll = { viewModel.selectAll() },
                            onDeselectAll = { viewModel.deselectAll() },
                            onDone = { viewModel.exitBulkSelectMode() }
                        ),
                        unifiedInboxEnabled = unifiedInboxEnabled
                    )

                    SearchFilterBar(
                        filters = searchFilters,
                        onFiltersChanged = { viewModel.updateSearch(it) },
                        visible = isSearchActive
                    )

                    val isOnline = rememberConnectivityState()
                    AnimatedVisibility(
                        visible = !isOnline,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        OfflineBanner()
                    }
                    when (val s = state) {
                        is InboxState.Loading -> {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                repeat(8) { ShimmerEmailItem() }
                            }
                        }

                        is InboxState.Error -> {
                            com.shrivatsav.monomail.ui.components.EmptyStateView(
                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.ERROR_CLOUD,
                                title = "Something went wrong",
                                subtitle = s.message,
                                ctaText = "Retry",
                                onCtaClick = { viewModel.refresh() }
                            )
                        }

                        is InboxState.Success -> {
                            val currentTab = s.currentTab
                            key(currentTab) {
                            val savedPos = viewModel.scrollPositions.collectAsState().value[currentTab] ?: (0 to 0)
                            val listState = rememberLazyListState(
                                initialFirstVisibleItemIndex = savedPos.first,
                                initialFirstVisibleItemScrollOffset = savedPos.second
                            )
                            val shouldLoadMore by remember {
                                derivedStateOf {
                                    val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    val totalItems = listState.layoutInfo.totalItemsCount
                                    totalItems > 0 && lastVisible >= totalItems - 5
                                }
                            }
                            LaunchedEffect(Unit) {
                                snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
                            }
                            LaunchedEffect(listState, currentTab) {
                                snapshotFlow {
                                    listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
                                }.collect { (index, offset) ->
                                    viewModel.saveScrollState(currentTab, index, offset)
                                }
                            }
                            val threadsToDisplay = if (isSearchActive) searchResults else s.threads
                            var expandedGroupsList by androidx.compose.runtime.saveable.rememberSaveable {
                                mutableStateOf(emptyList<String>())
                            }
                            var inboxStructure by remember { mutableStateOf(InboxStructure(emptyList(), emptyList())) }
                            var isComputingStructure by remember { mutableStateOf(true) }
                            LaunchedEffect(threadsToDisplay, appSettings.smartGroupingEnabled, appSettings.smartGroupingRecentOnly) {
                                isComputingStructure = true
                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                    val useGrouping = appSettings.smartGroupingEnabled &&
                                            !isSearchActive && currentTab == InboxTab.INBOX
                                    val result = computeInboxStructure(
                                        threadsToDisplay,
                                        useGrouping,
                                        appSettings.smartGroupingRecentOnly
                                    )
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                        inboxStructure = result
                                        isComputingStructure = false
                                    }
                                }
                            }
                            val displayItems = remember(inboxStructure, expandedGroupsList) {
                                flattenDisplayItems(inboxStructure, expandedGroupsList.toSet(), tabPrefix = currentTab.name)
                            }
                            val orderedThreadIds by remember(displayItems) {
                                derivedStateOf {
                                    displayItems.mapNotNull { item ->
                                        when (item) {
                                            is InboxDisplayItem.SingleThread -> item.thread.threadId
                                            is InboxDisplayItem.NestedThread -> item.thread.threadId
                                            else -> null
                                        }
                                    }
                                }
                            }
                            PullToRefreshBox(
                                isRefreshing = s.isRefreshing,
                                onRefresh = { viewModel.refresh() },
                                state = pullToRefreshState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {}
                            ) {
                                if (threadsToDisplay.isEmpty()) {
                                    val illustration: com.shrivatsav.monomail.ui.components.IllustrationType
                                    val title: String
                                    val subtitle: String
                                    val ctaText: String?
                                    val onCtaClick: (() -> Unit)?
                                    if (isSearchActive) {
                                        illustration = com.shrivatsav.monomail.ui.components.IllustrationType.SEARCH_EMPTY
                                        title = "No results found"
                                        subtitle = "Try searching on the server instead."
                                        ctaText = "Search server"
                                        onCtaClick = { viewModel.searchServer(searchFilters.query) }
                                    } else {
                                        when (currentTab) {
                                            InboxTab.SENT -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.PAPER_PLANE
                                                title = "Nothing sent yet"
                                                subtitle = "Your sent emails will appear here."
                                                ctaText = "Compose"
                                                onCtaClick = { navActions.onCompose() }
                                            }
                                            InboxTab.STARRED -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "No starred emails"
                                                subtitle = "Star important emails to find them here."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                            InboxTab.TRASH, InboxTab.SPAM -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "Empty"
                                                subtitle = "No messages here."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                            InboxTab.ARCHIVED -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "No archived emails"
                                                subtitle = "Emails you archive will appear here."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                            InboxTab.SNOOZED -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "Nothing snoozed"
                                                subtitle = "Snoozed emails will reappear when ready."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                            InboxTab.UNIFIED -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "You're all caught up"
                                                subtitle = "No emails across all your accounts."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                            else -> {
                                                illustration = com.shrivatsav.monomail.ui.components.IllustrationType.INBOX_ZERO
                                                title = "You're all caught up"
                                                subtitle = "Your inbox is empty."
                                                ctaText = null
                                                onCtaClick = null
                                            }
                                        }
                                    }
                                    com.shrivatsav.monomail.ui.components.EmptyStateView(
                                        illustration = illustration,
                                        title = title,
                                        subtitle = subtitle,
                                        ctaText = ctaText,
                                        onCtaClick = onCtaClick
                                    )
                                } else {
                                    val itemKeyFn = remember(currentTab) { { it: InboxDisplayItem -> "${currentTab.name}_${it.key}" } }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 100.dp)
                                    ) {
                                        itemsIndexed(displayItems, key = { _, it -> itemKeyFn(it) }) { index, displayItem ->
                                            com.shrivatsav.monomail.ui.components.AnimatedListItem(
                                                index = index,
                                                itemKey = itemKeyFn(displayItem),
                                                animatedItemsTracker = viewModel.animatedItemsTracker
                                            ) {
                                                when (displayItem) {
                                                is InboxDisplayItem.DateHeader -> {
                                                    com.shrivatsav.monomail.ui.components.SectionHeader(
                                                        label = displayItem.title,
                                                        showTopDivider = false,
                                                        modifier = Modifier.animateItem().background(MaterialTheme.colorScheme.background)
                                                    )
                                                }

                                                is InboxDisplayItem.GroupHeader -> {
                                                    GroupHeaderItem(
                                                        modifier = Modifier.animateItem(),
                                                        groupName = displayItem.groupName,
                                                        count = displayItem.count,
                                                        unreadCount = displayItem.unreadCount,
                                                        isExpanded = displayItem.isExpanded,
                                                        onClick = {
                                                            expandedGroupsList =
                                                                if (expandedGroupsList.contains(
                                                                        displayItem.groupName
                                                                    )
                                                                ) {
                                                                    expandedGroupsList - displayItem.groupName
                                                                } else {
                                                                    expandedGroupsList + displayItem.groupName
                                                                }
                                                        }
                                                    )
                                                }
                                                is InboxDisplayItem.SingleThread -> {
                                                    val isUnread = !displayItem.thread.isRead
                                                    val prevIsSingle = index > 0 && displayItems[index - 1] is InboxDisplayItem.SingleThread
                                                    val nextIsSingle = index < displayItems.lastIndex && displayItems[index + 1] is InboxDisplayItem.SingleThread
                                                    val groupPos = when {
                                                        !prevIsSingle && !nextIsSingle -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.SOLO
                                                        !prevIsSingle -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.TOP
                                                        !nextIsSingle -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.BOTTOM
                                                        else -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.MIDDLE
                                                    }
                                                    val prevUnread = isUnread && index > 0 && displayItems[index - 1].let {
                                                        it is InboxDisplayItem.SingleThread && !it.thread.isRead
                                                    }
                                                    val nextUnread = isUnread && index < displayItems.lastIndex && displayItems[index + 1].let {
                                                        it is InboxDisplayItem.SingleThread && !it.thread.isRead
                                                    }
                                                    val unreadPos = when {
                                                        !isUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.SOLO
                                                        prevUnread && nextUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.MIDDLE
                                                        prevUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.BOTTOM
                                                        nextUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.TOP
                                                        else -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.SOLO
                                                    }
                                                    SwipeableEmailItem(
                                                        modifier = Modifier.animateItem(),
                                                        thread = displayItem.thread,
                                                        tabForSwipe = currentTab,
                                                        appSettings = appSettings,
                                                        viewModel = viewModel,
                                                        callbacks = SwipeCallbacks(
                                                            onThreadToDeleteChange = { threadToDelete = it },
                                                            onEmailClick = { navActions.onEmailClick(displayItem.thread.threadId) },
                                                            onLongClick = {
                                                                hapticFeedback.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                                longPressedThread = displayItem.thread
                                                            },
                                                            isNested = false
                                                        ),
                                                        selection = SelectionState(
                                                            isSelected = displayItem.thread.threadId in selectedThreadIds,
                                                            isBulkMode = isBulkMode,
                                                            onSelectToggle = { viewModel.toggleThreadSelection(displayItem.thread.threadId) },
                                                            onRangeSelect = { viewModel.rangeSelectTo(displayItem.thread.threadId, orderedThreadIds) },
                                                            onAvatarLongClick = {
                                                                viewModel.enterBulkSelectMode(displayItem.thread.threadId)
                                                            }
                                                        ),
                                                        unreadPosition = unreadPos,
                                                        groupPosition = groupPos
                                                    )
                                                }

                                                is InboxDisplayItem.NestedThread -> {
                                                    val isUnread = !displayItem.thread.isRead
                                                    val prevIsNested = index > 0 && displayItems[index - 1] is InboxDisplayItem.NestedThread
                                                    val nextIsNested = index < displayItems.lastIndex && displayItems[index + 1] is InboxDisplayItem.NestedThread
                                                    val groupPos = when {
                                                        !prevIsNested && !nextIsNested -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.SOLO
                                                        !prevIsNested -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.TOP
                                                        !nextIsNested -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.BOTTOM
                                                        else -> com.shrivatsav.monomail.feature.inbox.components.GroupPosition.MIDDLE
                                                    }
                                                    val prevUnread = isUnread && index > 0 && displayItems[index - 1].let {
                                                        it is InboxDisplayItem.NestedThread && !it.thread.isRead
                                                    }
                                                    val nextUnread = isUnread && index < displayItems.lastIndex && displayItems[index + 1].let {
                                                        it is InboxDisplayItem.NestedThread && !it.thread.isRead
                                                    }
                                                    val unreadPos = when {
                                                        !isUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.SOLO
                                                        prevUnread && nextUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.MIDDLE
                                                        prevUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.BOTTOM
                                                        nextUnread -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.TOP
                                                        else -> com.shrivatsav.monomail.feature.inbox.components.UnreadPosition.SOLO
                                                    }
                                                    SwipeableEmailItem(
                                                        modifier = Modifier.animateItem(),
                                                        thread = displayItem.thread,
                                                        tabForSwipe = currentTab,
                                                        appSettings = appSettings,
                                                        viewModel = viewModel,
                                                        callbacks = SwipeCallbacks(
                                                            onThreadToDeleteChange = { threadToDelete = it },
                                                            onEmailClick = { navActions.onEmailClick(displayItem.thread.threadId) },
                                                            onLongClick = {
                                                                hapticFeedback.performHapticFeedback(
                                                                    HapticFeedbackType.LongPress
                                                                )
                                                                longPressedThread = displayItem.thread
                                                            },
                                                            isNested = true
                                                        ),
                                                        selection = SelectionState(
                                                            isSelected = displayItem.thread.threadId in selectedThreadIds,
                                                            isBulkMode = isBulkMode,
                                                            onSelectToggle = { viewModel.toggleThreadSelection(displayItem.thread.threadId) },
                                                            onRangeSelect = { viewModel.rangeSelectTo(displayItem.thread.threadId, orderedThreadIds) },
                                                            onAvatarLongClick = {
                                                                viewModel.enterBulkSelectMode(displayItem.thread.threadId)
                                                            }
                                                        ),
                                                        unreadPosition = unreadPos,
                                                        groupPosition = groupPos
                                                    )
                                                }
                                                }
                                            }
                                        }

                                        if (isSearchActive && s.threads.isNotEmpty()) {
                                            item(key = "search_server_cta") {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(
                                                            horizontal = 24.dp,
                                                            vertical = 28.dp
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    OutlinedButton(
                                                        onClick = { viewModel.searchServer(searchFilters.query) },
                                                        shape = MaterialTheme.shapes.large
                                                    ) { Text("Search server for older emails") }
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
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            } // key(currentTab)
                        }
                    }
                }
            }


            AnimatedVisibility(
                visible = longPressedThread == null && activeModal == null && !isBulkMode,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp)
                    ) {
                        BottomFabArea(
                            immediateTab = immediateTab,
                            appSettings = appSettings,
                            unifiedInboxEnabled = unifiedInboxEnabled,
                            navActions = navActions,
                            viewModel = viewModel,
                            navBarHeight = navBarHeight,
                            onEmptyBin = { isTrash -> if (isTrash) showClearTrashWarning = true else showClearSpamWarning = true }
                        )
                    }
            }

            AnimatedVisibility(
                visible = isBulkMode,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                BulkActionBar(
                    currentTab = immediateTab,
                    callbacks = BulkActionBarCallbacks(
                        onArchive = { viewModel.bulkArchive() },
                        onDelete = { viewModel.bulkDelete() },
                        onMarkRead = { viewModel.bulkMarkRead() },
                        onMarkUnread = { viewModel.bulkMarkUnread() },
                        onToggleStar = { viewModel.bulkToggleStar() },
                        onUnarchive = { viewModel.bulkUnarchive() },
                        onRestore = { viewModel.bulkRestore() },
                        onReportNotSpam = { viewModel.bulkReportNotSpam() },
                    ),
                    modifier = Modifier.padding(bottom = navBarHeight + 8.dp)
                )
            }

            
            var displayedThread by remember { mutableStateOf<EmailThread?>(null) }
            LaunchedEffect(longPressedThread) {
                if (longPressedThread != null) displayedThread = longPressedThread
            }

            AnimatedVisibility(
                visible = longPressedThread != null,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                val thread = displayedThread ?: return@AnimatedVisibility
                BackHandler { longPressedThread = null }
                LongPressMenu(
                    thread = thread,
                    state = state,
                    onDismiss = { longPressedThread = null },
                    actions = LongPressMenuActions(
                        onEmailClick = { navActions.onEmailClick(thread.threadId) },
                        onStar = { viewModel.toggleStar(thread.threadId) },
                        onArchive = { tab -> when (tab) {
                            InboxTab.ARCHIVED -> viewModel.unarchiveThread(thread.threadId)
                            InboxTab.SPAM -> viewModel.reportNotSpam(thread.threadId)
                            else -> viewModel.archiveThread(thread.threadId)
                        } },
                        onToggleRead = {
                            if (thread.isRead) viewModel.markThreadAsUnread(thread.threadId)
                            else viewModel.markThreadAsRead(thread.threadId)
                        },
                        onDelete = { threadToDelete = thread.threadId },
                        onSnooze = { onSnoozeSelected(thread.threadId) },
                        onUnsnooze = { viewModel.unsnoozeThread(thread.threadId) }
                    )
                )
            }

            
            ModalOverlay(
                activeModal = activeModal,
                userProfile = userProfile,
                accounts = accounts,
                callbacks = ModalCallbacks(
                    onDismiss = { activeModal = null },
                    onSignOut = { activeModal = null; navActions.onSignOut() },
                    onSwitchAccount = { viewModel.switchAccount(it); activeModal = null },
                    onCycleAccount = { viewModel.switchAccount(it) },
                    onAddAccount = {
                        when {
                            accounts.size >= 10 -> coroutineScope.launch {
                                snackbarHostState.showSnackbar("Maximum limit of 10 accounts reached.")
                            }
                            accounts.size >= 3 -> showPerformanceWarningDialog = true
                            else -> activeModal = ModalType.ADD_ACCOUNT
                        }
                    },
                    onShowSwitchAccount = { activeModal = ModalType.SWITCH_ACCOUNT },
                    onBackToProfile = { activeModal = ModalType.PROFILE },
                    onSettings = { activeModal = null; navActions.onSettings() },
                    onNavigateToImapSetup = navActions.onNavigateToImapSetup,
                    onToggleUnified = { viewModel.setUnifiedInboxEnabled(it) }
                ),
                unifiedInboxEnabled = unifiedInboxEnabled
            )
        }
            
            val toastState by viewModel.toastState.collectAsState()
            Box {
                TopAlertBanner(
                    toastState = toastState,
                    onUndo = { viewModel.undoAction() }
                )
            }
    }

    // ── Welcome to Monomail modal ────────────────────────────────────

    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showWelcomePrompt,
        onDismiss = { viewModel.dismissWelcomePrompt() }
    ) {
        Surface(
            shape = cornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val context = androidx.compose.ui.platform.LocalContext.current

            val kofiIcon = remember {
                val bmp = android.graphics.BitmapFactory.decodeStream(
                    context.resources.openRawResource(com.shrivatsav.monomail.core.designsystem.R.raw.kofi)
                )
                if (bmp != null) androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
                else null
            }

            val versionName = remember {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.7.24"
                } catch (_: Exception) { "1.7.24" }
            }

            val matchedNotes = remember(versionName) {
                allReleaseNotes.firstOrNull { it.version == versionName }
            }

            Column(modifier = Modifier.padding(26.dp)) {

                // ── Header ──────────────────────────────────────────────
                Text(
                    text = "Welcome to Monomail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "v$versionName — An open-source, privacy-first email client",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(24.dp))

                // ── What's New ─────────────────────────────────────────
                Surface(
                    shape = cornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.NewReleases,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "What's New",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        val notes = matchedNotes ?: allReleaseNotes.first()
                        notes.changes.forEach { change ->
                            Row(
                                modifier = Modifier.padding(vertical = 2.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    "•",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text(
                                    change,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Support ────────────────────────────────────────────
                Text(
                    "Support Monomail",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SupportCard(
                        modifier = Modifier.weight(1f),
                        label = "Buy me a coffee",
                        onClick = { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }
                    ) {
                        if (kofiIcon != null) {
                            Icon(
                                painter = kofiIcon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = Color.Unspecified
                            )
                        } else {
                            Icon(Icons.Rounded.FavoriteBorder, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    SupportCard(
                        modifier = Modifier.weight(1f),
                        label = "Star on GitHub",
                        onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") }
                    ) {
                        Icon(Icons.Rounded.Star, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }
                Spacer(Modifier.height(10.dp))

                SupportCard(
                    modifier = Modifier.fillMaxWidth(),
                    label = "Join Discord",
                    onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") }
                ) {
                    Icon(
                        imageVector = remember {
                            ImageVector.Builder(
                                name = "Discord",
                            defaultWidth = 22.dp,
                            defaultHeight = 22.dp,
                            viewportWidth = 24f,
                            viewportHeight = 24f
                        ).addPath(
                            fill = SolidColor(Color.Black),
                            pathData = addPathNodes(
                                "M20.317 4.37a19.791 19.791 0 0 0-4.885-1.515.074.074 0 0 0-.079.037c-.21.375-.444.864-.608 1.25a18.27 18.27 0 0 0-5.487 0 12.64 12.64 0 0 0-.617-1.25.077.077 0 0 0-.079-.037A19.736 19.736 0 0 0 3.677 4.37a.07.07 0 0 0-.032.027C.533 9.046-.32 13.58.099 18.057a.082.082 0 0 0 .031.057 19.9 19.9 0 0 0 5.993 3.03.078.078 0 0 0 .084-.028 14.09 14.09 0 0 0 1.226-1.994.076.076 0 0 0-.041-.106 13.107 13.107 0 0 1-1.872-.892.077.077 0 0 1-.008-.128 10.2 10.2 0 0 0 .372-.292.074.074 0 0 1 .077-.01c3.928 1.793 8.18 1.793 12.062 0a.074.074 0 0 1 .078.01c.12.098.246.198.373.292a.077.077 0 0 1-.006.127 12.299 12.299 0 0 1-1.873.892.077.077 0 0 0-.041.107c.36.698.772 1.362 1.225 1.993a.076.076 0 0 0 .084.028 19.839 19.839 0 0 0 6.002-3.03.077.077 0 0 0 .032-.054c.5-5.177-.838-9.674-3.549-13.66a.061.061 0 0 0-.031-.03zM8.02 15.33c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.956-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.956 2.418-2.157 2.418zm7.975 0c-1.183 0-2.157-1.085-2.157-2.419 0-1.333.955-2.419 2.157-2.419 1.21 0 2.176 1.096 2.157 2.42 0 1.333-.946 2.418-2.157 2.418z"
                            )
                        ).build()
                    },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(18.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Spacer(Modifier.height(12.dp))

                // ── Secondary actions ──────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TooltipIconButton(
                        icon = Icons.Rounded.Share,
                        contentDescription = "Share Monomail",
                        onClick = {
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(
                                    android.content.Intent.EXTRA_TEXT,
                                    "Check out Monomail - a private, open-source email client: https://github.com/shrivatsav-0/monomail"
                                )
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Share Monomail"))
                        }
                    )
                    TooltipIconButton(
                        icon = Icons.Rounded.BugReport,
                        contentDescription = "Report Issue",
                        onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail/issues") }
                    )
                    TooltipIconButton(
                        icon = Icons.Rounded.AccountBalanceWallet,
                        contentDescription = "Donate Crypto (BASE)",
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", "0xB27Ba9241de81F6DBCB322aDd76a9d9686462e9E"))
                            android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(Modifier.height(18.dp))

                // ── Get Started ────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilledTonalButton(
                        onClick = { viewModel.dismissWelcomePrompt() },
                        shape = cornerShape(16.dp)
                    ) {
                        Text(
                            "Get Started",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }


    // Performance warning dialog for adding 4th+ account
    if (showPerformanceWarningDialog) {
        AlertDialog(
            onDismissRequest = { showPerformanceWarningDialog = false },
            title = {
                Text("Heads up", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "Adding more accounts may affect performance. " +
                    "The app might lag, email delivery may be delayed, " +
                    "and you may experience slower sync times.\n\n" +
                    "You can add up to 10 accounts total."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPerformanceWarningDialog = false
                    activeModal = ModalType.ADD_ACCOUNT
                }) {
                    Text("I understand, add anyway", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPerformanceWarningDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }


    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = threadToDelete != null,
        onDismiss = { threadToDelete = null }
    ) {
        Surface(
            shape = cornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Move to Trash?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Are you sure you want to move this conversation to the trash?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { threadToDelete = null }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = {
                        threadToDelete?.let { viewModel.deleteThread(it) }
                        threadToDelete = null
                    }) {
                        Text("Move to Trash")
                    }
                }
            }
        }
    }

    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showClearTrashWarning,
        onDismiss = { showClearTrashWarning = false; isTrashCountdownActive = false }
    ) {
        ClearCountdownDialog(
            title = "Clear Trash?",
            message = "Are you sure you want to permanently delete all messages in the trash? This action cannot be undone.",
            onDismiss = { isTrashCountdownActive = false; showClearTrashWarning = false },
            onCountdownFinished = { viewModel.emptyTrash(); showClearTrashWarning = false },
            isActive = showClearTrashWarning,
            isCountdownActive = isTrashCountdownActive,
            setCountdownActive = { isTrashCountdownActive = it }
        )
    }

    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showClearSpamWarning,
        onDismiss = { showClearSpamWarning = false; isSpamCountdownActive = false }
    ) {
        ClearCountdownDialog(
            title = "Clear Spam?",
            message = "Are you sure you want to permanently delete all messages in spam? This action cannot be undone.",
            onDismiss = { isSpamCountdownActive = false; showClearSpamWarning = false },
            onCountdownFinished = { viewModel.emptySpam(); showClearSpamWarning = false },
            isActive = showClearSpamWarning,
            isCountdownActive = isSpamCountdownActive,
            setCountdownActive = { isSpamCountdownActive = it }
        )
    }

    if (showSnoozePicker && snoozeThreadId != null) {
        SnoozePickerDialog(
            onDismiss = { showSnoozePicker = false },
            onSnooze = { timestamp ->
                snoozeThreadId?.let { viewModel.snoozeThread(it, timestamp) }
                showSnoozePicker = false
            }
        )
    }
}


@Composable
private fun ClearCountdownDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onCountdownFinished: () -> Unit,
    isActive: Boolean,
    isCountdownActive: Boolean, // Kept for compatibility with existing call sites
    setCountdownActive: (Boolean) -> Unit
) {
    var countdown by remember { mutableIntStateOf(5) }
    LaunchedEffect(isActive) {
        if (isActive) {
            countdown = 5
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
        }
    }
    Surface(
        shape = cornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        shadowElevation = 32.dp,
        modifier = Modifier.fillMaxWidth(0.88f)
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { setCountdownActive(false); onDismiss() }) { Text("Cancel") }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onCountdownFinished() },
                    enabled = countdown == 0,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(if (countdown > 0) "Delete ($countdown)" else "Delete")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BottomFabArea(
    immediateTab: InboxTab,
    appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings,
    unifiedInboxEnabled: Boolean,
    navActions: InboxNavActions,
    viewModel: InboxViewModel,
    navBarHeight: Dp,
    onEmptyBin: (isTrash: Boolean) -> Unit
) {
    val tabForDock = immediateTab
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = navBarHeight + 8.dp)
            .padding(horizontal = 16.dp)
    ) {
        BottomDockBar(
            currentTab = tabForDock,
            dockConfig = appSettings.dockConfig,
            unifiedInboxEnabled = unifiedInboxEnabled,
            appSettings = appSettings,
            onTabClick = { viewModel.switchTab(it) },
            modifier = Modifier.fillMaxWidth()
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 12.dp, top = 12.dp)
        ) {
            AnimatedContent(
                targetState = when (immediateTab) {
                    InboxTab.TRASH -> "trash"
                    InboxTab.SPAM -> "spam"
                    else -> "default"
                },
                label = "FabIconMorph",
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f)) togetherWith
                            (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f))
                }
            ) { state ->
                when (state) {
                    "trash" -> EmptyTrashFab(appSettings) { onEmptyBin(true) }
                    "spam" -> EmptySpamFab(appSettings) { onEmptyBin(false) }
                    "default" -> ComposeFab(appSettings, navActions.onCompose)
                }
            }
        }
    }
}

@Composable
private fun EmptyTrashFab(appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier = Modifier.height((42 * appSettings.navScale).dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Delete, contentDescription = "Empty Trash", modifier = Modifier.size((22 * appSettings.navScale).dp))
            Text("Empty", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun EmptySpamFab(appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier = Modifier.height((42 * appSettings.navScale).dp)
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Rounded.Report, contentDescription = "Empty Spam", modifier = Modifier.size((22 * appSettings.navScale).dp))
            Text("Empty", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}

@Composable
private fun ComposeFab(appSettings: com.shrivatsav.monomail.core.data.settings.AppSettings, onClick: () -> Unit) {
    val fabInteractionSource = remember { MutableInteractionSource() }
    val isFabPressed by fabInteractionSource.collectIsPressedAsState()
    val fabScale by animateFloatAsState(
        targetValue = if (isFabPressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "fabScale"
    )
    FloatingActionButton(
        onClick = onClick,
        interactionSource = fabInteractionSource,
        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        shape = CircleShape,
        elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
        modifier = Modifier.size((52 * appSettings.navScale).dp).graphicsLayer(scaleX = fabScale, scaleY = fabScale)
    ) {
        Icon(Icons.Rounded.Edit, contentDescription = "Compose")
    }
}

private data class LongPressMenuActions(
    val onEmailClick: () -> Unit,
    val onStar: () -> Unit,
    val onArchive: (InboxTab) -> Unit,
    val onToggleRead: () -> Unit,
    val onDelete: () -> Unit,
    val onSnooze: () -> Unit,
    val onUnsnooze: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongPressMenu(
    thread: EmailThread,
    state: InboxState,
    onDismiss: () -> Unit,
    actions: LongPressMenuActions
) {
    val tabForMenu = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
    BackHandler { onDismiss() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onDismiss() }
    )
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxWidth(0.85f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = cornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, shadowElevation = 8.dp) {
                EmailItem(thread = thread, onClick = { onDismiss(); actions.onEmailClick() }, onLongClick = {}, modifier = Modifier)
            }
            Spacer(Modifier.height(8.dp))
            Surface(shape = cornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp, shadowElevation = 8.dp) {
                LongPressActionRow(thread, tabForMenu, actions) { onDismiss() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongPressActionRow(thread: EmailThread, tabForMenu: InboxTab, actions: LongPressMenuActions, onDismiss: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        StarOrUnsnoozeAction(thread, tabForMenu, actions, onDismiss)
        ArchiveOrRestoreAction(tabForMenu, actions, onDismiss)
        ReadUnreadAction(thread, actions, onDismiss)
        if (tabForMenu != InboxTab.TRASH && tabForMenu != InboxTab.SNOOZED && tabForMenu != InboxTab.SPAM) {
            LongPressAction(icon = Icons.Rounded.Schedule, label = "Snooze", tint = MaterialTheme.colorScheme.onSurface, onClick = { onDismiss(); actions.onSnooze() })
        }
        DeleteOrRestoreAction(tabForMenu, actions, onDismiss)
    }
}

@Composable
private fun StarOrUnsnoozeAction(thread: EmailThread, tabForMenu: InboxTab, actions: LongPressMenuActions, onDismiss: () -> Unit) {
    if (tabForMenu == InboxTab.SNOOZED) {
        LongPressAction(icon = Icons.Rounded.Restore, label = "Unsnooze", tint = MaterialTheme.colorScheme.onSurface, onClick = { actions.onUnsnooze(); onDismiss() })
    } else {
        LongPressAction(icon = if (thread.isStarred) Icons.Rounded.Star else Icons.Rounded.StarBorder, label = if (thread.isStarred) "Unstar" else "Star", tint = MaterialTheme.colorScheme.onSurface, onClick = { actions.onStar(); onDismiss() })
    }
}

@Composable
private fun ArchiveOrRestoreAction(tabForMenu: InboxTab, actions: LongPressMenuActions, onDismiss: () -> Unit) {
    LongPressAction(
        icon = if (tabForMenu == InboxTab.ARCHIVED) Icons.Rounded.Inbox else Icons.Rounded.Archive,
        label = when (tabForMenu) { InboxTab.ARCHIVED -> "Unarchive"; InboxTab.SPAM -> "Not spam"; else -> "Archive" },
        tint = MaterialTheme.colorScheme.onSurface,
        onClick = { actions.onArchive(tabForMenu); onDismiss() }
    )
}

@Composable
private fun ReadUnreadAction(thread: EmailThread, actions: LongPressMenuActions, onDismiss: () -> Unit) {
    LongPressAction(icon = if (thread.isRead) Icons.Rounded.MarkEmailUnread else Icons.Rounded.CheckCircle, label = if (thread.isRead) "Unread" else "Read", tint = MaterialTheme.colorScheme.onSurface, onClick = { actions.onToggleRead(); onDismiss() })
}

@Composable
private fun DeleteOrRestoreAction(tabForMenu: InboxTab, actions: LongPressMenuActions, onDismiss: () -> Unit) {
    LongPressAction(
        icon = if (tabForMenu == InboxTab.TRASH) Icons.Rounded.Restore else Icons.Rounded.Delete,
        label = if (tabForMenu == InboxTab.TRASH) "Restore" else "Delete",
        tint = if (tabForMenu == InboxTab.TRASH) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        onClick = { if (tabForMenu == InboxTab.TRASH) { actions.onArchive(tabForMenu) } else { actions.onDelete() }; onDismiss() }
    )
}

@Composable
private fun SnoozePickerDialog(
    onDismiss: () -> Unit,
    onSnooze: (Long) -> Unit
) {
    val now = System.currentTimeMillis()
    val options = remember {
        val cal = java.util.Calendar.getInstance()
        buildList {
            val c = cal.clone() as java.util.Calendar
            c.timeInMillis = now
            c.set(java.util.Calendar.SECOND, 0)
            c.set(java.util.Calendar.MILLISECOND, 0)
            c.add(java.util.Calendar.HOUR_OF_DAY, 1)
            add(SnoozeOption("In 1 hour", c.timeInMillis))

            c.timeInMillis = now
            c.set(java.util.Calendar.HOUR_OF_DAY, 18)
            c.set(java.util.Calendar.MINUTE, 0)
            if (c.timeInMillis <= now) c.add(java.util.Calendar.DAY_OF_MONTH, 1)
            add(SnoozeOption("Later today", c.timeInMillis))

            c.timeInMillis = now
            c.set(java.util.Calendar.HOUR_OF_DAY, 20)
            c.set(java.util.Calendar.MINUTE, 0)
            if (c.timeInMillis <= now) c.add(java.util.Calendar.DAY_OF_MONTH, 1)
            add(SnoozeOption("This evening", c.timeInMillis))

            c.timeInMillis = now
            c.add(java.util.Calendar.DAY_OF_MONTH, 1)
            c.set(java.util.Calendar.HOUR_OF_DAY, 8)
            c.set(java.util.Calendar.MINUTE, 0)
            add(SnoozeOption("Tomorrow morning", c.timeInMillis))

            c.timeInMillis = now
            val currentDay = c.get(java.util.Calendar.DAY_OF_WEEK)
            var diff = java.util.Calendar.SATURDAY - currentDay
            if (diff <= 0) diff += 7
            c.add(java.util.Calendar.DAY_OF_MONTH, diff)
            c.set(java.util.Calendar.HOUR_OF_DAY, 9)
            c.set(java.util.Calendar.MINUTE, 0)
            add(SnoozeOption("This weekend", c.timeInMillis))

            c.timeInMillis = now
            var mDiff = java.util.Calendar.MONDAY - c.get(java.util.Calendar.DAY_OF_WEEK)
            if (mDiff <= 0) mDiff += 7
            c.add(java.util.Calendar.DAY_OF_MONTH, mDiff)
            c.set(java.util.Calendar.HOUR_OF_DAY, 9)
            c.set(java.util.Calendar.MINUTE, 0)
            add(SnoozeOption("Next week", c.timeInMillis))
        }
    }

    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = true,
        onDismiss = onDismiss
    ) {
        Surface(
            shape = cornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Snooze until",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))
                options.forEach { opt ->
                    TextButton(
                        onClick = { onSnooze(opt.timestamp) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = cornerShape(12.dp)
                    ) {
                        Text(opt.label, modifier = Modifier.fillMaxWidth())
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                }
            }
        }
    }
}

private data class SnoozeOption(val label: String, val timestamp: Long)

@Composable
private fun SupportCard(
    modifier: Modifier = Modifier,
    label: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(cornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = cornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            icon()
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LongPressAction(
    icon: ImageVector,
    label: String,
    tint: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(cornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}





@Composable
private fun GroupHeaderItem(
    modifier: Modifier = Modifier,
    groupName: String,
    count: Int,
    unreadCount: Int,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarCircle(
            photoUrl = null,
            displayName = groupName,
            size = 40.dp,
            textStyle = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = groupName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (unreadCount > 0) FontWeight.Bold else FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "$count emails",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        }
        if (unreadCount > 0) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(MaterialTheme.colorScheme.onSurface, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.background,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
        }
        val rotation by animateFloatAsState(
            targetValue = if (isExpanded) 180f else 0f,
            animationSpec = tween(250),
            label = "chevron"
        )
        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.rotate(rotation)
        )
    }
}





@Composable
private fun ShimmerEmailItem() {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(14.dp)
                    .clip(cornerShape(4.dp))
                    .background(color)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(cornerShape(4.dp))
                    .background(color.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .clip(cornerShape(4.dp))
                    .background(color.copy(alpha = 0.3f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(12.dp)
                .clip(cornerShape(4.dp))
                .background(color.copy(alpha = 0.3f))
        )
    }
}

@Composable
private fun WelcomeActionButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (Modifier) -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = cornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface
        )
    ) {
        icon(Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun rememberConnectivityState(): Boolean {
    val cm = androidx.compose.ui.platform.LocalContext.current
        .getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val currentOnline = remember {
        val activeNetwork = cm.activeNetwork
        val caps = activeNetwork?.let { cm.getNetworkCapabilities(it) }
        caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    var isOnline by remember { mutableStateOf(currentOnline) }
    DisposableEffect(Unit) {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { isOnline = true }
            override fun onLost(network: Network) { isOnline = false }
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                isOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            }
        }
        cm.registerDefaultNetworkCallback(callback)
        onDispose { cm.unregisterNetworkCallback(callback) }
    }
    return isOnline
}

@Composable
private fun OfflineBanner() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "You are offline",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

// ── Release notes data ──────────────────────────────────────────────────

private data class ReleaseNotes(
    val version: String,
    val changes: List<String>
)

private val allReleaseNotes = listOf(
    ReleaseNotes(version = "1.7.38", changes = listOf(
        "Optimized email detail loading with asynchronous parsing and pre-warmed instances",
        "Completed a major architectural overhaul, modularizing the application",
        "Abstracted authentication logic to support specific flavor builds",
        "Fixed a critical crash on launch related to missing HiltWorker processing",
        "Restored the custom Monomail notification icon",
        "Settings screen now accurately displays the application version"
    )),
    ReleaseNotes(version = "1.7.24", changes = listOf(
        "Optimized build times with configuration caching",
        "Fixed MSAL sign-in for release builds",
        "Enhanced FAB positioning in dock bar",
        "Improved Discord icon theming",
        "Various UI polish and bug fixes"
    )),
    ReleaseNotes(version = "1.7.23", changes = listOf(
        "Redesigned bottom dock bar with custom layout options",
        "Added template-backed scheduled send",
        "Improved swipe gesture responsiveness",
        "Enhanced tablet layout support"
    ))
)

@Composable
private fun TopAlertBanner(
    toastState: InboxViewModel.ToastState?,
    onUndo: () -> Unit
) {
    androidx.compose.animation.AnimatedVisibility(
        visible = toastState != null,
        enter = fadeIn(tween(220)) + slideInVertically(
            initialOffsetY = { -it },
            animationSpec = tween(300, easing = FastOutSlowInEasing)
        ),
        exit = fadeOut(tween(180)) + slideOutVertically(
            targetOffsetY = { -it },
            animationSpec = tween(250)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp)
    ) {
        if (toastState != null) {
            Surface(
                shape = cornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                val icon = when (toastState.actionType) {
                    InboxViewModel.ActionType.ARCHIVE -> Icons.Rounded.Archive
                    InboxViewModel.ActionType.DELETE -> Icons.Rounded.Delete
                    InboxViewModel.ActionType.EMPTY_TRASH -> Icons.Rounded.Delete
                    InboxViewModel.ActionType.SEND -> Icons.Rounded.Send
                    InboxViewModel.ActionType.SNOOZE -> Icons.Rounded.Schedule
                    InboxViewModel.ActionType.UNARCHIVE -> Icons.Rounded.Unarchive
                    InboxViewModel.ActionType.RESTORE -> Icons.Rounded.Restore
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(icon, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(
                        toastState.message, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    TextButton(
                        onClick = onUndo,
                        shape = cornerShape(24.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("Undo", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
