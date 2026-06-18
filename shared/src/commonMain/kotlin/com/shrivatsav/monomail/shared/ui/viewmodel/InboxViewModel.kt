package com.shrivatsav.monomail.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.shared.auth.AccountManager
import com.shrivatsav.monomail.shared.auth.UserProfile
import com.shrivatsav.monomail.shared.data.model.EmailThread
import com.shrivatsav.monomail.shared.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.shared.data.repository.EmailRepository
import com.shrivatsav.monomail.shared.data.repository.InboxTab
import com.shrivatsav.monomail.shared.data.settings.SettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class InboxState {
    data object Loading : InboxState()
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
    private val accountManager: AccountManager,
    private val settings: SettingsRepository
) : ViewModel() {
    enum class ActionType { ARCHIVE, DELETE }
    data class ToastState(val threadId: String, val message: String, val actionType: ActionType)

    private val _currentTab = MutableStateFlow(InboxTab.INBOX)
    val currentTab: StateFlow<InboxTab> = _currentTab.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    private val _isLoadingMore = MutableStateFlow(false)
    private val pageTokens = mutableMapOf<String, String?>()
    private var currentServerQuery: String? = null
    private fun getPageTokenKey(): String = "${_currentTab.value.name}_${currentServerQuery ?: ""}"

    private val pendingHideIds = MutableStateFlow<Set<String>>(emptySet())
    private val pendingActionJobs = mutableMapOf<String, Job>()

    private val _toastState = MutableStateFlow<ToastState?>(null)
    val toastState: StateFlow<ToastState?> = _toastState.asStateFlow()
    private val _uiError = MutableSharedFlow<String>()
    val uiError = _uiError.asSharedFlow()

    val accounts: StateFlow<List<UserProfile>> = accountManager.accountsFlow
    private val _activeAccountId = MutableStateFlow(accountManager.getActiveAccount()?.id)

    private val _unifiedInboxEnabled = MutableStateFlow(false)
    val unifiedInboxEnabled = _unifiedInboxEnabled.asStateFlow()
    private val _organizeByThread = MutableStateFlow(true)
    val organizeByThread = _organizeByThread.asStateFlow()
    private val _showDonationPrompt = MutableStateFlow(false)
    val showDonationPrompt = _showDonationPrompt.asStateFlow()

    val state: StateFlow<InboxState> =
        combine(_currentTab, _activeAccountId, _unifiedInboxEnabled, _organizeByThread) { tab, _, _, organize ->
            Pair(tab, organize)
        }.flatMapLatest { (tab, organize) ->
            val flow = if (organize) {
                if (tab == InboxTab.UNIFIED) repository.getAllInboxThreadsFlow()
                else repository.getInboxThreadsFlow(tab)
            } else {
                repository.getInboxEmailsFlow(tab).map { emails ->
                    emails.groupBy { it.threadId }.map { (_, threadEmails) ->
                        val latest = threadEmails.maxBy { it.date }
                        EmailThread(
                            threadId = latest.threadId,
                            subject = latest.subject.replaceFirst(
                                Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), ""
                            ),
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
            combine(flow, _isRefreshing, _isLoadingMore, pendingHideIds) { threads, isRefreshing, isLoadingMore, hiddenIds ->
                val filtered = threads.filter { it.threadId !in hiddenIds }
                InboxState.Success(filtered, tab, isRefreshing, isLoadingMore, pageTokens[getPageTokenKey()])
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), InboxState.Loading)

    init {
        viewModelScope.launch {
            accountManager.activeAccountFlow.collect { profile -> _activeAccountId.value = profile?.id }
        }
        viewModelScope.launch {
            settings.settingsFlow.collect { s ->
                _unifiedInboxEnabled.value = s.unifiedInboxEnabled
                _organizeByThread.value = s.organizeByThread
                if (!s.hasSeenDonationPrompt && accountManager.getActiveAccount() != null) {
                    _showDonationPrompt.value = true
                }
                if (!s.unifiedInboxEnabled && _currentTab.value == InboxTab.UNIFIED) {
                    _currentTab.value = InboxTab.INBOX
                }
            }
        }
        refresh()
        viewModelScope.launch {
            InboxTab.entries.forEach { tab ->
                if (tab != _currentTab.value && tab != InboxTab.UNIFIED) repository.refreshInbox(tab)
            }
        }
        startForegroundPolling()
    }

    fun switchAccount(accountId: String) {
        viewModelScope.launch {
            accountManager.setActiveAccountId(accountId)
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
                if (token != null) pageTokens[getPageTokenKey()] = token else pageTokens.remove(getPageTokenKey())
            }.onFailure { _uiError.emit(it.message ?: "Failed to refresh emails") }
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
            repository.refreshInbox(_currentTab.value, pageToken = token, query = currentServerQuery)
                .onSuccess { newToken -> if (newToken != null) pageTokens[key] = newToken else pageTokens.remove(key) }
            _isLoadingMore.value = false
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val current = state.value as? InboxState.Success ?: return@launch
            if (current.currentTab != InboxTab.INBOX) return@launch
            current.threads.filter { !it.isRead }.forEach { repository.markThreadAsRead(it.threadId) }
        }
    }

    fun markThreadAsRead(threadId: String) = viewModelScope.launch { repository.markThreadAsRead(threadId) }.let {}
    fun markThreadAsUnread(threadId: String) = viewModelScope.launch { repository.markThreadAsUnread(threadId) }.let {}
    fun restoreThread(threadId: String) = viewModelScope.launch { repository.restoreThread(threadId) }.let {}
    fun unarchiveThread(threadId: String) = viewModelScope.launch { repository.unarchiveThread(threadId) }.let {}

    fun archiveThread(threadId: String) = queueAction(threadId, ActionType.ARCHIVE, "Conversation archived")
    fun deleteThread(threadId: String) = queueAction(threadId, ActionType.DELETE, "Conversation deleted")

    fun toggleStar(threadId: String) {
        viewModelScope.launch {
            val current = state.value as? InboxState.Success ?: return@launch
            val thread = current.threads.find { it.threadId == threadId } ?: return@launch
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
            if (_toastState.value?.threadId == threadId) _toastState.value = null
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
        val toast = _toastState.value ?: return
        pendingActionJobs[toast.threadId]?.cancel()
        pendingActionJobs.remove(toast.threadId)
        pendingHideIds.value = pendingHideIds.value - toast.threadId
        _toastState.value = null
    }

    fun dismissToast() {
        val toast = _toastState.value ?: return
        pendingActionJobs[toast.threadId]?.cancel()
        viewModelScope.launch {
            executeAction(toast.threadId, toast.actionType)
            _toastState.value = null
        }
    }

    fun dismissDonationPrompt() {
        settings.setHasSeenDonationPrompt(true)
        _showDonationPrompt.value = false
    }
}
