package com.shrivatsav.monomail.ui.screens.inbox

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import coil.compose.AsyncImage
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class ModalType { PROFILE, SWITCH_ACCOUNT, ADD_ACCOUNT }

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
     val showDonationPrompt by viewModel.showDonationPrompt.collectAsState()
    val immediateTab by viewModel.currentTab.collectAsState()

    val context = androidx.compose.ui.platform.LocalContext.current
    var threadToDelete by remember { mutableStateOf<String?>(null) }
    var showClearTrashWarning by remember { mutableStateOf(false) }
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

    val currentTab = (state as? InboxState.Success)?.currentTab ?: InboxTab.INBOX
    LaunchedEffect(currentTab) { listState.scrollToItem(0) }

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
                longPressedThread != null || blurForModal || threadToDelete != null || showDonationPrompt || showClearTrashWarning

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
                        isRefreshing = isRefreshing,
                        toastState = toastState,
                        onUndo = { viewModel.undoAction() },
                        onSettings = onSettings,
                        onOpenProfile = { activeModal = ModalType.PROFILE }
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
                            LaunchedEffect(threadsToDisplay, appSettings.smartGroupingEnabled, appSettings.smartGroupingRecentOnly, currentTab) {
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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 4.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val onUnifiedClick = remember(viewModel) { { viewModel.switchTab(InboxTab.UNIFIED) } }
                            val onInboxClick = remember(viewModel) { { viewModel.switchTab(InboxTab.INBOX) } }
                            val onSentClick = remember(viewModel) { { viewModel.switchTab(InboxTab.SENT) } }
                            val onArchiveClick = remember(viewModel) { { viewModel.switchTab(InboxTab.ARCHIVED) } }
                            if (unifiedInboxEnabled) {
                                DockTab(
                                    isActive = tabForDock == InboxTab.UNIFIED,
                                    icon = Icons.Outlined.Inbox,
                                    label = "Unified",
                                    contentDescription = "Unified Inbox",
                                    onClick = onUnifiedClick,
                                    scale = appSettings.navScale
                                )
                            }
                            DockTab(
                                isActive = tabForDock == InboxTab.INBOX,
                                icon = if (unifiedInboxEnabled) Icons.Outlined.AccountCircle else Icons.Outlined.Inbox,
                                label = "Inbox",
                                contentDescription = "Primary Inbox",
                                onClick = onInboxClick,
                                scale = appSettings.navScale
                            )
                            DockTab(
                                isActive = tabForDock == InboxTab.SENT,
                                icon = Icons.AutoMirrored.Outlined.Send,
                                label = "Sent",
                                contentDescription = "Sent",
                                onClick = onSentClick,
                                scale = appSettings.navScale
                            )
                            DockTab(
                                isActive = tabForDock == InboxTab.ARCHIVED,
                                icon = Icons.Outlined.Archive,
                                label = "Archived",
                                contentDescription = "Archived",
                                onClick = onArchiveClick,
                                scale = appSettings.navScale
                            )
                        }
                    }
                    AnimatedContent(
                        targetState = tabForDock == InboxTab.TRASH,
                        label = "FabIconMorph",
                        transitionSpec = {
                            (fadeIn(tween(180)) + scaleIn(tween(180), initialScale = 0.85f)) togetherWith
                                    (fadeOut(tween(120)) + scaleOut(tween(120), targetScale = 0.85f))
                        }
                    ) { isTrash ->
                        if (isTrash) {
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
                        } else {
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
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LongPressAction(
                                    icon = if (thread.isStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                    label = if (thread.isStarred) "Unstar" else "Star",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        viewModel.toggleStar(thread.threadId)
                                        longPressedThread = null
                                    }
                                )
                                LongPressAction(
                                    icon = if (tabForMenu == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                    label = if (tabForMenu == InboxTab.ARCHIVED) "Unarchive" else "Archive",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        if (tabForMenu == InboxTab.ARCHIVED) viewModel.unarchiveThread(
                                            thread.threadId
                                        )
                                        else viewModel.archiveThread(thread.threadId)
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
                onAddAccount = {
                    if (accounts.size >= 10) {
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Maximum limit of 10 accounts reached.")
                        }
                    } else activeModal = ModalType.ADD_ACCOUNT
                },
                onShowSwitchAccount = { activeModal = ModalType.SWITCH_ACCOUNT },
                onBackToProfile = { activeModal = ModalType.PROFILE },
                onTrashClick = { activeModal = null; viewModel.switchTab(InboxTab.TRASH) },
                onStarredClick = { activeModal = null; viewModel.switchTab(InboxTab.STARRED) },
                onSettings = { activeModal = null; onSettings() },
            )
        }
    }

    
    com.shrivatsav.monomail.ui.components.BlurredModalOverlay(
        visible = showDonationPrompt,
        onDismiss = { viewModel.dismissDonationPrompt() }
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
                    text = "Support Monomail",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "If you want to support this app please buy me a coffee! I am a student working on this as my side project contributing to OSS, it would be a great help.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
                    AsyncImage(
                        model = "https://storage.ko-fi.com/cdn/kofi3.png?v=6",
                        contentDescription = "Buy Me a Coffee",
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .wrapContentHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { uriHandler.openUri("https://ko-fi.com/N4N2W53M5") }
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { viewModel.dismissDonationPrompt() }) {
                        Text("Close")
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
        onDismiss = { showClearTrashWarning = false }
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
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { showClearTrashWarning = false }) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.emptyTrash()
                            showClearTrashWarning = false
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Clear Trash")
                    }
                }
            }
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
private fun ModalOverlay(
    activeModal: ModalType?,
    userProfile: UserProfile?,
    accounts: List<UserProfile>,
    onDismiss: () -> Unit,
    onSignOut: () -> Unit,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onShowSwitchAccount: () -> Unit,
    onBackToProfile: () -> Unit,
    onTrashClick: () -> Unit,
    onStarredClick: () -> Unit,
    onSettings: () -> Unit,
) {
    var displayed by remember { mutableStateOf<ModalType?>(null) }
    displayed = activeModal ?: displayed

    if (activeModal != null) {
        BackHandler {
            when (activeModal) {
                ModalType.SWITCH_ACCOUNT -> onBackToProfile()
                ModalType.PROFILE -> onDismiss()
                ModalType.ADD_ACCOUNT -> onDismiss()
            }
        }
    }

    AnimatedVisibility(
        visible = activeModal != null,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(180)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.5f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = displayed,
                transitionSpec = {
                    if (initialState == null) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (scaleIn(
                            tween(200, easing = FastOutSlowInEasing),
                            initialScale = 0.85f
                        ) + fadeIn(tween(200))) togetherWith
                                (scaleOut(tween(200), targetScale = 0.85f) +
                                        fadeOut(tween(180)))
                    }
                },
                label = "ModalContent"
            ) { modal ->
                Box(
                    modifier = Modifier.clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {},
                    contentAlignment = Alignment.Center
                ) {
                    when (modal) {
                        ModalType.ADD_ACCOUNT -> {
                            val ctx = androidx.compose.ui.platform.LocalContext.current
                            val a = ctx.applicationContext as com.shrivatsav.monomail.MonoMailApp
                            val vm: com.shrivatsav.monomail.ui.screens.auth.SignInViewModel =
                                androidx.lifecycle.viewmodel.compose.viewModel(
                                    factory = object :
                                        androidx.lifecycle.ViewModelProvider.Factory {
                                        @Suppress("UNCHECKED_CAST")
                                        override fun <T : androidx.lifecycle.ViewModel> create(
                                            modelClass: Class<T>
                                        ): T =
                                            com.shrivatsav.monomail.ui.screens.auth.SignInViewModel(
                                                a.authManager
                                            ) as T
                                    }
                                )
                            com.shrivatsav.monomail.ui.screens.auth.ProviderSelectionDialog(
                                viewModel = vm,
                                onDismiss = { onDismiss() },
                                onSuccess = { onDismiss() }
                            )
                        }

                        ModalType.PROFILE -> {
                            if (userProfile != null) {
                                ProfileCard(
                                    userProfile = userProfile,
                                    accounts = accounts,
                                    onSignOut = onSignOut,
                                    onShowSwitchAccount = onShowSwitchAccount,
                                    onTrashClick = onTrashClick,
                                    onStarredClick = onStarredClick,
                                    onSettings = onSettings,
                                    onAddAccount = onAddAccount,
                                )
                            }
                        }

                        ModalType.SWITCH_ACCOUNT -> {
                            if (userProfile != null) {
                                SwitchAccountCard(
                                    userProfile = userProfile,
                                    accounts = accounts,
                                    onSwitchAccount = onSwitchAccount,
                                    onAddAccount = onAddAccount,
                                    onBack = onBackToProfile,
                                )
                            }
                        }

                        null -> {}
                    }
                }
            }
        }
    }
}





