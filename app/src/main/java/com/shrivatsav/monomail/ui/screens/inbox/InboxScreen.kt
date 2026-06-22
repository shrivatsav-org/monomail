package com.shrivatsav.monomail.ui.screens.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    viewModel: InboxViewModel,
    userProfile: UserProfile?,
    onEmailClick: (String) -> Unit,
    onSignOut: () -> Unit,
    onCompose: () -> Unit = {},
    onSettings: () -> Unit = {},
    onAddAccount: () -> Unit = {},
    onScheduledClick: () -> Unit = {},
    onNavigateToImapSetup: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val unifiedInboxEnabled by viewModel.unifiedInboxEnabled.collectAsState()
    val showWelcomePrompt by viewModel.showWelcomePrompt.collectAsState()
    val scheduledCount by viewModel.scheduledCount.collectAsState()
    val immediateTab by viewModel.currentTab.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var threadToDelete by remember { mutableStateOf<String?>(null) }
    var showClearTrashWarning by remember { mutableStateOf(false) }
    var showClearSpamWarning by remember { mutableStateOf(false) }
    var isTrashCountdownActive by remember { mutableStateOf(false) }
    var isSpamCountdownActive by remember { mutableStateOf(false) }
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
    var activeModal by remember { mutableStateOf<ModalType?>(null) }
    var showSnoozePicker by remember { mutableStateOf(false) }
    var snoozeThreadId by remember { mutableStateOf<String?>(null) }
    val onSnoozeSelected: (String) -> Unit = remember { { id -> snoozeThreadId = id; showSnoozePicker = true } }

    val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
    LaunchedEffect(immediateTab) { listState.scrollToItem(0) }

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
        app.scheduledEmailEvents.collect { event ->
            val formatted = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                .format(java.util.Date(event.scheduledAt))
            snackbarHostState.showSnackbar(
                "Email scheduled for $formatted"
            )
        }
    }


    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(
            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
            bottom = 0.dp
        )
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
                        onSwitchAccount = { viewModel.switchAccount(it) },
                        onAddAccount = {
                            if (accounts.size >= 10) {
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar("Maximum limit of 10 accounts reached.")
                                }
                            } else activeModal = ModalType.ADD_ACCOUNT
                        },
                        onMarkAllRead = { viewModel.markAllAsRead() },
                        onStarredClick = { viewModel.switchTab(InboxTab.STARRED) },
                        onTrashClick = { viewModel.switchTab(InboxTab.TRASH) },
                        onScheduledClick = onScheduledClick,
                        isRefreshing = isRefreshing,
                        toastState = toastState,
                        onUndo = { viewModel.undoAction() },
                        onSettings = onSettings,
                        onOpenProfile = { activeModal = ModalType.PROFILE },
                        scheduledCount = scheduledCount
                    )

                    when (val s = state) {
                        is InboxState.Loading -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .semantics { contentDescription = "Loading emails" },
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        is InboxState.Error -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(32.dp)
                                ) {
                                    Text(
                                        s.message,
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
                                    ) { Text("Retry") }
                                }
                            }
                        }

                        is InboxState.Success -> {
                            val threadsToDisplay = localFilteredThreads ?: s.threads
                            val isSearchActive = localFilteredThreads != null
                            var expandedGroupsList by androidx.compose.runtime.saveable.rememberSaveable {
                                mutableStateOf(emptyList<String>())
                            }
                            var inboxStructure by remember { mutableStateOf(InboxStructure(emptyList(), emptyList())) }
                            var isComputingStructure by remember { mutableStateOf(true) }
                            LaunchedEffect(threadsToDisplay, appSettings.smartGroupingEnabled, appSettings.smartGroupingRecentOnly, currentTab) {
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
                                flattenDisplayItems(inboxStructure, expandedGroupsList.toSet())
                            }

                            PullToRefreshBox(
                                isRefreshing = s.isRefreshing,
                                onRefresh = { viewModel.refresh() },
                                state = pullToRefreshState,
                                modifier = Modifier.fillMaxSize(),
                                indicator = {}
                            ) {
                                if (threadsToDisplay.isEmpty()) {
                                    Box(
                                        Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                if (isSearchActive) "No results for \"$searchQuery\"" else "No emails",
                                                style = MaterialTheme.typography.titleSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                            if (isSearchActive) {
                                                OutlinedButton(
                                                    onClick = { viewModel.searchServer(searchQuery) },
                                                    shape = MaterialTheme.shapes.large
                                                ) { Text("Search server") }
                                            }
                                        }
                                    }
                                } else if (isComputingStructure && displayItems.isEmpty()) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 20.dp, vertical = 11.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        repeat(10) {
                                            ShimmerEmailItem()
                                        }
                                    }
                                } else {
                                    val itemKeyFn = remember { { it: InboxDisplayItem -> it.key } }
                                    LazyColumn(
                                        state = listState,
                                        modifier = Modifier
                                            .fillMaxSize(),
                                        contentPadding = PaddingValues(bottom = 100.dp)
                                    ) {
                                        items(displayItems, key = itemKeyFn) { displayItem ->
                                            when (displayItem) {
                                                is InboxDisplayItem.DateHeader -> {
                                                    Text(
                                                        text = displayItem.title,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface.copy(
                                                            alpha = 0.5f
                                                        ),
                                                        modifier = Modifier
                                                            .animateItem()
                                                            .fillMaxWidth()
                                                            .background(MaterialTheme.colorScheme.background)
                                                            .padding(
                                                                horizontal = 20.dp,
                                                                vertical = 10.dp
                                                            )
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
                                                    SwipeableEmailItem(
                                                        modifier = Modifier.animateItem(),
                                                        thread = displayItem.thread,
                                                        tabForSwipe = currentTab,
                                                        appSettings = appSettings,
                                                        viewModel = viewModel,
                                                        onThreadToDeleteChange = { threadToDelete = it },
                                                        onEmailClick = { onEmailClick(displayItem.thread.threadId) },
                                                        onLongClick = {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            longPressedThread = displayItem.thread
                                                        },
                                                        fontSizeScale = fontSizeScale,
                                                        isNested = false
                                                    )
                                                }

                                                is InboxDisplayItem.NestedThread -> {
                                                    SwipeableEmailItem(
                                                        modifier = Modifier.animateItem(),
                                                        thread = displayItem.thread,
                                                        tabForSwipe = currentTab,
                                                        appSettings = appSettings,
                                                        viewModel = viewModel,
                                                        onThreadToDeleteChange = { threadToDelete = it },
                                                        onEmailClick = { onEmailClick(displayItem.thread.threadId) },
                                                        onLongClick = {
                                                            hapticFeedback.performHapticFeedback(
                                                                HapticFeedbackType.LongPress
                                                            )
                                                            longPressedThread = displayItem.thread
                                                        },
                                                        fontSizeScale = fontSizeScale,
                                                        isNested = true
                                                    )
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
                                                        onClick = { viewModel.searchServer(searchQuery) },
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
                        }
                    }
                }
            }

            
            AnimatedVisibility(
                visible = longPressedThread == null && activeModal == null,
                enter = fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.9f),
                exit = fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.9f),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                val tabForDock by remember { derivedStateOf { immediateTab } }
                Row(
                    modifier = Modifier.padding(bottom = navBarHeight + 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    BottomDockBar(
                        currentTab = tabForDock,
                        dockConfig = appSettings.dockConfig,
                        unifiedInboxEnabled = unifiedInboxEnabled,
                        appSettings = appSettings,
                        onTabClick = { viewModel.switchTab(it) }
                    )
                    AnimatedContent(
                        targetState = when {
                            tabForDock == InboxTab.TRASH -> "trash"
                            tabForDock == InboxTab.SPAM -> "spam"
                            else -> "default"
                        },
                        label = "FabIconMorph",
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f))
                        }
                    ) { state ->
                        if (state == "trash") {
                            FloatingActionButton(
                                onClick = { showClearTrashWarning = true },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                                modifier = Modifier.height((42 * appSettings.navScale).dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "Empty Trash",
                                        modifier = Modifier.size((22 * appSettings.navScale).dp)
                                    )
                                    Text(
                                        text = "Empty",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (state == "spam") {
                            FloatingActionButton(
                                onClick = { showClearSpamWarning = true },
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                                modifier = Modifier.height((42 * appSettings.navScale).dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        Icons.Outlined.Report,
                                        contentDescription = "Empty Spam",
                                        modifier = Modifier.size((22 * appSettings.navScale).dp)
                                    )
                                    Text(
                                        text = "Empty",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        } else if (state == "default") {
                            FloatingActionButton(
                                onClick = onCompose,
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = CircleShape,
                                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp),
                                modifier = Modifier.size((52 * appSettings.navScale).dp)
                            ) {
                                Icon(Icons.Outlined.Edit, contentDescription = "Compose")
                            }
                        }
                    }
                }
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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() }
                        ) { longPressedThread = null }
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .animateEnterExit(
                                enter = scaleIn(
                                    tween(300, easing = FastOutSlowInEasing),
                                    initialScale = 0.9f
                                ),
                                exit = scaleOut(tween(200), targetScale = 0.9f)
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
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
                        Spacer(Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            tonalElevation = 0.dp,
                            shadowElevation = 8.dp
                        ) {
                            val tabForMenu =
                                (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                            ) {
                                if (tabForMenu == InboxTab.SNOOZED) {
                                    LongPressAction(
                                        icon = Icons.Outlined.Restore,
                                        label = "Unsnooze",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        onClick = {
                                            viewModel.unsnoozeThread(thread.threadId)
                                            longPressedThread = null
                                        }
                                    )
                                } else {
                                    LongPressAction(
                                        icon = if (thread.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                        label = if (thread.isStarred) "Unstar" else "Star",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        onClick = {
                                            viewModel.toggleStar(thread.threadId)
                                            longPressedThread = null
                                        }
                                    )
                                }
                                LongPressAction(
                                    icon = if (tabForMenu == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                    label = when (tabForMenu) {
                                        InboxTab.ARCHIVED -> "Unarchive"
                                        InboxTab.SPAM -> "Not spam"
                                        else -> "Archive"
                                    },
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        if (tabForMenu == InboxTab.ARCHIVED) viewModel.unarchiveThread(
                                            thread.threadId
                                        )
                                        else if (tabForMenu == InboxTab.SPAM) {
                                            viewModel.reportNotSpam(thread.threadId)
                                        } else viewModel.archiveThread(thread.threadId)
                                        longPressedThread = null
                                    }
                                )
                                LongPressAction(
                                    icon = if (thread.isRead) Icons.Outlined.MarkEmailUnread else Icons.Outlined.CheckCircle,
                                    label = if (thread.isRead) "Unread" else "Read",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        if (thread.isRead) viewModel.markThreadAsUnread(thread.threadId)
                                        else viewModel.markThreadAsRead(thread.threadId)
                                        longPressedThread = null
                                    }
                                )
                                if (tabForMenu != InboxTab.TRASH && tabForMenu != InboxTab.SNOOZED && tabForMenu != InboxTab.SPAM) {
                                    LongPressAction(
                                        icon = Icons.Outlined.Schedule,
                                        label = "Snooze",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        onClick = {
                                            longPressedThread = null
                                            onSnoozeSelected(thread.threadId)
                                        }
                                    )
                                }
                                LongPressAction(
                                    icon = if (tabForMenu == InboxTab.TRASH) Icons.Outlined.Restore else Icons.Outlined.Delete,
                                    label = if (tabForMenu == InboxTab.TRASH) "Restore" else "Delete",
                                    tint = if (tabForMenu == InboxTab.TRASH) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.error,
                                    onClick = {
                                        if (tabForMenu == InboxTab.TRASH) viewModel.restoreThread(thread.threadId)
                                        else threadToDelete = thread.threadId
                                        longPressedThread = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            
            val accounts by viewModel.accounts.collectAsState()
            ModalOverlay(
                activeModal = activeModal,
                userProfile = userProfile,
                accounts = accounts,
                onDismiss = { activeModal = null },
                onSignOut = { activeModal = null; onSignOut() },
                onSwitchAccount = { viewModel.switchAccount(it); activeModal = null },
                onCycleAccount = { viewModel.switchAccount(it) },
                onAddAccount = {
                    if (accounts.size >= 10) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Maximum limit of 10 accounts reached.")
                        }
                    } else activeModal = ModalType.ADD_ACCOUNT
                },
                onShowSwitchAccount = { activeModal = ModalType.SWITCH_ACCOUNT },
                onBackToProfile = { activeModal = ModalType.PROFILE },
                onSettings = { activeModal = null; onSettings() },
                onNavigateToImapSetup = onNavigateToImapSetup
            )
        }
    }

    
    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showWelcomePrompt,
        onDismiss = { viewModel.dismissWelcomePrompt() }
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
            val context = androidx.compose.ui.platform.LocalContext.current

            val kofiIcon = remember {
                val bmp = android.graphics.BitmapFactory.decodeStream(
                    context.resources.openRawResource(com.shrivatsav.monomail.R.raw.kofi)
                )
                if (bmp != null) androidx.compose.ui.graphics.painter.BitmapPainter(bmp.asImageBitmap())
                else null
            }

            Column(modifier = Modifier.padding(24.dp)) {

                // Header
                Text(
                    text = "Welcome to Monomail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Monomail is free & open-source, built with privacy in mind. If you find it useful, here's how you can help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Primary 2x2 support grid — card style, icon-on-top
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
                            Icon(Icons.Outlined.FavoriteBorder, contentDescription = null, modifier = Modifier.size(22.dp))
                        }
                    }
                    SupportCard(
                        modifier = Modifier.weight(1f),
                        label = "Pay with UPI",
                        onClick = { uriHandler.openUri("upi://pay?pa=shrivatsav@slc&pn=Sharan%20Shrivatsav&mode=02") }
                    ) {
                        Icon(Icons.Outlined.Payments, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SupportCard(
                        modifier = Modifier.weight(1f),
                        label = "Star on GitHub",
                        onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail") }
                    ) {
                        Icon(Icons.Outlined.StarOutline, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                    SupportCard(
                        modifier = Modifier.weight(1f),
                        label = "Join Discord",
                        onClick = { uriHandler.openUri("https://discord.gg/tZgpycdm") }
                    ) {
                        Icon(Icons.Outlined.HeadsetMic, contentDescription = null, modifier = Modifier.size(22.dp))
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Spacer(modifier = Modifier.height(14.dp))

                // Secondary actions — icon-only row, tooltip-style
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    SupportIconAction(
                        icon = Icons.Outlined.Share,
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
                    SupportIconAction(
                        icon = Icons.Outlined.BugReport,
                        contentDescription = "Report Issue",
                        onClick = { uriHandler.openUri("https://github.com/shrivatsav-0/monomail/issues") }
                    )
                    SupportIconAction(
                        icon = Icons.Outlined.AccountBalanceWallet,
                        contentDescription = "Donate Crypto (BASE)",
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Crypto Address", "0xB27Ba9241de81F6DBCB322aDd76a9d9686462e9E"))
                            android.widget.Toast.makeText(context, "Address copied to clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.dismissWelcomePrompt() }) {
                        Text("Get Started")
                    }
                }
            }
        }
    }

    
    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = threadToDelete != null,
        onDismiss = { threadToDelete = null }
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
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
        var trashCountdown by remember { mutableIntStateOf(5) }
        LaunchedEffect(showClearTrashWarning) {
            if (showClearTrashWarning) {
                trashCountdown = 5
                isTrashCountdownActive = true
                while (trashCountdown > 0 && isTrashCountdownActive) {
                    kotlinx.coroutines.delay(1000)
                    trashCountdown--
                }
                if (isTrashCountdownActive) {
                    viewModel.emptyTrash()
                    showClearTrashWarning = false
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Clear Trash?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Are you sure you want to permanently delete all messages in the trash? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = if (trashCountdown > 0) "Clearing trash in $trashCountdown..."
                               else "Clearing trash...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        isTrashCountdownActive = false
                        showClearTrashWarning = false
                    }) {
                        Text("Cancel")
                    }
                }
            }
        }
    }

    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showClearSpamWarning,
        onDismiss = { showClearSpamWarning = false; isSpamCountdownActive = false }
    ) {
        var spamCountdown by remember { mutableIntStateOf(5) }
        LaunchedEffect(showClearSpamWarning) {
            if (showClearSpamWarning) {
                spamCountdown = 5
                isSpamCountdownActive = true
                while (spamCountdown > 0 && isSpamCountdownActive) {
                    kotlinx.coroutines.delay(1000)
                    spamCountdown--
                }
                if (isSpamCountdownActive) {
                    viewModel.emptySpam()
                    showClearSpamWarning = false
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            shadowElevation = 32.dp,
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Clear Spam?",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Are you sure you want to permanently delete all messages in spam? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = if (spamCountdown > 0) "Clearing spam in $spamCountdown..."
                               else "Clearing spam...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        isSpamCountdownActive = false
                        showClearSpamWarning = false
                    }) {
                        Text("Cancel")
                    }
                }
            }
        }
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
            shape = RoundedCornerShape(28.dp),
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
                        shape = RoundedCornerShape(12.dp)
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
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
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

/**
 * Secondary support action — icon-only tap target with a long-press
 * tooltip for the label. Lower visual priority than SupportCard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupportIconAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(contentDescription) } },
        state = rememberTooltipState()
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
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
            .clip(RoundedCornerShape(12.dp))
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
            imageVector = Icons.Outlined.ExpandMore,
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
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.5f))
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color.copy(alpha = 0.3f))
            )
        }
        Spacer(Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(12.dp)
                .clip(RoundedCornerShape(4.dp))
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
        shape = RoundedCornerShape(14.dp),
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
