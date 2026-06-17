package com.shrivatsav.monomail.ui.screens.inbox
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
enum class InboxTab { INBOX, SENT, ARCHIVED, STARRED, TRASH, UNIFIED }
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
class InboxViewModel(
    private val repository: EmailRepository,
    private val contactProvider: ContactSuggestionProvider,
    private val authManager: AuthManager,
    private val settingsDataStore: SettingsDataStore
) : ViewModel() {
    private val _currentTab = MutableStateFlow(InboxTab.INBOX)
    val currentTab: StateFlow<InboxTab> = _currentTab.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    private val pageTokens = mutableMapOf<String, String?>()
    private fun getPageTokenKey(): String = "${_currentTab.value.name}_${currentServerQuery ?: ""}"
    private var currentServerQuery: String? = null
    private val _isLoadingMore = MutableStateFlow(false)
    private val pendingHideIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingActionJobs = mutableMapOf<String, Job>()
    data class ToastState(
        val threadId: String,
        val message: String,
        val actionType: ActionType
    )
    enum class ActionType { ARCHIVE, DELETE }
    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState = _toastState.asStateFlow()
    private val _uiError = MutableSharedFlow<String>()
    val uiError = _uiError.asSharedFlow()
    val accounts: StateFlow<List<UserProfile>> = authManager.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _activeAccountId = MutableStateFlow<String?>(null)
    private val _unifiedInboxEnabled = MutableStateFlow(false)
    val unifiedInboxEnabled = _unifiedInboxEnabled.asStateFlow()
    private val _organizeByThread = MutableStateFlow(true)
    val organizeByThread = _organizeByThread.asStateFlow()
    private val _showDonationPrompt = MutableStateFlow(false)
    val showDonationPrompt = _showDonationPrompt.asStateFlow()
    val state: StateFlow<InboxState> = combine(_currentTab, _activeAccountId, _unifiedInboxEnabled, _organizeByThread) { tab, _, _, organize -> Pair(tab, organize) }
        .flatMapLatest { (tab, organize) ->
        val flow = if (organize) {
            if (tab == InboxTab.UNIFIED) repository.getAllInboxThreadsFlow() else repository.getInboxThreadsFlow(tab)
        } else {
            repository.getInboxEmailsFlow(tab).map { emails ->
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
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxState.Loading
    )
    init {
        viewModelScope.launch {
            authManager.activeAccountFlow.collect { profile ->
                _activeAccountId.value = profile?.id
            }
        }
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                _unifiedInboxEnabled.value = settings.unifiedInboxEnabled
                _organizeByThread.value = settings.organizeByThread
                if (!settings.hasSeenDonationPrompt && authManager.currentUser != null) {
                    _showDonationPrompt.value = true
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
                delay(60_000) 
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
            }.onFailure {
                _uiError.emit(it.message ?: "Failed to refresh emails")
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
        viewModelScope.launch {
            val currentState = state.value as? InboxState.Success ?: return@launch
            if (currentState.currentTab != InboxTab.INBOX) return@launch
            val unreadThreads = currentState.threads.filter { !it.isRead }
            if (unreadThreads.isEmpty()) return@launch
            unreadThreads.forEach { thread ->
                repository.markThreadAsRead(thread.threadId)
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
    fun restoreThread(threadId: String) {
        viewModelScope.launch { repository.restoreThread(threadId) }
    }
    fun unarchiveThread(threadId: String) {
        viewModelScope.launch { repository.unarchiveThread(threadId) }
    }
    fun toggleStar(threadId: String) {
        viewModelScope.launch {
            val currentState = state.value as? InboxState.Success ?: return@launch
            val thread = currentState.threads.find { it.threadId == threadId } ?: return@launch
            repository.toggleStar(threadId, thread.isStarred)
        }
    }
    private fun queueAction(threadId: String, type: ActionType, message: String) {
        pendingHideIds.value = pendingHideIds.value + threadId
        _toastState.value = ToastState(threadId, message, type)
        pendingActionJobs[threadId]?.cancel()
        pendingActionJobs[threadId] = viewModelScope.launch {
            delay(4000)
            executeAction(threadId, type)
            if (_toastState.value?.threadId == threadId) {
                _toastState.value = null
            }
        }
    }
    private suspend fun executeAction(threadId: String, type: ActionType) {
        pendingHideIds.value = pendingHideIds.value - threadId
        pendingActionJobs.remove(threadId)
        when (type) {
            ActionType.ARCHIVE -> repository.archiveThread(threadId)
            ActionType.DELETE -> repository.deleteThread(threadId)
        }
    }
    fun undoAction() {
        val currentToast = _toastState.value ?: return
        val threadId = currentToast.threadId
        pendingActionJobs[threadId]?.cancel()
        pendingActionJobs.remove(threadId)
        pendingHideIds.value = pendingHideIds.value - threadId
        _toastState.value = null
    }
    fun dismissToast() {
        val currentToast = _toastState.value ?: return
        val threadId = currentToast.threadId
        pendingActionJobs[threadId]?.cancel()
        viewModelScope.launch {
            executeAction(threadId, currentToast.actionType)
            _toastState.value = null
        }
    }
    fun dismissDonationPrompt() {
        viewModelScope.launch {
            settingsDataStore.setHasSeenDonationPrompt(true)
            _showDonationPrompt.value = false
        }
    }
}