@Composable
private fun ProfileCard(
    userProfile: UserProfile,
    accounts: List<UserProfile>,
    onSignOut: () -> Unit,
    onShowSwitchAccount: () -> Unit,
    onTrashClick: () -> Unit,
    onStarredClick: () -> Unit,
    onSettings: () -> Unit,
    onAddAccount: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 32.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 28.dp, bottom = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (accounts.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy((-16).dp),
                            modifier = Modifier.offset(x = 28.dp)
                        ) {
                            accounts.filter { it.id != userProfile.id }.take(2).forEach { acc ->
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                                        .clip(CircleShape)
                                        .alpha(0.45f)
                                ) {
                                    AvatarCircle(
                                        acc.photoUrl,
                                        acc.displayName,
                                        44.dp,
                                        MaterialTheme.typography.titleSmall
                                    )
                                }
                            }
                        }
                    }
                    Box(modifier = Modifier.border(3.dp, MaterialTheme.colorScheme.background, CircleShape)) {
                        AvatarCircle(
                            photoUrl = userProfile.photoUrl,
                            displayName = userProfile.displayName,
                            size = 72.dp,
                            textStyle = MaterialTheme.typography.headlineSmall
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = userProfile.displayName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = userProfile.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )

                if (accounts.size > 1) {
                    Spacer(Modifier.height(12.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                        modifier = Modifier.clickable { onShowSwitchAccount() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "${accounts.size} accounts",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            )
                            Icon(
                                Icons.Default.KeyboardArrowDown,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                thickness = 0.5.dp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            ) {
                ProfileMenuItem(Icons.Outlined.Star, "Starred", onStarredClick)
                ProfileMenuItem(Icons.Outlined.Delete, "Trash", onTrashClick)
                ProfileMenuItem(Icons.Outlined.Settings, "Settings", onSettings)
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                thickness = 0.5.dp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.onBackground
                    ),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.15f)
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Text(
                        "Sign out",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Button(
                    onClick = onAddAccount,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Add account",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}





@Composable
private fun SwitchAccountCard(
    userProfile: UserProfile,
    accounts: List<UserProfile>,
    onSwitchAccount: (String) -> Unit,
    onAddAccount: () -> Unit,
    onBack: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(0.88f),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 32.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = "Accounts",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                accounts.forEach { account ->
                    val isCurrent = account.id == userProfile.id
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                if (isCurrent) MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f)
                                else Color.Transparent
                            )
                            .clickable { if (!isCurrent) onSwitchAccount(account.id) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AvatarCircle(
                            photoUrl = account.photoUrl,
                            displayName = account.displayName,
                            size = 40.dp,
                            textStyle = MaterialTheme.typography.titleSmall
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = account.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onBackground,
                                maxLines = 1
                            )
                            Text(
                                text = account.email,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(MaterialTheme.colorScheme.onBackground, CircleShape)
                            )
                        }
                    }
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f),
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onAddAccount)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "Add account",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(6.dp))
        }
    }
}





