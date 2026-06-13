package com.shrivatsav.monomail.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class EmailDetailState {
    object Loading : EmailDetailState()
    data class Success(val emails: List<Email>) : EmailDetailState()
    data class Error(val message: String) : EmailDetailState()
}

class EmailDetailViewModel(
    private val repository: EmailRepository,
    private val threadId: String
) : ViewModel() {

    // Observe the specific thread from DB
    val state: StateFlow<EmailDetailState> = repository.getThreadEmailsFlow(threadId)
        .map { emails ->
            if (emails.isNotEmpty()) {
                val unreadIds = emails.filter { !it.isRead }.map { it.id }
                if (unreadIds.isNotEmpty()) {
                    repository.markEmailsAsRead(unreadIds)
                }
                EmailDetailState.Success(emails)
            } else {
                EmailDetailState.Loading
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = EmailDetailState.Loading
        )

    val isStarred: StateFlow<Boolean> = repository.getThreadEmailsFlow(threadId)
        .map { emails -> emails.any { it.isStarred } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    init {
        // Trigger a background refresh to ensure we have the full thread details
        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
            repository.refreshThread(threadId)
        }
    }

    fun toggleStar() {
        viewModelScope.launch {
            repository.toggleStar(threadId, isStarred.value)
        }
    }

    suspend fun fetchAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return repository.getAttachmentBytes(messageId, attachmentId)
    }
}
