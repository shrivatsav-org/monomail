package com.shrivatsav.monomail.auth
import android.accounts.Account
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import com.shrivatsav.monomail.push.PushNotificationManager

data class ReauthInfo(val email: String, val provider: String)
sealed class SignInResult {
    data class Success(val profile: UserProfile) : SignInResult()
    data class NeedsConsent(val intent: Intent)  : SignInResult()
    data class Failure(val error: Exception)     : SignInResult()
}
class AuthManager(
    private val context: Context,
    private val accountManager: AccountManager,
    private val pushNotificationManager: PushNotificationManager,
    private val database: com.shrivatsav.monomail.data.local.AppDatabase
) {
    companion object {
        private const val TAG = "AuthManager"
        private const val PUSH_REGISTRATION_FAILED = "registerForPushNotifications failed"
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.modify"
        private const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
    val microsoftAuthManager = MicrosoftAuthManager(context, accountManager)
    private val credentialManager = CredentialManager.create(context)
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()
    private var _userProfile: UserProfile? = null
    private var _pendingConsentProfile: UserProfile? = null
    val currentUser: UserProfile? get() = _userProfile
    val accountsFlow = accountManager.accountsFlow
    val activeAccountFlow = accountManager.activeAccountFlow
    private val _reauthNeeded = MutableStateFlow<ReauthInfo?>(null)
    val reauthNeeded: StateFlow<ReauthInfo?> = _reauthNeeded.asStateFlow()
    fun notifyReauthRequired(email: String, provider: String) {
        _reauthNeeded.value = ReauthInfo(email, provider)
    }
    fun dismissReauth() {
        _reauthNeeded.value = null
    }
    suspend fun restoreSession(): Boolean {
        val profile = accountManager.getActiveAccount() ?: return false
        _userProfile = profile
        _isSignedIn.value = true
        refreshCurrentToken()
        try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w(TAG, PUSH_REGISTRATION_FAILED, e) }
        return true
    }

    /**
     * Fast path for cold start: reads the stored profile and sets signed-in state
     * without blocking on token refresh or push registration.
     * Returns true if a previously-signed-in account exists.
     */
    suspend fun restoreSessionQuick(): Boolean {
        val profile = accountManager.getActiveAccount() ?: return false
        _userProfile = profile
        _isSignedIn.value = true
        return true
    }
    suspend fun forceRefreshToken(email: String) {
        val target = accountManager.getAccounts().find { it.email.equals(email, ignoreCase = true) } ?: return
        if (target.provider == "gmail") {
            withContext(Dispatchers.IO) {
                try { GoogleAuthUtil.clearToken(context, target.accessToken) } catch (e: Exception) {}
            }
        } else if (target.provider == "outlook") {
            // Outlook SDK typically handles its own token clearing, but we can force interactive if needed.
            // For now, rely on refreshOutlookToken.
        }
        
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                val refreshed = when (target.provider) {
                    "gmail" -> refreshGmailToken(target)
                    "outlook" -> refreshOutlookToken(target, attempt)
                    else -> return
                }
                if (refreshed) {
                    if (_userProfile?.id == target.id) {
                        _userProfile = accountManager.getActiveAccount()
                    }
                    return
                }
            } catch (e: UserRecoverableAuthException) {
                notifyReauthRequired(target.email, target.provider)
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) kotlinx.coroutines.delay(1000L * (1 shl attempt))
            }
        }
        handleRefreshFailure(target, lastException)
    }

    private suspend fun refreshCurrentToken() {
        val profile = _userProfile ?: return
        var lastException: Exception? = null
        repeat(3) { attempt ->
            try {
                val refreshed = when (profile.provider) {
                    "gmail" -> refreshGmailToken(profile)
                    "outlook" -> refreshOutlookToken(profile, attempt)
                    else -> return
                }
                if (refreshed) return
            } catch (e: UserRecoverableAuthException) {
                notifyReauthRequired(profile.email, profile.provider)
                return
            } catch (e: Exception) {
                lastException = e
                if (attempt < 2) {
                    kotlinx.coroutines.delay(1000L * (1 shl attempt))
                }
            }
        }
        handleRefreshFailure(profile, lastException)
    }

    private suspend fun refreshGmailToken(profile: UserProfile): Boolean {
        val newToken = withContext(Dispatchers.IO) {
            GoogleAuthUtil.getToken(context, Account(profile.email, GOOGLE_ACCOUNT_TYPE), GMAIL_SCOPE)
        }
        if (newToken != profile.accessToken) {
            updateAccessToken(profile.copy(accessToken = newToken))
        }
        return true
    }

    private suspend fun refreshOutlookToken(profile: UserProfile, attempt: Int): Boolean {
        val initError = microsoftAuthManager.initialize()
        if (initError != null) {
            if (attempt >= 2) {
                notifyReauthRequired(profile.email, profile.provider)
                return true
            }
            kotlinx.coroutines.delay(1000L * (1 shl attempt))
            return false
        }
        val newToken = microsoftAuthManager.getAccessTokenSilently(profile.id)
        if (newToken != null) {
            if (newToken != profile.accessToken) {
                updateAccessToken(profile.copy(accessToken = newToken))
            }
            return true
        }
        if (attempt >= 2) {
            notifyReauthRequired(profile.email, profile.provider)
            return true
        }
        kotlinx.coroutines.delay(1000L * (1 shl attempt))
        return false
    }

    private fun handleRefreshFailure(profile: UserProfile, lastException: Exception?) {
        when (lastException) {
            is java.net.SocketTimeoutException,
            is java.net.UnknownHostException -> {
                android.util.Log.w(TAG, "Transient network error after 3 retries", lastException)
            }
            else -> {
                android.util.Log.w(TAG, "Token refresh failed after 3 retries", lastException)
                notifyReauthRequired(profile.email, profile.provider)
            }
        }
    }
    suspend fun signIn(activityContext: Context): SignInResult {
        return try {
            val clientId = com.shrivatsav.monomail.security.SecurityUtil.getGoogleClientId(activityContext)
            if (clientId.isBlank()) {
                return SignInResult.Failure(Exception("Google Client ID is not configured. Please set your API key first."))
            }
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(clientId)
                .setAutoSelectEnabled(false)
                .build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val googleIdTokenCredential = GoogleIdTokenCredential
                .createFrom(result.credential.data)
            val email       = googleIdTokenCredential.id
            val displayName = googleIdTokenCredential.displayName ?: "User"
            val photoUrl    = googleIdTokenCredential.profilePictureUri?.toString()
            requestAccessToken(activityContext, email, displayName, photoUrl)
        } catch (e: GetCredentialException) {
            SignInResult.Failure(Exception(
                "Google sign-in failed: ${e.message ?: e.type}. " +
                "Make sure a Google account is added in Settings > Accounts."
            ))
        } catch (e: Exception) {
            SignInResult.Failure(e)
        }
    }
    suspend fun handleConsentResult(activityContext: Context): SignInResult {
        val profile = _pendingConsentProfile
            ?: return SignInResult.Failure(IllegalStateException("No pending consent profile"))
        _pendingConsentProfile = null
        return requestAccessToken(
            activityContext, profile.email, profile.displayName,
            profile.photoUrl
        )
    }
    suspend fun getAccounts(): List<UserProfile> = accountManager.getAccounts()
    suspend fun addAccount(profile: UserProfile) {
        try {
            accountManager.addAccount(profile)
        } catch (e: Exception) {
            android.util.Log.e("AuthManager", "addAccount failed for ${profile.email}", e)
            // Propagate so the caller can show an error to the user
            throw e
        }
        try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w(TAG, PUSH_REGISTRATION_FAILED, e) }
    }
    suspend fun switchAccount(accountId: String) {
        accountManager.setActiveAccountId(accountId)
        val profile = accountManager.getActiveAccount()
        if (profile != null) {
            _userProfile = profile
            _isSignedIn.value = true
        } else {
            _isSignedIn.value = false
            _userProfile = null
        }
    }
    suspend fun removeAccount(accountId: String) {
        val allAccounts = accountManager.getAccounts()
        val target = allAccounts.find { it.id == accountId } ?: return
        if (target.provider == "outlook") {
            try { microsoftAuthManager.signOut(target.id) } catch (e: Exception) { android.util.Log.w(TAG, "Outlook signOut failed during removeAccount for ${target.id}", e) }
        }
        accountManager.removeAccount(accountId)
        try { pushNotificationManager.unregisterForPushNotifications(target.id) } catch (e: Exception) { android.util.Log.w(TAG, "push unregister failed during removeAccount for ${target.id}", e) }
        try {
            database.emailDao().clearForAccount(accountId)
            database.threadDao().clearForAccount(accountId)
            database.pendingActionDao().clearForAccount(accountId)
            database.scheduledMessageDao().clearForAccount(accountId)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to clear database for $accountId", e)
        }
        val remaining = accountManager.getAccounts()
        if (remaining.isNotEmpty()) {
            val newActive = if (_userProfile?.id == accountId) remaining.first() else _userProfile
            if (newActive != null) {
                _userProfile = newActive
                _isSignedIn.value = true
                accountManager.setActiveAccountId(newActive.id)
            }
        } else {
            _isSignedIn.value = false
            _userProfile = null
        }
    }
    suspend fun signOutActiveAccount() {
        val active = _userProfile ?: return
        if (active.provider == "gmail") {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "clearCredentialState failed", e)
            }
        } else if (active.provider == "outlook") {
            try {
                microsoftAuthManager.signOut(active.id)
            } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Outlook signOut failed for ${active.id}", e)
            }
        }
        accountManager.removeAccount(active.id)
        try { pushNotificationManager.unregisterForPushNotifications(active.id) } catch (e: Exception) {
            android.util.Log.w("AuthManager", "push unregister failed during signOut for ${active.id}", e)
        }
        try {
            database.emailDao().clearForAccount(active.id)
            database.threadDao().clearForAccount(active.id)
            database.pendingActionDao().clearForAccount(active.id)
            database.scheduledMessageDao().clearForAccount(active.id)
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to clear database for ${active.id}", e)
        }
        val newActive = accountManager.getActiveAccount()
        if (newActive != null) {
            _userProfile = newActive
            _isSignedIn.value = true
        } else {
            _isSignedIn.value = false
            _userProfile = null
        }
    }
    suspend fun signOutAll() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: Exception) {
            android.util.Log.w("AuthManager", "signOutAll clearCredentialState failed", e)
        }
        val accounts = accountManager.getAccounts()
        accounts.forEach {
            try { pushNotificationManager.unregisterForPushNotifications(it.id) } catch (e: Exception) {
                android.util.Log.w("AuthManager", "push unregister failed during signOutAll for ${it.id}", e)
            }
        }
        accounts.filter { it.provider == "outlook" }.forEach {
            try { microsoftAuthManager.signOut(it.id) } catch (e: Exception) {
                android.util.Log.w("AuthManager", "Outlook signOut failed for ${it.id}", e)
            }
        }
        accountManager.clearAll()
        _isSignedIn.value = false
        _userProfile = null
    }
    fun isUserSignedIn(): Boolean = _isSignedIn.value
    suspend fun updateAccessToken(updated: UserProfile) {
        _userProfile = updated
        accountManager.updateAccountToken(updated.id, updated.accessToken)
    }
    private suspend fun requestAccessToken(
        activityContext: Context,
        email: String,
        displayName: String,
        photoUrl: String?
    ): SignInResult {
        return try {
            var accessToken = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(
                    activityContext,
                    Account(email, GOOGLE_ACCOUNT_TYPE),
                    GMAIL_SCOPE
                )
            }
            val responseCode = withContext(Dispatchers.IO) {
                val url = java.net.URI("https://gmail.googleapis.com/gmail/v1/users/me/profile").toURL()
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.responseCode
            }
            if (responseCode == 401 || responseCode == 403) {
                accessToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.clearToken(activityContext, accessToken)
                    GoogleAuthUtil.getToken(
                        activityContext,
                        Account(email, GOOGLE_ACCOUNT_TYPE),
                        GMAIL_SCOPE
                    )
                }
            } else if (responseCode == 404) {
                return SignInResult.Failure(Exception(
                    "Account not found. It may have been deleted or disabled."
                ))
            }
            val profile = UserProfile(
                id          = "gmail_$email", 
                displayName = displayName,
                email       = email,
                photoUrl    = photoUrl,
                accessToken = accessToken,
                provider    = "gmail",
                refreshToken = ""
            )
            _userProfile = profile
            _isSignedIn.value = true
            accountManager.addAccount(profile)
            accountManager.setActiveAccountId(profile.id)
            try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w(TAG, PUSH_REGISTRATION_FAILED, e) }
            SignInResult.Success(profile)
        } catch (e: UserRecoverableAuthException) {
            val consentIntent = e.intent
            if (consentIntent != null) {
                _pendingConsentProfile = UserProfile(
                    id = "gmail_$email", displayName = displayName, email = email,
                    photoUrl = photoUrl, accessToken = "", provider = "gmail", refreshToken = ""
                )
                SignInResult.NeedsConsent(consentIntent)
            } else {
                SignInResult.Failure(e)
            }
        } catch (e: Exception) {
            SignInResult.Failure(e)
        }
    }
}