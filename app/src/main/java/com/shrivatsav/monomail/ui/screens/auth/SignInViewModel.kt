package com.shrivatsav.monomail.ui.screens.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.auth.SignInResult
import com.shrivatsav.monomail.auth.UserProfile
import android.content.Intent
import android.app.Activity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SignInState {
    object Idle    : SignInState()
    object Loading : SignInState()
    data class Success(val profile: UserProfile)  : SignInState()
    data class NeedsConsent(val intent: Intent)    : SignInState()
    data class Error(val message: String)          : SignInState()
}

class SignInViewModel(private val authManager: AuthManager) : ViewModel() {

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
            _state.value = when (val result = authManager.signIn(context)) {
                is SignInResult.Success      -> SignInState.Success(result.profile)
                is SignInResult.NeedsConsent -> SignInState.NeedsConsent(result.intent)
                is SignInResult.Failure      -> SignInState.Error(
                    result.error.message ?: "Sign in failed"
                )
            }
        }
    }

    fun signInMicrosoft(activity: Activity) {
        viewModelScope.launch {
            _state.value = SignInState.Loading
            
            // Re-initialize just in case it failed earlier
            val initialized = authManager.microsoftAuthManager.initialize()
            if (!initialized) {
                _state.value = SignInState.Error("Failed to initialize Microsoft login")
                return@launch
            }
            
            _state.value = when (val result = authManager.microsoftAuthManager.signIn(activity)) {
                is SignInResult.Success -> {
                    // Update auth manager state
                    authManager.addAccount(result.profile)
                    authManager.switchAccount(result.profile.id) // To add it or switch
                    SignInState.Success(result.profile)
                }
                is SignInResult.NeedsConsent -> SignInState.Error("Consent needed")
                is SignInResult.Failure -> SignInState.Error(
                    result.error.message ?: "Microsoft sign in failed"
                )
            }
        }
    }

    /** Called after user grants Gmail permission via the consent screen. */
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
}