@Composable
fun AvatarCircle(
    photoUrl: String?,
    displayName: String,
    size: Dp,
    textStyle: androidx.compose.ui.text.TextStyle,
) {
    if (!photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = displayName,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onBackground),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = displayName.firstOrNull()?.uppercase() ?: "?",
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.background
            )
        }
    }
}





@Composable
private fun ProfileMenuItem(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}





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
    onSettings: () -> Unit = {},
    onOpenProfile: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (toastState != null)
            MaterialTheme.colorScheme.secondaryContainer
        else
            MaterialTheme.colorScheme.surfaceContainer,
        animationSpec = tween(300),
        label = "SearchBarColor"
    )

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
                                        modifier = Modifier.pointerInput(accounts, userProfile) {
                                            detectVerticalDragGestures { change, dragAmount ->
                                                change.consume()
                                                if (Math.abs(dragAmount) > 10f && accounts.size > 1 && userProfile != null) {
                                                    val idx =
                                                        accounts.indexOfFirst { it.id == userProfile.id }
                                                    if (idx != -1) {
                                                        val newIdx = if (dragAmount > 0)
                                                            (idx + 1) % accounts.size
                                                        else
                                                            if (idx - 1 < 0) accounts.size - 1 else idx - 1
                                                        onSwitchAccount(accounts[newIdx].id)
                                                    }
                                                }
                                            }
                                        },
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
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = SearchBarDefaults.colors(containerColor = containerColor),
        shape = MaterialTheme.shapes.extraLarge,
        windowInsets = WindowInsets(0.dp)
    ) {}
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





