package com.shrivatsav.monomail.ui.screens.detail
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
sealed class EmailDetailState {
    data class Success(val emails: List<Email>, val isRefreshing: Boolean = false, val refreshError: String? = null) : EmailDetailState()
    data class Error(val message: String) : EmailDetailState()
}
@HiltViewModel
class EmailDetailViewModel @Inject constructor(
    private val repository: EmailRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val threadId: String = savedStateHandle.get<String>("threadId") ?: ""
    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    val state: StateFlow<EmailDetailState> = kotlinx.coroutines.flow.combine(
        repository.getThreadEmailsFlow(threadId),
        _isLoading,
        _error
    ) { emails, isLoading, error ->
        if (emails.isNotEmpty()) {
            val needsBodyFetch = emails.any { it.body.isEmpty() }
            EmailDetailState.Success(emails, isRefreshing = isLoading && needsBodyFetch, refreshError = error)
        } else if (error != null) {
            EmailDetailState.Error(error)
        } else if (!isLoading) {
            EmailDetailState.Error("Email thread not found.")
        } else {
            EmailDetailState.Success(emptyList(), isRefreshing = true)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EmailDetailState.Success(emptyList(), isRefreshing = true)
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
        viewModelScope.launch {
            state.collect { s ->
                if (s is EmailDetailState.Success) {
                    val unreadIds = s.emails.filter { !it.isRead }.map { it.id }
                    if (unreadIds.isNotEmpty()) {
                        repository.markEmailsAsRead(unreadIds)
                    }
                }
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
