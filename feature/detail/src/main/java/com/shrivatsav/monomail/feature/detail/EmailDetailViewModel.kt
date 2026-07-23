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

sealed class EmailDetailState {
    data class Success(val emails: List<Email>, val isRefreshing: Boolean = false, val refreshError: String? = null) :
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
    private val _threadId = MutableStateFlow(savedStateHandle.get<String>("threadId") ?: "")
    
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
            flowOf(EmailDetailState.Success(emptyList(), isRefreshing = false))
        } else {
            combine(
                repository.getThreadEmailsFlow(id),
                _isLoading,
                _error
            ) { emails, isLoading, error ->
                when {
                    emails.isNotEmpty() -> {
                        val needsBodyFetch = emails.any { it.body.isEmpty() }
                        EmailDetailState.Success(emails, isRefreshing = isLoading && needsBodyFetch, refreshError = error)
                    }
                    error != null -> EmailDetailState.Error(error)
                    !isLoading -> EmailDetailState.Error("Email thread not found.")
                    else -> EmailDetailState.Success(emptyList(), isRefreshing = true)
                }
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EmailDetailState.Success(emptyList(), isRefreshing = true)
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

    suspend fun fetchAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return repository.getAttachmentBytes(messageId, attachmentId)
    }
}
