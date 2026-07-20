package com.shrivatsav.monomail.feature.inbox

import com.shrivatsav.monomail.model.InboxTab
import com.shrivatsav.monomail.feature.inbox.BuildConfig
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.ScheduledEmailEvent
import com.shrivatsav.monomail.SentEmailEvent
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.core.data.settings.AppSettings
import com.shrivatsav.monomail.core.data.settings.SyncFrequency
import com.shrivatsav.monomail.core.data.settings.SettingsDataStore
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
// InboxTab moved to :core:model

sealed class InboxState {
    object Loading : InboxState()
    data class Success(
        val threads: List<EmailThread>,
        val currentTab: InboxTab = InboxTab.INBOX,
        val isRefreshing: Boolean = false,
        val isLoadingMore: Boolean = false,
        val nextPageToken: String? = null
    ) : InboxState()
    data class Error(val message: String) : InboxState()
}
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InboxViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val authManager: AuthManager,
    private val settingsDataStore: SettingsDataStore,
    private val sentEmailEvents: MutableSharedFlow<SentEmailEvent>,
    val scheduledEmailEvents: MutableSharedFlow<ScheduledEmailEvent>
) : ViewModel() {
    private val _currentTab = MutableStateFlow(InboxTab.INBOX)
    val currentTab: StateFlow<InboxTab> = _currentTab.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    private val pageTokens = mutableMapOf<String, String?>()
    private fun getPageTokenKey(): String = "${_currentTab.value.name}_${currentServerQuery ?: ""}"
    private var currentServerQuery: String? = null
    private val _isLoadingMore = MutableStateFlow(false)
    private val pendingHideIdsSnapshot = mutableStateMapOf<String, Boolean>()
    private val pendingHideIds: Flow<Set<String>> = snapshotFlow {
        pendingHideIdsSnapshot.keys.toSet()
    }
    private val pendingActionJobs = mutableMapOf<String, Job>()
    private val pendingHiddenTrashIds = mutableSetOf<String>()
    private var trashOperationGeneration = 0L
    data class ToastState(
        val threadId: String,
        val message: String,
        val actionType: ActionType,
        val isPendingSend: Boolean = false
    )
    enum class ActionType { ARCHIVE, DELETE, EMPTY_TRASH, SEND, SNOOZE, UNARCHIVE, RESTORE }
    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState = _toastState.asStateFlow()
    private val _uiError = MutableSharedFlow<String>(replay = 1)
    val uiError = _uiError.asSharedFlow()
    val accounts: StateFlow<List<UserProfile>> = authManager.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _activeAccountId = MutableStateFlow(authManager.currentUser?.id)
    private val _unifiedInboxEnabled = MutableStateFlow(false)
    val unifiedInboxEnabled = _unifiedInboxEnabled.asStateFlow()
    private val _organizeByThread = MutableStateFlow(true)
    val organizeByThread = _organizeByThread.asStateFlow()
    private val _showWelcomePrompt = MutableStateFlow(false)
    val showWelcomePrompt = _showWelcomePrompt.asStateFlow()
    private val _scheduledCount = MutableStateFlow(0)
    val scheduledCount: StateFlow<Int> = _scheduledCount.asStateFlow()
    private val _isBulkSelectMode = MutableStateFlow(false)
    val isBulkSelectMode: StateFlow<Boolean> = _isBulkSelectMode.asStateFlow()
    private val _selectedThreadIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedThreadIds: StateFlow<Set<String>> = _selectedThreadIds.asStateFlow()
    val selectedCount: StateFlow<Int> = _selectedThreadIds.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    private val _lastSelectedThreadId = MutableStateFlow<String?>(null)
    val lastSelectedThreadId: StateFlow<String?> = _lastSelectedThreadId.asStateFlow()
    val animatedItemsTracker = mutableSetOf<String>()
    private val _appSettings = MutableStateFlow(AppSettings())
    val appSettingsState: StateFlow<AppSettings> = _appSettings.asStateFlow()
    private var pollingIntervalMs = 120_000L
    private var _isForegroundPollingEnabled = true
    /** When foreground and actively reading, poll at 2 min instead of the configured interval. */
    private companion object {
        private const val TAG = "InboxVM"
        private const val ADAPTIVE_POLL_MS = 2 * 60 * 1000L
    }
    private val _state = MutableStateFlow<InboxState>(InboxState.Loading)
    val state: StateFlow<InboxState> = _state.asStateFlow()
    private val _scrollPositions = MutableStateFlow<Map<InboxTab, Pair<Int, Int>>>(emptyMap())
    val scrollPositions: StateFlow<Map<InboxTab, Pair<Int, Int>>> = _scrollPositions.asStateFlow()
    private val _searchFilters = MutableStateFlow(SearchFilters())
    val searchFilters: StateFlow<SearchFilters> = _searchFilters.asStateFlow()
    private val _searchResults = MutableStateFlow<List<EmailThread>>(emptyList())
    val searchResults: StateFlow<List<EmailThread>> = _searchResults.asStateFlow()


    fun saveScrollState(tab: InboxTab, index: Int, offset: Int) {
        _scrollPositions.value = _scrollPositions.value + (tab to (index to offset))
    }

    init {
        viewModelScope.launch {
            combine(_currentTab, _activeAccountId, _unifiedInboxEnabled, _organizeByThread) { tab, accountId, unifiedEnabled, organize ->
                Quad(tab, accountId, organize, unifiedEnabled)
            }
                .flatMapLatest { (tab, accountId, organize, unifiedEnabled) ->
                    if (accountId == null) {
                        return@flatMapLatest kotlinx.coroutines.flow.flowOf(
                            InboxState.Success(emptyList(), tab)
                        )
                    }
                    val flow = if (organize) {
                        when {
                            tab == InboxTab.UNIFIED || unifiedEnabled -> {
                                when (tab) {
                                    InboxTab.UNIFIED, InboxTab.INBOX -> repository.getAllInboxThreadsFlow()
                                    InboxTab.SENT -> repository.getAllSentThreadsFlow()
                                    InboxTab.ARCHIVED -> repository.getAllArchivedThreadsFlow()
                                    InboxTab.STARRED -> repository.getAllStarredThreadsFlow()
                                    InboxTab.TRASH -> repository.getAllTrashThreadsFlow()
                                    InboxTab.SNOOZED -> repository.getAllSnoozedThreadsFlow()
                                    InboxTab.SPAM -> repository.getAllSpamThreadsFlow()
                                }
                            }
                            else -> {
                                when (tab) {
                                    InboxTab.UNIFIED -> repository.getAllInboxThreadsFlow()
                                    InboxTab.SENT -> repository.getSentThreadsFlow(accountId)
                                    InboxTab.ARCHIVED -> repository.getArchivedThreadsFlow(accountId)
                                    InboxTab.STARRED -> repository.getStarredThreadsFlow(accountId)
                                    InboxTab.TRASH -> repository.getTrashThreadsFlow(accountId)
                                    InboxTab.SNOOZED -> repository.getSnoozedThreadsFlow(accountId)
                                    InboxTab.SPAM -> repository.getSpamThreadsFlow(accountId)
                                    else -> repository.getInboxThreadsFlow(tab, accountId)
                                }
                            }
                        }
                    } else {
                        when {
                            tab == InboxTab.UNIFIED || unifiedEnabled -> {
                                when (tab) {
                                    InboxTab.UNIFIED, InboxTab.INBOX -> repository.getAllInboxEmailsFlow()
                                    InboxTab.SENT -> repository.getAllSentEmailsFlow()
                                    InboxTab.ARCHIVED -> repository.getAllArchivedEmailsFlow()
                                    InboxTab.STARRED -> repository.getAllStarredEmailsFlow()
                                    InboxTab.TRASH -> repository.getAllTrashEmailsFlow()
                                    InboxTab.SNOOZED -> repository.getInboxEmailsFlow(InboxTab.INBOX, accountId)
                                    InboxTab.SPAM -> repository.getAllSpamEmailsFlow()
                                }
                            }
                            else -> {
                                when (tab) {
                                    InboxTab.INBOX, InboxTab.UNIFIED -> repository.getInboxEmailsFlow(tab, accountId)
                                    InboxTab.SENT -> repository.getSentEmailsFlow(accountId)
                                    InboxTab.ARCHIVED -> repository.getArchivedEmailsFlow(accountId)
                                    InboxTab.STARRED -> repository.getStarredEmailsFlow(accountId)
                                    InboxTab.TRASH -> repository.getTrashEmailsFlow(accountId)
                                    InboxTab.SNOOZED -> repository.getInboxEmailsFlow(InboxTab.INBOX, accountId)
                                    InboxTab.SPAM -> repository.getSpamEmailsFlow(accountId)
                                }
                            }
                        }.map { emails ->
                            emails.groupBy { it.threadId }.map { (_, threadEmails) ->
                                val latest = threadEmails.maxBy { it.date }
                                EmailThread(
                                    threadId = latest.threadId,
                                    subject = latest.subject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), ""),
                                    from = latest.from,
                                    fromEmail = latest.fromEmail,
                                    snippet = latest.snippet,
                                    date = latest.date,
                                    messageCount = threadEmails.size,
                                    isRead = threadEmails.all { it.isRead },
                                    isStarred = threadEmails.any { it.isStarred },
                                    latestMessageId = latest.id,
                                    participants = threadEmails.map { it.from }.distinct()
                                )
                            }
                        }
                    }
                    combine(
                        flow,
                        _isRefreshing,
                        _isLoadingMore,
                        pendingHideIds
                    ) { threads, isRefreshing, isLoadingMore, hiddenIds ->
                        val filteredThreads = threads.filter { it.threadId !in hiddenIds }
                        InboxState.Success(filteredThreads, tab, isRefreshing, isLoadingMore, pageTokens[getPageTokenKey()])
                    }
                }.collect { newState ->
                    _state.value = newState
                }
        }
    }
    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
    init {
        viewModelScope.launch {
            authManager.activeAccountFlow
                .map { it?.id }
                .distinctUntilChanged()
                .collect { id -> _activeAccountId.value = id }
        }
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _appSettings.value = settings
                _unifiedInboxEnabled.value = settings.unifiedInboxEnabled
                _organizeByThread.value = settings.organizeByThread
                val isPlayStoreBuild = !BuildConfig.IS_GITHUB_BUILD
                pollingIntervalMs = if (isPlayStoreBuild) {
                    Long.MAX_VALUE
                } else {
                    when (settings.syncFrequency) {
                        SyncFrequency.MIN_15 -> 15 * 60 * 1000L
                        SyncFrequency.MIN_30 -> 30 * 60 * 1000L
                        SyncFrequency.HOUR_1 -> 60 * 60 * 1000L
                        SyncFrequency.MANUAL -> Long.MAX_VALUE
                    }
                }
                if (!settings.hasSeenWelcomePrompt && authManager.currentUser != null) {
                    _showWelcomePrompt.value = true
                }
                if (!settings.unifiedInboxEnabled && _currentTab.value == InboxTab.UNIFIED) {
                    _currentTab.value = InboxTab.INBOX
                }
            }
        }
        refresh()
        viewModelScope.launch {
            InboxTab.values().forEach { tab ->
                if (tab != _currentTab.value && tab != InboxTab.UNIFIED) {
                    try { repository.refreshInbox(tab) } catch (e: Exception) {
                        android.util.Log.e(TAG, "Background refresh failed for tab $tab", e)
                    }
                }
            }
        }
        startForegroundPolling()
        observeSentEvents()
        recoverPendingSends()
        viewModelScope.launch {
            authManager.activeAccountFlow.collect { profile ->
                val accountId = profile?.id ?: return@collect
                repository.getPendingScheduledCountFlow(accountId).collect { count ->
                    _scheduledCount.value = count
                }
            }
        }
    }

    private fun observeSentEvents() {
        viewModelScope.launch {
            sentEmailEvents.collect { event ->
                if (event.isPendingSend) {
                    val settings = settingsDataStore.settingsFlow.first()
                    startUndoCountdown(
                        toastId = event.threadId,
                        totalSec = settings.undoSendWindow.seconds,
                        isPendingSend = true
                    )
                } else {
                    val settings = settingsDataStore.settingsFlow.first()
                    if (settings.undoSendEnabled) {
                        startUndoCountdown(
                            toastId = event.threadId ?: "send_${System.currentTimeMillis()}",
                            totalSec = settings.undoSendWindow.seconds,
                            isPendingSend = false
                        )
                    }
                }
            }
        }
    }

    /** Dispatch any pending sends that survived an app crash (undo window already elapsed). */
    private fun recoverPendingSends() {
        viewModelScope.launch {
            val activeId = authManager.currentUser?.id ?: return@launch
            val pending = repository.getAllPendingSendsForAccount(activeId)
            for (send in pending) {
                repository.completePendingSend(send.id)
            }
        }
    }
    private fun startUndoCountdown(toastId: String, totalSec: Int, isPendingSend: Boolean = false) {
        _toastState.value = ToastState(
            threadId = toastId,
            message = "Message sent \u00b7 Undo for ${totalSec}s",
            actionType = ActionType.SEND,
            isPendingSend = isPendingSend
        )
        pendingActionJobs["send_$toastId"]?.cancel()
        pendingActionJobs["send_$toastId"] = viewModelScope.launch {
            for (i in totalSec downTo 1) {
                delay(1000)
                if (_toastState.value?.threadId == toastId) {
                    _toastState.value = _toastState.value?.copy(
                        message = "Message sent \u00b7 Undo for ${i}s"
                    )
                }
            }
            if (_toastState.value?.threadId == toastId) {
                _toastState.value = null
                if (isPendingSend) {
                    repository.completePendingSend(toastId)
                }
            }
        }
    }
    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            authManager.switchAccount(accountId)
            _activeAccountId.value = accountId
            refresh(showLoader = true)
        }
    }
    fun setForegroundPollingEnabled(enabled: Boolean) {
        _isForegroundPollingEnabled = enabled
    }

    private fun startForegroundPolling() {
        viewModelScope.launch {
            var lastPollMs = 0L
            while (true) {
                delay(pollingIntervalMs)
                if (pollingIntervalMs == Long.MAX_VALUE) continue
                if (!_isForegroundPollingEnabled) continue
                val effectiveInterval = computeEffectiveInterval(lastPollMs)
                if (System.currentTimeMillis() - lastPollMs >= effectiveInterval || lastPollMs == 0L) {
                    lastPollMs = System.currentTimeMillis()
                    executePoll()
                }
            }
        }
    }

    private fun computeEffectiveInterval(lastPollMs: Long): Long {
        if (pollingIntervalMs <= ADAPTIVE_POLL_MS || lastPollMs == 0L) return pollingIntervalMs
        val elapsed = System.currentTimeMillis() - lastPollMs
        return if (elapsed >= ADAPTIVE_POLL_MS) ADAPTIVE_POLL_MS else pollingIntervalMs
    }

    private suspend fun executePoll() {
        repository.refreshInbox(InboxTab.INBOX)
        if (_currentTab.value != InboxTab.INBOX && _currentTab.value != InboxTab.UNIFIED) {
            repository.refreshInbox(_currentTab.value)
        }
    }
    fun switchTab(tab: InboxTab) {
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        currentServerQuery = null
        clearSearch()
        if (_isBulkSelectMode.value) exitBulkSelectMode()
        refresh()
    }
    fun refresh(showLoader: Boolean = true) {
        viewModelScope.launch {
            if (showLoader) _isRefreshing.value = true
            val query = currentServerQuery
            val result = if (_currentTab.value == InboxTab.UNIFIED) {
                repository.refreshInbox(InboxTab.INBOX)
            } else {
                repository.refreshInbox(_currentTab.value, query = query)
            }
            result.onSuccess { token -> updatePageToken(token) }
                .onFailure { e -> _uiError.emit(e.message ?: "Failed to refresh emails") }
            if (showLoader) _isRefreshing.value = false
        }
    }

    private fun updatePageToken(token: String?) {
        if (pageTokens.size >= 50) {
            pageTokens.keys.take(10).forEach { pageTokens.remove(it) }
        }
        if (token != null) {
            pageTokens[getPageTokenKey()] = token
        } else {
            pageTokens.remove(getPageTokenKey())
        }
    }
    fun searchServer(query: String) {
        currentServerQuery = query.takeIf { it.isNotBlank() }
        refresh()
    }
    fun updateSearch(filters: SearchFilters) {
        _searchFilters.value = filters
        if (filters.query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            val accountId = _activeAccountId.value ?: return@launch
            val results = repository.searchThreads(
                query = filters.query,
                searchField = filters.searchField,
                dateFrom = filters.dateFrom,
                dateTo = filters.dateTo,
                hasAttachments = filters.hasAttachments,
                accountId = accountId
            )
            _searchResults.value = results
        }
    }
    fun clearSearch() {
        _searchFilters.value = SearchFilters()
        _searchResults.value = emptyList()
    }
    fun loadMore() {
        val key = getPageTokenKey()
        val token = pageTokens[key] ?: return
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            val result = repository.refreshInbox(_currentTab.value, pageToken = token, query = currentServerQuery)
            result.onSuccess { newToken ->
                if (newToken != null) {
                    pageTokens[key] = newToken
                } else {
                    pageTokens.remove(key)
                }
            }
            _isLoadingMore.value = false
        }
    }
    fun markAllAsRead() {
        val currentState = state.value as? InboxState.Success ?: return
        val unreadThreads = currentState.threads.filter { !it.isRead }
        if (unreadThreads.isEmpty()) return
        val ids = unreadThreads.map { it.threadId }
        _state.value = currentState.copy(
            threads = currentState.threads.map { thread ->
                if (!thread.isRead) thread.copy(isRead = true) else thread
            }
        )
        viewModelScope.launch {
            repository.markThreadsAsRead(ids).onFailure { e ->
                _uiError.emit(e.message ?: "Failed to mark emails as read")
            }
        }
    }
    fun markThreadAsRead(threadId: String) {
        viewModelScope.launch { repository.markThreadAsRead(threadId) }
    }
    fun markThreadAsUnread(threadId: String) {
        viewModelScope.launch { repository.markThreadAsUnread(threadId) }
    }
    fun archiveThread(threadId: String) {
        queueAction(threadId, ActionType.ARCHIVE, "Conversation archived")
    }
    fun deleteThread(threadId: String) {
        queueAction(threadId, ActionType.DELETE, "Conversation deleted")
    }
    fun snoozeThread(threadId: String, untilTimestamp: Long) {
        pendingHideIdsSnapshot[threadId] = true
        _toastState.value = ToastState(threadId, "Snoozed", ActionType.SNOOZE)
        pendingActionJobs[threadId]?.cancel()
        if (pendingActionJobs.size >= 30) {
            pendingActionJobs.entries.take(10).forEach { (key, job) ->
                job.cancel()
                pendingActionJobs.remove(key)
            }
        }
        pendingActionJobs[threadId] = viewModelScope.launch {
            delay(4000)
            if (_toastState.value?.threadId == threadId) {
                repository.snoozeThread(threadId, untilTimestamp)
                pendingHideIdsSnapshot.remove(threadId)
                _toastState.value = null
            }
        }
    }

    fun emptyTrash() {
        val isUnified = _currentTab.value == InboxTab.UNIFIED || _unifiedInboxEnabled.value
        viewModelScope.launch {
            try {
                repository.emptyTrash(isUnified)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "emptyTrash failed", e)
            }
        }
    }
    fun emptySpam() {
        val isUnified = _currentTab.value == InboxTab.UNIFIED || _unifiedInboxEnabled.value
        viewModelScope.launch { repository.emptySpam(isUnified) }
    }

    fun restoreThread(threadId: String) {
        viewModelScope.launch { repository.restoreThread(threadId) }
    }
    fun reportNotSpam(threadId: String) {
        viewModelScope.launch { repository.reportNotSpam(threadId) }
    }
    fun unarchiveThread(threadId: String) {
        viewModelScope.launch { repository.unarchiveThread(threadId) }
    }
    fun unsnoozeThread(threadId: String) {
        viewModelScope.launch { repository.unsnoozeThread(threadId) }
    }
    fun toggleStar(threadId: String) {
        viewModelScope.launch {
            val currentState = state.value as? InboxState.Success ?: return@launch
            val thread = currentState.threads.find { it.threadId == threadId } ?: return@launch
            repository.toggleStar(threadId, thread.isStarred)
        }
    }
    private fun queueAction(threadId: String, type: ActionType, message: String) {
        pendingHideIdsSnapshot[threadId] = true
        _toastState.value = ToastState(threadId, message, type)
        pendingActionJobs[threadId]?.cancel()
        if (pendingActionJobs.size >= 30) {
            pendingActionJobs.entries.take(10).forEach { (key, job) ->
                job.cancel()
                pendingActionJobs.remove(key)
            }
        }
        pendingActionJobs[threadId] = viewModelScope.launch {
            try {
                delay(4000)
                executeAction(threadId, type)
            } catch (e: Exception) {
                android.util.Log.w(TAG, "queueAction failed", e)
            } finally {
                if (_toastState.value?.threadId == threadId) {
                    _toastState.value = null
                }
            }
        }
    }
    private suspend fun executeAction(threadId: String, type: ActionType) {
        pendingActionJobs.remove(threadId)
        when (type) {
            ActionType.ARCHIVE -> repository.archiveThread(threadId)
            ActionType.DELETE -> repository.deleteThread(threadId)
            ActionType.EMPTY_TRASH -> repository.emptyTrash()
            ActionType.SEND -> { /* handled elsewhere */ }
            ActionType.SNOOZE -> { /* handled elsewhere */ }
            ActionType.UNARCHIVE -> repository.unarchiveThread(threadId)
            ActionType.RESTORE -> repository.restoreThread(threadId)
        }
        pendingHideIdsSnapshot.remove(threadId)
    }
    fun undoAction() {
        val currentToast = _toastState.value ?: return
        val threadId = currentToast.threadId
        when (currentToast.actionType) {
            ActionType.SEND -> {
                pendingActionJobs["send_$threadId"]?.cancel()
                pendingActionJobs.remove("send_$threadId")
                if (threadId.isNotEmpty()) {
                    viewModelScope.launch {
                        if (currentToast.isPendingSend) {
                            repository.cancelPendingSend(threadId)
                        } else {
                            repository.deleteThread(threadId)
                        }
                    }
                }
                _toastState.value = null
            }
            ActionType.EMPTY_TRASH -> {
                pendingActionJobs[threadId]?.cancel()
                pendingActionJobs.remove(threadId)
                pendingHiddenTrashIds.forEach { pendingHideIdsSnapshot.remove(it) }
                pendingHiddenTrashIds.clear()
                _toastState.value = null
            }
            else -> {
                pendingActionJobs[threadId]?.cancel()
                pendingActionJobs.remove(threadId)
                pendingHideIdsSnapshot.remove(threadId)
                _toastState.value = null
            }
        }
    }
    fun dismissToast() {
        val currentToast = _toastState.value ?: return
        val threadId = currentToast.threadId
        if (currentToast.actionType == ActionType.SEND) {
            pendingActionJobs["send_$threadId"]?.cancel()
            pendingActionJobs.remove("send_$threadId")
            _toastState.value = null
            return
        }
        pendingActionJobs[threadId]?.cancel()
        viewModelScope.launch {
            if (currentToast.actionType == ActionType.EMPTY_TRASH) {
                repository.emptyTrash()
                pendingActionJobs.remove(threadId)
                pendingHiddenTrashIds.forEach { pendingHideIdsSnapshot.remove(it) }
                pendingHiddenTrashIds.clear()
            } else {
                executeAction(threadId, currentToast.actionType)
            }
            _toastState.value = null
        }
    }
    fun dismissWelcomePrompt() {
        viewModelScope.launch {
            settingsDataStore.setHasSeenWelcomePrompt(true)
            _showWelcomePrompt.value = false
        }
    }
    fun setUnifiedInboxEnabled(enabled: Boolean) = viewModelScope.launch {
        try {
            settingsDataStore.setUnifiedInboxEnabled(enabled)
            if (enabled) {
                val allAccounts = authManager.getAccounts()
                android.util.Log.d(TAG, "Unified enabled, refreshing ${allAccounts.size} accounts")
                allAccounts.forEach { account ->
                    try {
                        val result = repository.refreshInbox(InboxTab.INBOX, accountId = account.id)
                        result.onSuccess {
                            android.util.Log.d(TAG, "Refresh OK for ${account.id}")
                        }.onFailure { e ->
                            android.util.Log.e(TAG, "Refresh FAILED for ${account.id}: ${e.message}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e(TAG, "Exception refreshing ${account.id}", e)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "setUnifiedInboxEnabled crashed", e)
        }
    }
    fun enterBulkSelectMode(threadId: String) {
        _isBulkSelectMode.value = true
        _lastSelectedThreadId.value = threadId
        _selectedThreadIds.value = setOf(threadId)
    }
    fun exitBulkSelectMode() {
        _isBulkSelectMode.value = false
        _lastSelectedThreadId.value = null
        _selectedThreadIds.value = emptySet()
    }
    fun toggleThreadSelection(threadId: String) {
        _lastSelectedThreadId.value = threadId
        _selectedThreadIds.value = _selectedThreadIds.value.let { current ->
            if (threadId in current) current - threadId else current + threadId
        }.also {
            if (it.isEmpty()) _isBulkSelectMode.value = false
        }
    }

    fun rangeSelectTo(threadId: String, orderedThreadIds: List<String>) {
        val lastId = _lastSelectedThreadId.value ?: threadId
        val fromIdx = orderedThreadIds.indexOf(lastId)
        val toIdx = orderedThreadIds.indexOf(threadId)
        if (fromIdx == -1 || toIdx == -1) {
            toggleThreadSelection(threadId)
            return
        }
        val rangeIds = orderedThreadIds.subList(
            minOf(fromIdx, toIdx), maxOf(fromIdx, toIdx) + 1
        ).toSet()
        _selectedThreadIds.value = _selectedThreadIds.value + rangeIds
        _lastSelectedThreadId.value = threadId
    }
    fun selectAll() {
        val threads = (state.value as? InboxState.Success)?.threads ?: return
        _selectedThreadIds.value = threads.map { it.threadId }.toSet()
    }
    fun deselectAll() {
        _lastSelectedThreadId.value = null
        _selectedThreadIds.value = emptySet()
        _isBulkSelectMode.value = false
    }
    fun bulkArchive() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { queueAction(it, ActionType.ARCHIVE, "Conversation archived") }
        exitBulkSelectMode()
    }
    fun bulkDelete() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { queueAction(it, ActionType.DELETE, "Conversation deleted") }
        exitBulkSelectMode()
    }
    fun bulkMarkRead() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        val currentState = _state.value
        if (currentState is InboxState.Success) {
            _state.value = currentState.copy(
                threads = currentState.threads.map { thread ->
                    if (thread.threadId in ids) thread.copy(isRead = true) else thread
                }
            )
        }
        viewModelScope.launch {
            ids.forEach { repository.markThreadAsRead(it) }
        }
        exitBulkSelectMode()
    }
    fun bulkMarkUnread() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        val currentState = _state.value
        if (currentState is InboxState.Success) {
            _state.value = currentState.copy(
                threads = currentState.threads.map { thread ->
                    if (thread.threadId in ids) thread.copy(isRead = false) else thread
                }
            )
        }
        viewModelScope.launch {
            ids.forEach { repository.markThreadAsUnread(it) }
        }
        exitBulkSelectMode()
    }
    fun bulkToggleStar() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            val currentState = state.value as? InboxState.Success ?: return@launch
            ids.forEach { id ->
                val thread = currentState.threads.find { it.threadId == id } ?: return@forEach
                repository.toggleStar(id, thread.isStarred)
            }
        }
        exitBulkSelectMode()
    }
    fun bulkUnarchive() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { queueAction(it, ActionType.UNARCHIVE, "Conversation unarchived") }
        exitBulkSelectMode()
    }
    fun bulkRestore() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        ids.forEach { queueAction(it, ActionType.RESTORE, "Conversation restored") }
        exitBulkSelectMode()
    }
    fun bulkReportNotSpam() {
        val ids = _selectedThreadIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            ids.forEach { repository.reportNotSpam(it) }
        }
        exitBulkSelectMode()
    }
}
