package com.shrivatsav.monomail.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.shared.data.model.Email
import com.shrivatsav.monomail.shared.data.repository.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class EmailDetailState {
    data object Loading : EmailDetailState()
    data class Success(val emails: List<Email>) : EmailDetailState()
    data class Error(val message: String) : EmailDetailState()
}

class EmailDetailViewModel(
    private val repository: EmailRepository,
    private val threadId: String
) : ViewModel() {
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)

    val state: StateFlow<EmailDetailState> = combine(
        repository.getThreadEmailsFlow(threadId), _isLoading, _error
    ) { emails, isLoading, error ->
        if (emails.isNotEmpty()) {
            val unreadIds = emails.filter { !it.isRead }.map { it.id }
            if (unreadIds.isNotEmpty()) repository.markEmailsAsRead(unreadIds)
            EmailDetailState.Success(emails)
        } else if (error != null) {
            EmailDetailState.Error(error)
        } else if (!isLoading) {
            EmailDetailState.Error("Email thread not found.")
        } else {
            EmailDetailState.Loading
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmailDetailState.Loading)

    val isStarred: StateFlow<Boolean> = repository.getThreadEmailsFlow(threadId)
        .map { emails -> emails.any { it.isStarred } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
            _isLoading.value = true
            val result = repository.refreshThread(threadId)
            _isLoading.value = false
            result.onFailure { _error.value = it.message ?: "Failed to refresh thread" }
        }
    }

    fun toggleStar() = viewModelScope.launch { repository.toggleStar(threadId, isStarred.value) }.let {}
    fun markUnread(onComplete: () -> Unit) = viewModelScope.launch {
        repository.markThreadAsUnread(threadId); onComplete()
    }.let {}
    fun archiveThread(onComplete: () -> Unit) = viewModelScope.launch {
        repository.archiveThread(threadId); onComplete()
    }.let {}
    fun trashThread(onComplete: () -> Unit) = viewModelScope.launch {
        repository.deleteThread(threadId); onComplete()
    }.let {}

    suspend fun fetchAttachmentBytes(messageId: String, attachmentId: String): ByteArray? =
        repository.getAttachmentBytes(messageId, attachmentId)
}
