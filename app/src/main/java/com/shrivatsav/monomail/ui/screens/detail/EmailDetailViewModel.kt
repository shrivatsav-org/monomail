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
    data class Success(val emails: List<Email>, val isRefreshing: Boolean = false, val refreshError: String? = null) : EmailDetailState()
    data class Error(val message: String) : EmailDetailState()
}
class EmailDetailViewModel(
    private val repository: EmailRepository,
    private val threadId: String
) : ViewModel() {
    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val state: StateFlow<EmailDetailState> = kotlinx.coroutines.flow.combine(
        repository.getThreadEmailsFlow(threadId),
        _isLoading,
        _error
    ) { emails, isLoading, error ->
        if (emails.isNotEmpty()) {
            val unreadIds = emails.filter { !it.isRead }.map { it.id }
            if (unreadIds.isNotEmpty()) {
                repository.markEmailsAsRead(unreadIds)
            }
            // Only show the loading spinner if we don't have the body yet
            val needsBodyFetch = emails.any { it.body.isEmpty() }
            EmailDetailState.Success(emails, isRefreshing = isLoading && needsBodyFetch, refreshError = error)
        } else if (error != null) {
            EmailDetailState.Error(error)
        } else if (!isLoading) {
            EmailDetailState.Error("Email thread not found.")
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
        viewModelScope.launch {
            repository.markThreadAsRead(threadId)
            _isLoading.value = true
            val result = repository.refreshThread(threadId)
            _isLoading.value = false
            result.onFailure {
                _error.value = it.message ?: "Failed to refresh thread"
            }
        }
    }
    fun toggleStar() {
        viewModelScope.launch {
            repository.toggleStar(threadId, isStarred.value)
        }
    }
    fun markUnread(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.markThreadAsUnread(threadId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
        }
    }
    fun archiveThread(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.archiveThread(threadId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
        }
    }
    fun trashThread(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.deleteThread(threadId)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onComplete() }
        }
    }
    suspend fun fetchAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return repository.getAttachmentBytes(messageId, attachmentId)
    }
}
