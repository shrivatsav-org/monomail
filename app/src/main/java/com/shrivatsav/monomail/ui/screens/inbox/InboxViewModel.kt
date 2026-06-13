package com.shrivatsav.monomail.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

enum class InboxTab { INBOX, SENT, ARCHIVED, STARRED }

sealed class InboxState {
    object Loading : InboxState()
    data class Success(
        val threads: List<EmailThread>,
        val currentTab: InboxTab = InboxTab.INBOX,
        val isRefreshing: Boolean = false,
        val nextPageToken: String? = null
    ) : InboxState()
    data class Error(val message: String) : InboxState()
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val repository: EmailRepository,
    private val contactProvider: ContactSuggestionProvider
) : ViewModel() {

    private val _currentTab = MutableStateFlow(InboxTab.INBOX)
    private val _isRefreshing = MutableStateFlow(false)
    private val _nextPageToken = MutableStateFlow<String?>(null)
    private var currentServerQuery: String? = null
    private var isLoadingMore = false

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

    // State flow based on the DB flow of the current tab
    val state: StateFlow<InboxState> = combine(
        _currentTab.flatMapLatest { tab -> repository.getInboxThreadsFlow(tab) },
        _currentTab,
        _isRefreshing,
        _nextPageToken,
        pendingHideIds
    ) { threads, tab, isRefreshing, nextPageToken, hiddenIds ->
        val filteredThreads = threads.filter { it.threadId !in hiddenIds }
        InboxState.Success(filteredThreads, tab, isRefreshing, nextPageToken)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InboxState.Loading
    )

    init {
        refresh()
        startForegroundPolling()
    }

    private fun startForegroundPolling() {
        viewModelScope.launch {
            while (true) {
                delay(30_000) // Poll every 30 seconds silently
                refresh(showLoader = false)
            }
        }
    }

    fun switchTab(tab: InboxTab) {
        if (_currentTab.value == tab) return
        _currentTab.value = tab
        currentServerQuery = null
        refresh(showLoader = false)
    }

    fun refresh(showLoader: Boolean = true) {
        viewModelScope.launch {
            if (showLoader) _isRefreshing.value = true
            val query = currentServerQuery // Use query if searching
            val result = repository.refreshInbox(_currentTab.value, query = query)
            result.onSuccess { token ->
                _nextPageToken.value = token
            }
            if (showLoader) _isRefreshing.value = false
        }
    }

    fun searchServer(query: String) {
        currentServerQuery = query.takeIf { it.isNotBlank() }
        refresh()
    }

    fun loadMore() {
        val token = _nextPageToken.value ?: return
        if (isLoadingMore) return
        isLoadingMore = true

        viewModelScope.launch {
            val result = repository.refreshInbox(_currentTab.value, pageToken = token, query = currentServerQuery)
            result.onSuccess { newToken ->
                _nextPageToken.value = newToken
            }
            isLoadingMore = false
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val currentState = state.value as? InboxState.Success ?: return@launch
            if (currentState.currentTab != InboxTab.INBOX) return@launch
            val unreadIds = currentState.threads.filter { !it.isRead }.map { it.latestMessageId }
            if (unreadIds.isEmpty()) return@launch

            repository.markEmailsAsRead(unreadIds)
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
}