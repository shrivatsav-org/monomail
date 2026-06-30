package com.shrivatsav.monomail.ui.screens.inbox
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.ScheduledEmailEvent
import com.shrivatsav.monomail.SentEmailEvent
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.AppSettings
import com.shrivatsav.monomail.data.settings.SyncFrequency
import com.shrivatsav.monomail.data.settings.SettingsDataStore
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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import javax.inject.Inject
enum class InboxTab { INBOX, SENT, ARCHIVED, STARRED, TRASH, UNIFIED, SNOOZED, SPAM }
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
    private val contactProvider: ContactSuggestionProvider,
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
    data class ToastState(
        val threadId: String,
        val message: String,
        val actionType: ActionType
    )
    enum class ActionType { ARCHIVE, DELETE, EMPTY_TRASH, SEND, SNOOZE }
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
    val settingsFlow = settingsDataStore.settingsFlow
    private var pollingIntervalMs = 120_000L
    private val _state = MutableStateFlow<InboxState>(InboxState.Loading)
    val state: StateFlow<InboxState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(_currentTab, _activeAccountId, _unifiedInboxEnabled, _organizeByThread) { tab, accountId, unifiedEnabled, organize ->
                Quad(tab, accountId, organize, unifiedEnabled)
            }
                .flatMapLatest { (tab, accountId, organize, _) ->
                    if (accountId == null) {
                        return@flatMapLatest kotlinx.coroutines.flow.flowOf(
                            InboxState.Success(emptyList(), tab)
                        )
                    }
                    val flow = if (organize) {
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
                    } else {
                        when (tab) {
                            InboxTab.UNIFIED -> repository.getInboxEmailsFlow(tab, accountId)
                            InboxTab.SENT -> repository.getSentEmailsFlow(accountId)
                            InboxTab.ARCHIVED -> repository.getArchivedEmailsFlow(accountId)
                            InboxTab.STARRED -> repository.getStarredEmailsFlow(accountId)
                            InboxTab.TRASH -> repository.getTrashEmailsFlow(accountId)
                            InboxTab.SNOOZED -> repository.getInboxEmailsFlow(InboxTab.INBOX, accountId)
                            InboxTab.SPAM -> repository.getSpamEmailsFlow(accountId)
                            else -> repository.getInboxEmailsFlow(tab, accountId)
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
                _unifiedInboxEnabled.value = settings.unifiedInboxEnabled
                _organizeByThread.value = settings.organizeByThread
                pollingIntervalMs = when (settings.syncFrequency) {
                    SyncFrequency.MIN_15 -> 15 * 60 * 1000L
                    SyncFrequency.MIN_30 -> 30 * 60 * 1000L
                    SyncFrequency.HOUR_1 -> 60 * 60 * 1000L
                    SyncFrequency.MANUAL -> Long.MAX_VALUE
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
                    repository.refreshInbox(tab)
                }
            }
        }
        startForegroundPolling()
        observeSentEvents()
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
            combine(sentEmailEvents, settingsDataStore.settingsFlow) { event, settings ->
                Pair(event, settings)
            }.collect { (event, settings) ->
                val toastId = event.threadId ?: "send_${System.currentTimeMillis()}"
                if (!settings.undoSendEnabled) return@collect
                val totalSec = settings.undoSendWindow.seconds
                _toastState.value = ToastState(
                    threadId = toastId,
                    message = "Message sent \u00b7 Undo for ${totalSec}s",
                    actionType = ActionType.SEND
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
                    delay(1000)
                    if (_toastState.value?.threadId == toastId) {
                        _toastState.value = null
                    }
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
    private fun startForegroundPolling() {
        viewModelScope.launch {
            while (true) {
                delay(pollingIntervalMs)
                if (pollingIntervalMs == Long.MAX_VALUE) continue
                repository.refreshInbox(InboxTab.INBOX)
                if (_currentTab.value != InboxTab.INBOX && _currentTab.value != InboxTab.UNIFIED) {
                    repository.refreshInbox(_currentTab.value)
                }
            }
        }
    }
    fun switchTab(tab: InboxTab) {
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        currentServerQuery = null
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
            result.onSuccess { token ->
                if (token != null) {
                    pageTokens[getPageTokenKey()] = token
                } else {
                    pageTokens.remove(getPageTokenKey())
                }
            }.onFailure { e ->
                val msg = e.message ?: "Failed to refresh emails"
                if (msg.contains("sign in", ignoreCase = true) || msg.contains("Session expired", ignoreCase = true)) {
                    _uiError.emit(msg)
                } else {
                    _uiError.emit(msg)
                }
            }
            if (showLoader) _isRefreshing.value = false
        }
    }
    fun searchServer(query: String) {
        currentServerQuery = query.takeIf { it.isNotBlank() }
        refresh()
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
        pendingActionJobs[threadId] = viewModelScope.launch {
            delay(4000)
            if (_toastState.value?.threadId == threadId) {
                pendingHideIdsSnapshot.remove(threadId)
                repository.snoozeThread(threadId, untilTimestamp)
                _toastState.value = null
            }
        }
    }

    fun emptyTrash() {
        val currentState = state.value as? InboxState.Success ?: return
        val trashIds = currentState.threads.map { it.threadId }.toSet()
        if (trashIds.isEmpty()) return

        val sentinelId = "empty_trash"
        pendingHiddenTrashIds.clear()
        pendingHiddenTrashIds.addAll(trashIds)
        trashIds.forEach { pendingHideIdsSnapshot[it] = true }
        _toastState.value = ToastState(sentinelId, "Trash emptied", ActionType.EMPTY_TRASH)

        pendingActionJobs[sentinelId]?.cancel()
        pendingActionJobs[sentinelId] = viewModelScope.launch {
            try {
                delay(4000)
                if (_toastState.value?.threadId == sentinelId) {
                    repository.emptyTrash()
                }
            } catch (e: Exception) {
            } finally {
                if (_toastState.value?.threadId == sentinelId) {
                    _toastState.value = null
                    pendingHiddenTrashIds.forEach { pendingHideIdsSnapshot.remove(it) }
                    pendingHiddenTrashIds.clear()
                    pendingActionJobs.remove(sentinelId)
                }
            }
        }
    }
    fun emptySpam() {
        viewModelScope.launch { repository.emptySpam() }
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
        pendingActionJobs[threadId] = viewModelScope.launch {
            try {
                delay(4000)
                executeAction(threadId, type)
            } catch (e: Exception) {
            } finally {
                if (_toastState.value?.threadId == threadId) {
                    _toastState.value = null
                }
            }
        }
    }
    private suspend fun executeAction(threadId: String, type: ActionType) {
        pendingHideIdsSnapshot.remove(threadId)
        pendingActionJobs.remove(threadId)
        when (type) {
            ActionType.ARCHIVE -> repository.archiveThread(threadId)
            ActionType.DELETE -> repository.deleteThread(threadId)
            ActionType.EMPTY_TRASH -> repository.emptyTrash()
            ActionType.SEND -> {}
            ActionType.SNOOZE -> {}
        }
    }
    fun undoAction() {
        val currentToast = _toastState.value ?: return
        val threadId = currentToast.threadId
        when (currentToast.actionType) {
            ActionType.SEND -> {
                pendingActionJobs["send_$threadId"]?.cancel()
                pendingActionJobs.remove("send_$threadId")
                if (threadId.isNotEmpty()) {
                    viewModelScope.launch { repository.deleteThread(threadId) }
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
    fun enterBulkSelectMode(threadId: String) {
        _isBulkSelectMode.value = true
        _selectedThreadIds.value = setOf(threadId)
    }
    fun exitBulkSelectMode() {
        _isBulkSelectMode.value = false
        _selectedThreadIds.value = emptySet()
    }
    fun toggleThreadSelection(threadId: String) {
        _selectedThreadIds.value = _selectedThreadIds.value.let { current ->
            if (threadId in current) current - threadId else current + threadId
        }.also {
            if (it.isEmpty()) _isBulkSelectMode.value = false
        }
    }
    fun selectAll() {
        val threads = (state.value as? InboxState.Success)?.threads ?: return
        _selectedThreadIds.value = threads.map { it.threadId }.toSet()
    }
    fun deselectAll() {
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
}
