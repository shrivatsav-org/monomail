package com.shrivatsav.monomail.ui.screens.scheduled

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.local.ScheduledMessageEntity
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScheduledMessagesViewModel(
    private val repository: EmailRepository,
    private val accountId: String
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ScheduledMessageEntity>>(emptyList())
    val messages: StateFlow<List<ScheduledMessageEntity>> = _messages.asStateFlow()

    init {
        viewModelScope.launch {
            repository.getPendingScheduledMessagesFlow(accountId).collect { list ->
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