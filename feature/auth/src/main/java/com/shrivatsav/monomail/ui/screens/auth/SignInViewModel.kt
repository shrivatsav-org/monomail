package com.shrivatsav.monomail.ui.screens.auth
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.SignInResult
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.model.InboxTab
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
sealed class SignInState {
    object Idle    : SignInState()
    object Loading : SignInState()
    data class Success(val profile: UserProfile)  : SignInState()
    data class NeedsConsent(val intent: Intent)    : SignInState()
    data class Error(val message: String)          : SignInState()
}
@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val emailRepository: EmailRepository
) : ViewModel() {
    private val _state = MutableStateFlow<SignInState>(SignInState.Idle)
    val state: StateFlow<SignInState> = _state.asStateFlow()
    init {
        viewModelScope.launch {
            authManager.microsoftAuthManager.initialize()
        }
    }
    fun signIn(context: Context) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            when (val result = authManager.signIn(context)) {
                is SignInResult.Success -> {
                    val refreshResult = emailRepository.refreshInbox(InboxTab.INBOX)
                    if (refreshResult.isFailure) {
                        android.util.Log.w("SignInViewModel", "Initial inbox refresh failed after sign-in", refreshResult.exceptionOrNull())
                    }
                    _state.value = SignInState.Success(result.profile)
                }
                is SignInResult.NeedsConsent -> _state.value = SignInState.NeedsConsent(result.intent)
                is SignInResult.Failure -> _state.value = SignInState.Error(
                    result.error.message ?: "Sign in failed"
                )
            }
        }
    }
    fun signInMicrosoft(activity: Activity) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            val initError = authManager.microsoftAuthManager.initialize()
            if (initError != null) {
                _state.value = SignInState.Error("Microsoft login failed: $initError")
                return@launch
            }
            _state.value = when (val result = authManager.microsoftAuthManager.signIn(activity)) {
                is SignInResult.Success -> {
                    authManager.addAccount(result.profile)
                    authManager.switchAccount(result.profile.id)
                    val refreshResult = emailRepository.refreshInbox(InboxTab.INBOX)
                    if (refreshResult.isFailure) {
                        android.util.Log.w("SignInViewModel", "Initial inbox refresh failed after Microsoft sign-in", refreshResult.exceptionOrNull())
                    }
                    SignInState.Success(result.profile)
                }
                is SignInResult.NeedsConsent -> SignInState.Error("Consent needed")
                is SignInResult.Failure -> SignInState.Error(
                    result.error.message ?: "Microsoft sign in failed"
                )
            }
        }
    }
    fun onConsentResult(context: Context) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            _state.value = when (val result = authManager.handleConsentResult(context)) {
                is SignInResult.Success      -> SignInState.Success(result.profile)
                is SignInResult.NeedsConsent -> SignInState.Error("Permission still required")
                is SignInResult.Failure      -> SignInState.Error(
                    result.error.message ?: "Failed to get Gmail access"
                )
            }
        }
    }

    fun getCustomGoogleClientId(context: Context): String {
        return com.shrivatsav.monomail.security.SecurityUtil.getGoogleClientId(context)
    }

    fun saveCustomGoogleClientId(context: Context, clientId: String) {
        com.shrivatsav.monomail.security.SecurityUtil.saveCustomGoogleClientId(context, clientId)
    }

    fun clearCustomGoogleClientId(context: Context) {
        com.shrivatsav.monomail.security.SecurityUtil.clearCustomGoogleClientId(context)
    }
}
