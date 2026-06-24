package com.shrivatsav.monomail.ui.screens.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.ScheduledEmailEvent
import com.shrivatsav.monomail.SentEmailEvent
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.repository.ContactSuggestionProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ComposeMode { NEW, REPLY, FORWARD }

data class ComposeUiState(
    val to: String = "",
    val cc: String = "",
    val bcc: String = "",
    val subject: String = "",
    val body: String = "",
    val isSending: Boolean = false,
    val isSent: Boolean = false,
    val error: String? = null,
    val mode: ComposeMode = ComposeMode.NEW,
    val threadId: String? = null,
    val inReplyToMessageId: String? = null,
    val references: String? = null,
    val originalBody: String? = null,
    val attachments: List<EmailAttachment> = emptyList(),
    val showConfirmSendDialog: Boolean = false,
    val showSchedulePicker: Boolean = false,
    val scheduledAt: Long? = null
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val contactProvider: ContactSuggestionProvider,
    private val authManager: AuthManager,
    private val settingsDataStore: SettingsDataStore,
    private val sentEmailEvents: MutableSharedFlow<SentEmailEvent>,
    private val scheduledEmailEvents: MutableSharedFlow<ScheduledEmailEvent>,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val fromEmail: String = authManager.currentUser?.email ?: ""
    private val accountId: String = authManager.currentUser?.id ?: ""
    private val mode: ComposeMode = ComposeMode.valueOf(savedStateHandle.get<String>("mode") ?: "NEW")
    private val replyTo: String = savedStateHandle.get<String>("to") ?: ""
    private val originalSubject: String = savedStateHandle.get<String>("subject") ?: ""
    private val threadIdArg: String? = savedStateHandle.get<String>("threadId")?.takeIf { it.isNotEmpty() }
    private val messageIdArg: String? = savedStateHandle.get<String>("messageId")?.takeIf { it.isNotEmpty() }
    private val scheduledId: String? = savedStateHandle.get<String>("scheduledId")?.takeIf { it.isNotEmpty() }

    private var scheduledMessageId: String? = scheduledId

    private val _state = MutableStateFlow(
        ComposeUiState(
            mode = mode,
            to = when (mode) {
                ComposeMode.REPLY -> replyTo
                else -> ""
            },
            subject = when (mode) {
                ComposeMode.REPLY -> "Re: ${originalSubject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")}"
                ComposeMode.FORWARD -> "Fwd: ${originalSubject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")}"
                else -> ""
            },
            body = "",
            threadId = threadIdArg,
            inReplyToMessageId = messageIdArg,
            references = messageIdArg,
            originalBody = null
        )
    )
    val templatesFlow = settingsDataStore.templatesFlow
    val state: StateFlow<ComposeUiState> = _state.asStateFlow()

    private var confirmBeforeSending = false

    init {
        viewModelScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                confirmBeforeSending = settings.confirmBeforeSending
            }
        }
        viewModelScope.launch {
            if (!messageIdArg.isNullOrEmpty()) {
                val email = repository.getEmailById(messageIdArg)
                if (email != null) {
                    _state.value = _state.value.copy(originalBody = email.body)
                }
            }
        }
        viewModelScope.launch {
            if (!scheduledId.isNullOrEmpty()) {
                val scheduled = repository.getScheduledMessageById(scheduledId)
                if (scheduled != null) {
                    _state.value = _state.value.copy(
                        to = scheduled.to,
                        cc = scheduled.cc,
                        bcc = scheduled.bcc,
                        subject = scheduled.subject,
                        body = scheduled.body
                    )
                }
            }
        }
    }

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

    fun updateTo(value: String) {
        _state.value = _state.value.copy(to = value)
        _toQuery.value = value
    }

    fun updateCc(value: String) {
        _state.value = _state.value.copy(cc = value)
    }

    fun updateBcc(value: String) {
        _state.value = _state.value.copy(bcc = value)
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
        if (confirmBeforeSending) {
            _state.value = current.copy(showConfirmSendDialog = true)
            return
        }
        executeSend(current)
    }

    fun confirmSend() {
        val current = _state.value
        _state.value = current.copy(showConfirmSendDialog = false)
        executeSend(current)
    }

    fun dismissConfirmSend() {
        _state.value = _state.value.copy(showConfirmSendDialog = false)
    }

    fun applyTemplate(subject: String, body: String) {
        val current = _state.value
        _state.value = current.copy(
            subject = if (current.subject.isBlank()) subject else current.subject,
            body = if (current.body.isBlank()) body else current.body
        )
    }

    private fun executeSend(current: ComposeUiState) {
        viewModelScope.launch {
            val sId = scheduledMessageId
            if (!sId.isNullOrEmpty()) repository.cancelScheduledMessage(sId)
            _state.value = current.copy(isSending = true, error = null)
            val fullBody = buildString {
                append(current.body)
                if (current.originalBody != null) {
                    append("<br><br><blockquote>")
                    append(current.originalBody)
                    append("</blockquote>")
                }
            }
            val result = repository.sendEmail(
                from = fromEmail,
                to = current.to,
                cc = current.cc,
                bcc = current.bcc,
                subject = current.subject,
                body = fullBody,
                threadId = current.threadId,
                inReplyToMessageId = current.inReplyToMessageId,
                references = current.references,
                attachments = current.attachments
            )
            result.onSuccess { sentThreadId ->
                sentEmailEvents.tryEmit(
                    SentEmailEvent(
                        threadId = sentThreadId,
                        to = current.to,
                        subject = current.subject
                    )
                )
            }
            _state.value = result.fold(
                onSuccess = { current.copy(isSending = false, isSent = true) },
                onFailure = { current.copy(isSending = false, error = it.message ?: "Failed to send") }
            )
        }
    }

    fun showSchedulePicker() {
        _state.value = _state.value.copy(showSchedulePicker = true)
    }

    fun dismissSchedulePicker() {
        _state.value = _state.value.copy(showSchedulePicker = false)
    }

    fun scheduleSend(scheduledAt: Long) {
        val current = _state.value
        _state.value = current.copy(showSchedulePicker = false, scheduledAt = scheduledAt)
        viewModelScope.launch {
            val sId = scheduledMessageId
            if (!sId.isNullOrEmpty()) repository.cancelScheduledMessage(sId)
            val cachedAttachments = repository.copyAttachmentsToCache(
                "schedule_${System.currentTimeMillis()}",
                current.attachments
            )
            repository.scheduleSend(
                accountId = accountId,
                fromEmail = fromEmail,
                to = current.to,
                subject = current.subject,
                body = current.body,
                scheduledAt = scheduledAt,
                cc = current.cc,
                bcc = current.bcc,
                attachments = cachedAttachments
            )
            scheduledEmailEvents.tryEmit(
                ScheduledEmailEvent(
                    to = current.to,
                    subject = current.subject,
                    scheduledAt = scheduledAt
                )
            )
            _state.value = _state.value.copy(isSent = true)
        }
    }

    fun cancelSchedule() {
        _state.value = _state.value.copy(scheduledAt = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
