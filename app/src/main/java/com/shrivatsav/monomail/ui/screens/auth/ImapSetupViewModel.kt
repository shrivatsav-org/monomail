package com.shrivatsav.monomail.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.provider.imap.ImapAccountConfig
import com.shrivatsav.monomail.data.provider.imap.ImapProvider
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.security.SecurityUtil
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import jakarta.mail.AuthenticationFailedException
import jakarta.mail.MessagingException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class ImapTestState {
    object Idle : ImapTestState()
    object Testing : ImapTestState()
    object Syncing : ImapTestState()
    object Success : ImapTestState()
    data class Error(val message: String) : ImapTestState()
}

class ImapSetupViewModel(
    private val accountManager: AccountManager,
    private val authManager: AuthManager,
    private val emailRepository: EmailRepository
) : ViewModel() {

    private val _imapHost = MutableStateFlow("")
    val imapHost = _imapHost.asStateFlow()
    fun setImapHost(v: String) = _imapHost.update { v }

    private val _imapPort = MutableStateFlow("993")
    val imapPort = _imapPort.asStateFlow()
    fun setImapPort(v: String) = _imapPort.update { v }

    private val _imapSsl = MutableStateFlow(true)
    val imapSsl = _imapSsl.asStateFlow()
    fun setImapSsl(v: Boolean) = _imapSsl.update { v }

    private val _imapStartTls = MutableStateFlow(false)
    val imapStartTls = _imapStartTls.asStateFlow()
    fun setImapStartTls(v: Boolean) = _imapStartTls.update { v }

    private val _smtpHost = MutableStateFlow("")
    val smtpHost = _smtpHost.asStateFlow()
    fun setSmtpHost(v: String) = _smtpHost.update { v }

    private val _smtpPort = MutableStateFlow("465")
    val smtpPort = _smtpPort.asStateFlow()
    fun setSmtpPort(v: String) = _smtpPort.update { v }

    private val _smtpSsl = MutableStateFlow(true)
    val smtpSsl = _smtpSsl.asStateFlow()
    fun setSmtpSsl(v: Boolean) = _smtpSsl.update { v }

    private val _smtpStartTls = MutableStateFlow(false)
    val smtpStartTls = _smtpStartTls.asStateFlow()
    fun setSmtpStartTls(v: Boolean) = _smtpStartTls.update { v }

    private val _username = MutableStateFlow("")
    val username = _username.asStateFlow()
    fun setUsername(v: String) = _username.update { v }

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()
    fun setPassword(v: String) = _password.update { v }

    private val _displayName = MutableStateFlow("")
    val displayName = _displayName.asStateFlow()
    fun setDisplayName(v: String) = _displayName.update { v }

    private val _testState = MutableStateFlow<ImapTestState>(ImapTestState.Idle)
    val testState: StateFlow<ImapTestState> = _testState.asStateFlow()

    private val _suggestedConfig = MutableStateFlow<ImapAccountConfig?>(null)
    val suggestedConfig = _suggestedConfig.asStateFlow()

    init {
        @OptIn(FlowPreview::class)
        viewModelScope.launch {
            _imapHost.debounce(500).collect { host ->
                if (host.isNotBlank()) {
                    val preset = ImapAccountConfig.presetForHost(host)
                    _suggestedConfig.value = preset
                } else {
                    _suggestedConfig.value = null
                }
            }
        }
    }

    fun applySuggestion(config: ImapAccountConfig) {
        setImapHost(config.imapHost)
        setImapPort(config.imapPort.toString())
        setImapSsl(config.imapSsl)
        setImapStartTls(config.imapStartTls)

        setSmtpHost(config.smtpHost)
        setSmtpPort(config.smtpPort.toString())
        setSmtpSsl(config.smtpSsl)
        setSmtpStartTls(config.smtpStartTls)
        
        _suggestedConfig.value = null
    }

    fun testAndSaveAccount(context: Context, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _testState.value = ImapTestState.Testing

            val config = buildConfig()
            if (config == null) {
                _testState.value = ImapTestState.Error("Invalid configuration (check ports)")
                return@launch
            }

            try {
                // To test connection, we instantiate the provider and call a basic method or just try to connect
                val provider = ImapProvider(config, _password.value, context)
                // Just listing threads from INBOX implicitly connects to the store
                provider.listThreads(com.shrivatsav.monomail.data.provider.EmailFolder.INBOX, 1)
                provider.disconnect()
                _testState.value = ImapTestState.Syncing
                
                // If successful, save the account
                saveAccountInternal(config, onSuccess)
            } catch (e: AuthenticationFailedException) {
                _testState.value = ImapTestState.Error("Wrong username or password")
            } catch (e: MessagingException) {
                if (e.message?.contains("timed out") == true) {
                    _testState.value = ImapTestState.Error("Connection timed out \u2014 check host and port")
                } else {
                    _testState.value = ImapTestState.Error("Could not connect: ${e.message}")
                }
            } catch (e: Exception) {
                val errorMsg = e.message ?: e.javaClass.simpleName
                _testState.value = ImapTestState.Error("Connection failed: $errorMsg")
            }
        }
    }

    private fun saveAccountInternal(config: ImapAccountConfig, onSuccess: () -> Unit) {
        val pass = _password.value
        val name = _displayName.value.ifBlank { "IMAP User" }
        
        viewModelScope.launch {
            val encryptedConfig = SecurityUtil.encryptString(config.toJson())
            val encryptedPassword = SecurityUtil.encryptString(pass)
            
            val profile = UserProfile(
                id = "imap_${config.username}",
                displayName = name,
                email = config.username,
                photoUrl = null,
                accessToken = encryptedConfig,
                provider = "imap",
                refreshToken = encryptedPassword
            )
            
            authManager.addAccount(profile)
            authManager.switchAccount(profile.id)
            emailRepository.refreshInbox(InboxTab.INBOX)
            onSuccess()
        }
    }

    private fun buildConfig(): ImapAccountConfig? {
        val iPort = _imapPort.value.toIntOrNull() ?: return null
        val sPort = _smtpPort.value.toIntOrNull() ?: return null

        return ImapAccountConfig(
            imapHost = _imapHost.value,
            imapPort = iPort,
            imapSsl = _imapSsl.value,
            imapStartTls = _imapStartTls.value,
            smtpHost = _smtpHost.value,
            smtpPort = sPort,
            smtpSsl = _smtpSsl.value,
            smtpStartTls = _smtpStartTls.value,
            username = _username.value
        )
    }
}
