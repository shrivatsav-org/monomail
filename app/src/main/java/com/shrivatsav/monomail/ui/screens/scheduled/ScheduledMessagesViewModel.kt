package com.shrivatsav.monomail.ui.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.local.ScheduledMessageEntity
import com.shrivatsav.monomail.data.repository.EmailRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScheduledMessagesViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val authManager: AuthManager
) : ViewModel() {

    private val accountId: String? get() = authManager.currentUser?.id

    private val _messages = MutableStateFlow<List<ScheduledMessageEntity>>(emptyList())
    val messages: StateFlow<List<ScheduledMessageEntity>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            val id = accountId ?: return@launch
            repository.getPendingScheduledMessagesFlow(id).collect { list ->
                _messages.value = list
            }
        }
    }

    fun cancelSchedule(id: String) {
        viewModelScope.launch {
            repository.cancelScheduledMessage(id)
        }
    }
}
