package com.shrivatsav.monomail.ui.screens.compose

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class ComposeMode { NEW, REPLY, FORWARD }

data class ComposeUiState(
    val to: String = "",
    val subject: String = "",
    val body: String = "",
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val error: String? = null,
    val mode: ComposeMode = ComposeMode.NEW,
    // For reply/forward threading
    val threadId: String? = null,
    val inReplyToMessageId: String? = null,
    val references: String? = null,
    val originalBody: String? = null,
    val attachments: List<EmailAttachment> = emptyList()
)

class ComposeViewModel(
    private val repository: EmailRepository,
    private val contactProvider: ContactSuggestionProvider,
    private val fromEmail: String,
    mode: ComposeMode = ComposeMode.NEW,
    replyTo: String? = null,
    originalSubject: String? = null,
    originalBody: String? = null,
    threadId: String? = null,
    messageId: String? = null
) : ViewModel() {

    private val _state = MutableStateFlow(
        ComposeUiState(
            mode = mode,
            to = when (mode) {
                ComposeMode.REPLY -> replyTo ?: ""
                else -> ""
            },
            subject = when (mode) {
                ComposeMode.REPLY -> "Re: ${originalSubject?.removePrefix("Re: ")?.removePrefix("Fwd: ") ?: ""}"
                ComposeMode.FORWARD -> "Fwd: ${originalSubject?.removePrefix("Re: ")?.removePrefix("Fwd: ") ?: ""}"
                else -> ""
            },
            body = "",
            threadId = threadId,
            inReplyToMessageId = messageId,
            references = messageId,
            originalBody = null // We will fetch this async
        )
    )

    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            if (!messageId.isNullOrEmpty()) {
                val email = repository.getEmailById(messageId)
                if (email != null) {
                    _state.value = _state.value.copy(originalBody = email.body)
                }
            }
        }
    }

    // ── Email autocomplete ───────────────────────────────────────────

    private val _suggestions = MutableStateFlow<List<ContactSuggestionProvider.EmailContact>>(emptyList())
    val suggestions: StateFlow<List<ContactSuggestionProvider.EmailContact>> = _suggestions.asStateFlow()

    private val _toQuery = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _toQuery
                .debounce(200)
                .distinctUntilChanged()
                .map { query -> contactProvider.suggest(query) }
                .collect { _suggestions.value = it }
        }
    }

    // ── State updates ────────────────────────────────────────────────

    fun updateTo(value: String) {
        _state.value = _state.value.copy(to = value)
        _toQuery.value = value
    }

    fun selectSuggestion(contact: ContactSuggestionProvider.EmailContact) {
        _state.value = _state.value.copy(to = contact.email)
        _suggestions.value = emptyList()
        _toQuery.value = ""
    }

    fun updateSubject(value: String) {
        _state.value = _state.value.copy(subject = value)
    }

    fun updateBody(value: String) {
        _state.value = _state.value.copy(body = value)
    }

    fun addAttachment(attachment: EmailAttachment) {
        val current = _state.value
        _state.value = current.copy(attachments = current.attachments + attachment)
    }

    fun removeAttachment(attachment: EmailAttachment) {
        val current = _state.value
        _state.value = current.copy(attachments = current.attachments - attachment)
    }

    fun send() {
        val current = _state.value
        if (current.to.isBlank()) {
            _state.value = current.copy(error = "Recipient is required")
            return
        }
        if (current.subject.isBlank() && current.body.isBlank()) {
            _state.value = current.copy(error = "Cannot send empty email")
            return
        }

        viewModelScope.launch {
            _state.value = current.copy(isSending = true, error = null)

            val fullBody = buildString {
                append(current.body.replace("\n", "<br>"))
                if (current.originalBody != null) {
                    append(current.originalBody.replace("\n", "<br>"))
                }
            }

            val result = repository.sendEmail(
                from = fromEmail,
                to = current.to,
                subject = current.subject,
                body = fullBody,
                threadId = current.threadId,
                inReplyToMessageId = current.inReplyToMessageId,
                references = current.references,
                attachments = current.attachments
            )

            _state.value = result.fold(
                onSuccess = { current.copy(isSending = false, isSent = true) },
                onFailure = { current.copy(isSending = false, error = it.message ?: "Failed to send") }
            )
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