@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableEmailItem(
    modifier: Modifier = Modifier,
    thread: EmailThread,
    tabForSwipe: InboxTab,
    appSettings: com.shrivatsav.monomail.data.settings.AppSettings,
    viewModel: InboxViewModel,
    onThreadToDeleteChange: (String?) -> Unit,
    onEmailClick: () -> Unit,
    onLongClick: () -> Unit,
    fontSizeScale: Float,
    isNested: Boolean = false
) {
    var optIsRead by remember(thread.isRead) { mutableStateOf(thread.isRead) }
    var optIsStarred by remember(thread.isStarred) { mutableStateOf(thread.isStarred) }

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
                                if (tabForSwipe == InboxTab.ARCHIVED) Icons.Outlined.Inbox else Icons.Outlined.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.STAR ->
                            Icon(
                                if (optIsStarred) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.DELETE ->
                            Icon(
                                Icons.Outlined.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        com.shrivatsav.monomail.data.settings.SwipeAction.READ_UNREAD ->
                            Icon(
                                if (optIsRead) Icons.Outlined.MarkEmailRead else Icons.Outlined.MarkEmailUnread,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        else -> {}
                    }
                }
            }
        ) {
            EmailItem(
                thread = thread.copy(isRead = optIsRead, isStarred = optIsStarred),
                onClick = onEmailClick,
                onLongClick = onLongClick,
                showSnippet = appSettings.showSnippet,
                compactMode = appSettings.compactList,
                fontSizeScale = fontSizeScale
            )
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
@Composable
private fun DockTab(
    isActive: Boolean,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    scale: Float
) {
    AnimatedDockTab(isActive, icon, label, contentDescription, onClick, scale)
}

@Composable
private fun AnimatedDockTab(
    isActive: Boolean,
    icon: ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    scale: Float,
) {
    val transition = updateTransition(targetState = isActive, label = "dockTab")
    val bgColor by transition.animateColor(
        transitionSpec = { tween(200) },
        label = "dockTabBg"
    ) { active -> if (active) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent }
    val contentColor by transition.animateColor(
        transitionSpec = { tween(200) },
        label = "dockTabContent"
    ) { active -> if (active) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) }

    Surface(
        shape = CircleShape,
        color = bgColor,
        contentColor = contentColor,
        onClick = onClick,
        modifier = Modifier
            .height((42 * scale).dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = (12 * scale).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size((22 * scale).dp)
            )
            val labelWidth by transition.animateDp(
                transitionSpec = { tween(220, easing = FastOutSlowInEasing) },
                label = "dockLabelWidth"
            ) { active -> if (active) 72.dp else 0.dp }
            val labelAlpha by transition.animateFloat(
                transitionSpec = { tween(180) },
                label = "dockLabelAlpha"
            ) { active -> if (active) 1f else 0f }

            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = labelAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = labelWidth)
            )
        }
    }
}
