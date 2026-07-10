package com.shrivatsav.monomail.ui.screens.compose

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.ScheduledEmailEvent
import com.shrivatsav.monomail.SentEmailEvent
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.pgp.PgpManager
import com.shrivatsav.monomail.data.pgp.PgpKeyInfo
import com.shrivatsav.monomail.data.provider.SendAsAlias
import com.shrivatsav.monomail.data.repository.EmailContact
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.repository.SendEmailParams
import com.shrivatsav.monomail.data.repository.ScheduleSendParams
import com.shrivatsav.monomail.data.repository.suggestContacts
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
    val from: String = "",
    val fromAliases: List<SendAsAlias> = emptyList(),
    val showFromDropdown: Boolean = false,
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
    val scheduledAt: Long? = null,
    val encryptEnabled: Boolean = false,
    val signEnabled: Boolean = false,
    val hasEncryptionKeys: Boolean = false,
    val hasSigningKeys: Boolean = false
)

@HiltViewModel
class ComposeViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val authManager: AuthManager,
    private val settingsDataStore: SettingsDataStore,
    private val sentEmailEvents: MutableSharedFlow<SentEmailEvent>,
    private val scheduledEmailEvents: MutableSharedFlow<ScheduledEmailEvent>,
    private val pgpManager: PgpManager,
    private val pgpKeyManager: com.shrivatsav.monomail.data.pgp.PgpKeyManager,
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
            from = fromEmail,
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
        // Load PGP key availability
        viewModelScope.launch {
            val encKeys = pgpManager.getAvailableEncryptionKeys()
            val sigKeys = pgpManager.getAvailableSigningKeys()
            _state.value = _state.value.copy(
                hasEncryptionKeys = encKeys.isNotEmpty(),
                hasSigningKeys = sigKeys.isNotEmpty()
            )
        }
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
        viewModelScope.launch {
            repository.refreshSendAsAliases()
            repository.sendAsAliasesFlow.collect { aliases ->
                val currentFrom = _state.value.from
                _state.value = _state.value.copy(
                    fromAliases = aliases,
                    from = currentFrom.ifEmpty { fromEmail }
                )
            }
        }
    }

    private val _suggestions = MutableStateFlow<List<EmailContact>>(emptyList())
    val suggestions: StateFlow<List<EmailContact>> = _suggestions.asStateFlow()
    private val _toQuery = MutableStateFlow("")

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _toQuery
                .debounce(200)
                .distinctUntilChanged()
                .map { query -> suggestContacts(query) }
                .collect { _suggestions.value = it }
        }
    }

    fun selectAlias(alias: SendAsAlias) {
        _state.value = _state.value.copy(from = alias.email, showFromDropdown = false)
    }

    fun toggleFromDropdown() {
        _state.value = _state.value.copy(showFromDropdown = !_state.value.showFromDropdown)
    }

    fun dismissFromDropdown() {
        _state.value = _state.value.copy(showFromDropdown = false)
    }

    fun toggleEncrypt() {
        _state.value = _state.value.copy(encryptEnabled = !_state.value.encryptEnabled)
    }

    fun toggleSign() {
        _state.value = _state.value.copy(signEnabled = !_state.value.signEnabled)
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

    fun selectSuggestion(contact: EmailContact) {
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
            val finalBody = if (current.encryptEnabled || current.signEnabled) {
                applyPgp(current, fullBody) ?: return@launch
            } else fullBody
            val result = repository.sendEmail(
                from = current.from,
                to = current.to,
                subject = current.subject,
                body = finalBody,
                params = SendEmailParams(cc = current.cc, bcc = current.bcc, threadId = current.threadId, inReplyToMessageId = current.inReplyToMessageId, references = current.references, attachments = current.attachments)
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

            val finalBody = if (current.encryptEnabled || current.signEnabled) {
                applyPgp(current, current.body) ?: return@launch
            } else current.body

            val cachedAttachments = repository.copyAttachmentsToCache(
                "schedule_${System.currentTimeMillis()}",
                current.attachments
            )
            val fromAlias = current.from.takeIf { it != fromEmail }
            repository.scheduleSend(
                accountId = accountId,
                fromEmail = current.from,
                to = current.to,
                subject = current.subject,
                body = finalBody,
                scheduledAt = scheduledAt,
                params = ScheduleSendParams(cc = current.cc, bcc = current.bcc, attachments = cachedAttachments, fromAlias = fromAlias)
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

    /** Apply PGP encryption and/or signing. Returns the transformed body or null on failure (sets error). */
    private suspend fun applyPgp(current: ComposeUiState, bodyText: String): String? {
        return if (current.encryptEnabled) {
            val recipients = parseRecipients(current.to, current.cc, current.bcc)
            if (recipients.isEmpty()) {
                _state.value = current.copy(isSending = false, error = "No valid recipient addresses for encryption")
                return null
            }
            val signingFp = if (current.signEnabled) getBestSigningKey() else null
            val result = if (signingFp != null) {
                pgpManager.encryptAndSignBody(bodyText, recipients, signingFp)
            } else {
                pgpManager.encryptBody(bodyText, recipients)
            }
            if (result == null) {
                _state.value = current.copy(
                    isSending = false,
                    error = "Missing PGP key for one or more recipients. Import their public key first."
                )
                return null
            }
            result.encryptedBody
        } else if (current.signEnabled) {
            val signingFp = getBestSigningKey()
            if (signingFp == null) {
                _state.value = current.copy(
                    isSending = false,
                    error = "No private key available for signing. Generate or import one in Settings > PGP Keys."
                )
                return null
            }
            pgpManager.signBody(bodyText, signingFp) ?: run {
                _state.value = current.copy(isSending = false, error = "Failed to sign message")
                return null
            }
        } else bodyText
    }

    private fun getBestSigningKey(): String? {
        val keys = pgpManager.getAvailableSigningKeys()
        if (keys.isEmpty()) return null
        val currentEmail = authManager.currentUser?.email ?: ""
        val matching = keys.find { it.userId.contains(currentEmail, ignoreCase = true) }
        return matching?.fingerprint ?: keys.first().fingerprint
    }

    private fun parseRecipients(vararg fields: String): List<String> {
        val emailRegex = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
        return fields.flatMap { field ->
            field.split(",").mapNotNull { part ->
                emailRegex.find(part.trim())?.value?.lowercase()
            }
        }.distinct()
    }

    fun cancelSchedule() {
        _state.value = _state.value.copy(scheduledAt = null)
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
