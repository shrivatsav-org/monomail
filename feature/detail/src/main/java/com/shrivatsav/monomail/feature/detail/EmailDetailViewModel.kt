package com.shrivatsav.monomail.feature.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.util.Log
import com.shrivatsav.monomail.core.data.auth.AuthManager
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.pgp.PgpDecryptionResult
import com.shrivatsav.monomail.data.pgp.PgpManager
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.core.data.settings.EmailTheme
import com.shrivatsav.monomail.core.data.settings.FontScale
import com.shrivatsav.monomail.core.data.settings.SettingsDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed interface ThreadListItem {
    data class Message(val email: Email, val isFocused: Boolean, val isExpanded: Boolean) : ThreadListItem
    data class CollapsedGroup(val count: Int, val hiddenEmailIds: List<String>) : ThreadListItem
}

sealed class EmailDetailState {
    data class Success(val items: List<ThreadListItem>, val emails: List<Email>, val isRefreshing: Boolean = false, val refreshError: String? = null) :
        EmailDetailState()

    data class Error(val message: String) : EmailDetailState()
}

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class EmailDetailViewModel @Inject constructor(
    private val repository: EmailRepository,
    private val settingsDataStore: SettingsDataStore,
    private val pgpManager: PgpManager,
    private val authManager: AuthManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    val currentUserEmail: String = authManager.currentUser?.email ?: ""
    val accountId: String = authManager.currentUser?.id ?: ""
    private val _threadId = MutableStateFlow(savedStateHandle.get<String>("threadId") ?: "")
    private val focusedEmailId = savedStateHandle.get<String>("focusedId")
    private val _expandedEmailIds = MutableStateFlow<Set<String>>(emptySet())

    fun expandEmails(ids: List<String>) {
        _expandedEmailIds.value = _expandedEmailIds.value + ids
    }
    fun toggleEmailExpansion(id: String) {
        val current = _expandedEmailIds.value
        if (current.contains(id)) {
            _expandedEmailIds.value = current - id
        } else {
            _expandedEmailIds.value = current + id
        }
    }
    
    fun setThreadId(id: String) {
        if (_threadId.value != id && id.isNotEmpty()) {
            _threadId.value = id
        }
    }

    private val _isLoading = kotlinx.coroutines.flow.MutableStateFlow(true)
    private val _error = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    private val _decryptedBodies = MutableStateFlow<Map<String, PgpDecryptionResult>>(emptyMap())
    val decryptedBodies: StateFlow<Map<String, PgpDecryptionResult>> = _decryptedBodies.asStateFlow()

    val fontScaleMultiplier: StateFlow<Float> = settingsDataStore.settingsFlow
        .map { settings ->
            when (settings.fontScale) {
                FontScale.EXTRA_SMALL -> 0.8f
                FontScale.SMALL -> 0.9f
                FontScale.DEFAULT -> 1.0f
                FontScale.LARGE -> 1.15f
                FontScale.EXTRA_LARGE -> 1.3f
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 1.0f)

    val isConversationView: StateFlow<Boolean> = settingsDataStore.settingsFlow
        .map { it.organizeByThread }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loadRemoteImages: StateFlow<Boolean> = settingsDataStore.settingsFlow
        .map { it.loadRemoteImages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val emailTheme: StateFlow<EmailTheme> = settingsDataStore.settingsFlow
        .map { it.emailTheme }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmailTheme.AUTO)

    val isDeveloperMode: StateFlow<Boolean> = settingsDataStore.settingsFlow
        .map { it.isDeveloperMode }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val showInlineImages: StateFlow<Boolean> = settingsDataStore.settingsFlow
        .map { it.showInlineImages }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showInlineAttachments: StateFlow<Boolean> = settingsDataStore.settingsFlow
        .map { it.showInlineAttachments }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val state: StateFlow<EmailDetailState> = _threadId.flatMapLatest { id ->
        if (id.isEmpty()) {
            flowOf(EmailDetailState.Success(emptyList(), emptyList(), isRefreshing = false))
        } else {
            combine(
                repository.getThreadEmailsFlow(id),
                _isLoading,
                _error,
                _expandedEmailIds
            ) { emails, isLoading, error, expandedIds ->
                when {
                    emails.isNotEmpty() -> {
                        val deduplicated = emails.fold(mutableListOf<Email>()) { acc, email ->
                            val isDuplicate = acc.any { existing -> 
                                existing.fromEmail == email.fromEmail && 
                                existing.snippet == email.snippet &&
                                existing.body == email.body &&
                                Math.abs(existing.date - email.date) < 60000 
                            }
                            if (!isDuplicate) acc.add(email)
                            acc
                        }
                        val needsBodyFetch = deduplicated.any { it.body.isEmpty() }
                        val items = mutableListOf<ThreadListItem>()
                        
                        if (focusedEmailId != null) {
                            // Collapsing: show focused email + neighbors, collapse the rest
                            val targetIndex = deduplicated.indexOfFirst { it.id == focusedEmailId }.takeIf { it >= 0 }
                                ?: deduplicated.indexOfLast { !it.isRead }.takeIf { it >= 0 }
                                ?: -1
                                
                            var i = 0
                            while (i < deduplicated.size) {
                                val email = deduplicated[i]
                                val isFirst = i == 0
                                val isLast = i == deduplicated.lastIndex
                                val isTarget = i == targetIndex
                                val isBeforeTarget = i == targetIndex - 1
                                val isAfterTarget = i == targetIndex + 1
                                val isExpandedByUser = expandedIds.contains(email.id)
                                
                                if (isFirst || isLast || isTarget || isBeforeTarget || isAfterTarget || isExpandedByUser) {
                                    items.add(ThreadListItem.Message(email, isFocused = isTarget, isExpanded = true))
                                    i++
                                } else {
                                    var j = i + 1
                                    while (j < deduplicated.size) {
                                        val nextEmail = deduplicated[j]
                                        val nextIsLast = j == deduplicated.lastIndex
                                        val nextIsTarget = j == targetIndex
                                        val nextIsBeforeTarget = j == targetIndex - 1
                                        val nextIsExpanded = expandedIds.contains(nextEmail.id)
                                        if (nextIsLast || nextIsTarget || nextIsBeforeTarget || nextIsExpanded) break
                                        j++
                                    }
                                    val hiddenIds = deduplicated.subList(i, j).map { it.id }
                                    items.add(ThreadListItem.CollapsedGroup(hiddenIds.size, hiddenIds))
                                    i = j
                                }
                            }
                        } else {
                            // No focused email: first + last visible, middle collapsed
                            items.add(ThreadListItem.Message(deduplicated.first(), isFocused = false, isExpanded = true))
                            if (deduplicated.size > 2) {
                                val hiddenIds = deduplicated.subList(1, deduplicated.lastIndex).map { it.id }
                                items.add(ThreadListItem.CollapsedGroup(hiddenIds.size, hiddenIds))
                            }
                            if (deduplicated.size > 1) {
                                items.add(ThreadListItem.Message(deduplicated.last(), isFocused = false, isExpanded = true))
                            }
                        }
                        EmailDetailState.Success(items, deduplicated, isRefreshing = isLoading && needsBodyFetch, refreshError = error)
                    }
                    error != null -> EmailDetailState.Error(error)
                    !isLoading -> EmailDetailState.Error("Email thread not found.")
                    else -> EmailDetailState.Success(emptyList(), emptyList(), isRefreshing = true)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EmailDetailState.Success(emptyList(), emptyList(), isRefreshing = true)
    )

    val isStarred: StateFlow<Boolean> = _threadId.flatMapLatest { id ->
        if (id.isEmpty()) flowOf(false)
        else repository.getThreadEmailsFlow(id).map { emails -> emails.any { it.isStarred } }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = false
    )

    init {
        viewModelScope.launch {
            _threadId.collect { id ->
                if (id.isNotEmpty()) {
                    try {
                        repository.markThreadAsRead(id)
                        _isLoading.value = true
                        val result = repository.refreshThread(id)
                        _isLoading.value = false
                        result.onFailure {
                            _error.value = it.message ?: "Failed to refresh thread"
                        }
                    } catch (e: Exception) {
                        _isLoading.value = false
                        _error.value = e.message ?: "Failed to load email"
                        Log.e("EmailDetailVM", "Unexpected error loading thread $id", e)
                    }
                }
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
        viewModelScope.launch {
            state
                .map { s -> if (s is EmailDetailState.Success) s.emails.map { it.id to it.body } else emptyList() }
                .distinctUntilChanged()
                .collect { idsAndBodies ->
                    val currentIds = idsAndBodies.map { it.first }.toSet()
                    val decrypted = _decryptedBodies.value.filterKeys { it in currentIds }.toMutableMap()
                    for ((id, body) in idsAndBodies) {
                        if (decrypted.containsKey(id)) continue
                        try {
                            val isPgp = withContext(Dispatchers.Default) { pgpManager.isPgpMessage(body) }
                            if (isPgp) {
                                val result = withContext(Dispatchers.Default) { pgpManager.decryptBody(body) }
                                if (result != null) {
                                    decrypted[id] = result
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("EmailDetailVM", "PGP processing failed for $id", e)
                        }
                    }
                    _decryptedBodies.value = decrypted
                }
        }
    }

    fun toggleStar() {
        val currentId = _threadId.value
        if (currentId.isEmpty()) return
        viewModelScope.launch {
            repository.toggleStar(currentId, isStarred.value)
        }
    }

    fun markUnread(onComplete: () -> Unit) {
        val currentId = _threadId.value
        if (currentId.isEmpty()) return
        viewModelScope.launch {
            repository.markThreadAsUnread(currentId)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun archiveThread(onComplete: () -> Unit) {
        val currentId = _threadId.value
        if (currentId.isEmpty()) return
        viewModelScope.launch {
            repository.archiveThread(currentId)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun trashThread(onComplete: () -> Unit) {
        val currentId = _threadId.value
        if (currentId.isEmpty()) return
        viewModelScope.launch {
            repository.deleteThread(currentId)
            withContext(Dispatchers.Main) { onComplete() }
        }
    }

    fun toggleEmailStar(emailId: String, isStarred: Boolean) {
        val currentId = _threadId.value
        if (currentId.isEmpty()) return
        viewModelScope.launch {
            repository.toggleEmailStar(emailId, isStarred, accountId, currentId)
        }
    }

    fun archiveEmail(emailId: String, accountId: String, threadId: String) {
        viewModelScope.launch {
            repository.archiveEmail(emailId, accountId, threadId)
        }
    }

    fun trashEmail(emailId: String, accountId: String, threadId: String) {
        viewModelScope.launch {
            repository.trashEmail(emailId, accountId, threadId)
        }
    }

    suspend fun fetchAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return repository.getAttachmentBytes(messageId, attachmentId)
    }
}
