package com.shrivatsav.monomail.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    private val _state = MutableStateFlow<EmailDetailState>(EmailDetailState.Loading)
    val state: StateFlow<EmailDetailState> = _state.asStateFlow()

    init {
        loadThread()
    }

    private fun loadThread() {
        viewModelScope.launch {
            _state.value = EmailDetailState.Loading
            val result = repository.getThread(threadId)
            _state.value = result.fold(
                onSuccess = { emails ->
                    // Automatically mark unread messages as read
                    val unreadIds = emails.filter { !it.isRead }.map { it.id }
                    if (unreadIds.isNotEmpty()) {
                        launch { repository.markEmailsAsRead(unreadIds) }
                    }
                    EmailDetailState.Success(emails)
                },
                onFailure = { EmailDetailState.Error(it.message ?: "Failed to load thread") }
            )
        }
    }
}
