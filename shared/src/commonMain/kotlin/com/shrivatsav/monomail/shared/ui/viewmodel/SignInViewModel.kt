package com.shrivatsav.monomail.shared.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.shared.auth.AuthManager
import com.shrivatsav.monomail.shared.auth.MailProvider
import com.shrivatsav.monomail.shared.auth.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SignInState {
    data object Idle : SignInState()
    data object Loading : SignInState()
    data class Success(val profile: UserProfile) : SignInState()
    data class Error(val message: String) : SignInState()
}

/**
 * Drives the shared PKCE sign-in. The browser redirect happens inside
 * AuthManager via the platform OAuthBrowser, so no Context/Intent/Activity here.
 */
class SignInViewModel(private val authManager: AuthManager) : ViewModel() {
    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()

    fun signIn(provider: MailProvider) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            _state.value = authManager.signIn(provider).fold(
                onSuccess = { SignInState.Success(it) },
                onFailure = { SignInState.Error(it.message ?: "Sign in failed") }
            )
        }
    }

    fun signInGmail() = signIn(MailProvider.GMAIL)
    fun signInOutlook() = signIn(MailProvider.OUTLOOK)

    fun reset() {
        _state.value = SignInState.Idle
    }
}
