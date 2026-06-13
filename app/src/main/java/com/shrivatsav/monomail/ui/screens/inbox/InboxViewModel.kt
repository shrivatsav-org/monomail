package com.shrivatsav.monomail.ui.screens.inbox

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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

class InboxViewModel(
    private val repository: EmailRepository,
    private val contactProvider: ContactSuggestionProvider
) : ViewModel() {

    private val _state = MutableStateFlow<InboxState>(InboxState.Loading)
    val state: StateFlow<InboxState> = _state.asStateFlow()

    private var nextPageToken: String? = null
    private var currentServerQuery: String? = null

    // Guard against concurrent loadMore calls
    private var isLoadingMore = false

    private var currentTab = InboxTab.INBOX

    init {
        loadInbox()
    }

    fun switchTab(tab: InboxTab) {
        if (currentTab == tab) return
        currentTab = tab
        currentServerQuery = null // Clear any manual search when switching tabs
        loadInbox(forceRefresh = true)
    }

    fun loadInbox(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            // Show cached data immediately while fetching fresh (only for INBOX tab)
            if (!forceRefresh && currentTab == InboxTab.INBOX) {
                val cachedResult = repository.getCachedInboxThreads()
                val cachedPage = cachedResult.getOrNull()
                if (cachedPage != null && cachedPage.threads.isNotEmpty()) {
                    nextPageToken = cachedPage.nextPageToken
                    _state.value = InboxState.Success(
                        threads = cachedPage.threads,
                        currentTab = currentTab,
                        nextPageToken = cachedPage.nextPageToken,
                        isRefreshing = true
                    )
                    // Index cached contacts
                    contactProvider.indexFromThreads(cachedPage.threads)
                } else {
                    _state.value = InboxState.Loading
                }
            } else {
                _state.value = InboxState.Loading
            }

            if (!forceRefresh) {
                currentServerQuery = null
            }

            val query = currentServerQuery ?: getTabQuery(currentTab)
            val result = repository.getInboxThreads(query = query)
            _state.value = result.fold(
                onSuccess = {
                    nextPageToken = it.nextPageToken
                    if (currentTab == InboxTab.INBOX) {
                        contactProvider.indexFromThreads(it.threads)
                    }
                    InboxState.Success(
                        threads = it.threads, 
                        currentTab = currentTab,
                        nextPageToken = it.nextPageToken
                    )
                },
                onFailure = {
                    val current = _state.value
                    if (current is InboxState.Success) {
                        current.copy(isRefreshing = false)
                    } else {
                        InboxState.Error(it.message ?: "Failed to load inbox")
                    }
                }
            )
        }
    }

    fun searchServer(query: String) {
        viewModelScope.launch {
            _state.value = InboxState.Loading
            currentServerQuery = query.takeIf { it.isNotBlank() }
            val result = repository.getInboxThreads(query = currentServerQuery)
            _state.value = result.fold(
                onSuccess = {
                    nextPageToken = it.nextPageToken
                    InboxState.Success(
                        threads = it.threads, 
                        currentTab = currentTab,
                        nextPageToken = it.nextPageToken
                    )
                },
                onFailure = {
                    InboxState.Error(it.message ?: "Search failed")
                }
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            val current = _state.value
            if (current is InboxState.Success) {
                _state.value = current.copy(isRefreshing = true)
            }
            val query = currentServerQuery ?: getTabQuery(currentTab)
            val result = repository.getInboxThreads(query = query)
            _state.value = result.fold(
                onSuccess = {
                    nextPageToken = it.nextPageToken
                    if (currentTab == InboxTab.INBOX) {
                        contactProvider.indexFromThreads(it.threads)
                    }
                    InboxState.Success(
                        threads = it.threads, 
                        currentTab = currentTab,
                        nextPageToken = it.nextPageToken
                    )
                },
                onFailure = {
                    InboxState.Error(it.message ?: "Refresh failed")
                }
            )
        }
    }

    fun loadMore() {
        val token = nextPageToken ?: return
        if (isLoadingMore) return // prevent duplicate in-flight calls
        isLoadingMore = true

        viewModelScope.launch {
            val current = (_state.value as? InboxState.Success) ?: run {
                isLoadingMore = false
                return@launch
            }
            val query = currentServerQuery ?: getTabQuery(currentTab)
            val result = repository.getInboxThreads(pageToken = token, query = query)
            result.onSuccess { page ->
                nextPageToken = page.nextPageToken
                contactProvider.indexFromThreads(page.threads)
                _state.value = current.copy(
                    threads = current.threads + page.threads,
                    nextPageToken = page.nextPageToken
                )
            }
            isLoadingMore = false
        }
    }

    fun markAllAsRead() {
        viewModelScope.launch {
            val current = _state.value as? InboxState.Success ?: return@launch
            // Don't mark all as read from sent or archive tabs (not typical behavior)
            if (current.currentTab != InboxTab.INBOX) return@launch
            val unreadIds = current.threads
                .filter { !it.isRead }
                .map { it.latestMessageId }
            if (unreadIds.isEmpty()) return@launch

            // Optimistic update
            _state.value = current.copy(
                threads = current.threads.map { if (!it.isRead) it.copy(isRead = true) else it }
            )

            // Revert on server failure
            val result = repository.markEmailsAsRead(unreadIds)
            if (result.isFailure) {
                _state.value = current
            }
        }
    }

    fun archiveThread(threadId: String) {
        viewModelScope.launch {
            val current = _state.value as? InboxState.Success ?: return@launch
            // Optimistic update
            _state.value = current.copy(
                threads = current.threads.filter { it.threadId != threadId }
            )
            // Background network call
            repository.archiveThread(threadId)
        }
    }

    fun unarchiveThread(threadId: String) {
        viewModelScope.launch {
            val current = _state.value as? InboxState.Success ?: return@launch
            // Optimistic update
            _state.value = current.copy(
                threads = current.threads.filter { it.threadId != threadId }
            )
            // Background network call
            repository.unarchiveThread(threadId)
        }
    }

    fun toggleStar(threadId: String) {
        viewModelScope.launch {
            val current = _state.value as? InboxState.Success ?: return@launch
            
            // Find the thread
            val threadToUpdate = current.threads.find { it.threadId == threadId } ?: return@launch
            val isCurrentlyStarred = threadToUpdate.isStarred
            
            // Optimistic update
            _state.value = current.copy(
                threads = current.threads.map { 
                    if (it.threadId == threadId) it.copy(isStarred = !isCurrentlyStarred) 
                    else it 
                }
            )
            
            // Background network call
            if (isCurrentlyStarred) {
                repository.unstarThread(threadId)
            } else {
                repository.starThread(threadId)
            }
        }
    }

    private fun getTabQuery(tab: InboxTab): String? {
        return when (tab) {
            InboxTab.INBOX -> null // Repository defaults to "label:INBOX" when query is null
            InboxTab.SENT -> "in:sent"
            InboxTab.ARCHIVED -> "-in:inbox -in:sent -in:trash -in:spam"
            InboxTab.STARRED -> "is:starred"
        }
    }
}