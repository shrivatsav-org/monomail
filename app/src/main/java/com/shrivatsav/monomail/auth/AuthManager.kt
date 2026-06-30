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
    private val pushNotificationManager: PushNotificationManager
) {
    val microsoftAuthManager = MicrosoftAuthManager(context, accountManager)
    companion object {
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.modify"
    }
    private val credentialManager = CredentialManager.create(context)
    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()
    private var _userProfile: UserProfile? = null
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
        try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w("AuthManager", "registerForPushNotifications failed", e) }
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
    private suspend fun refreshCurrentToken() {
        val profile = _userProfile ?: return
        try {
            if (profile.provider == "gmail") {
                val newToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.getToken(
                        context,
                        Account(profile.email, "com.google"),
                        GMAIL_SCOPE
                    )
                }
                if (newToken != profile.accessToken) {
                    val updated = profile.copy(accessToken = newToken)
                    updateAccessToken(updated)
                }
            } else if (profile.provider == "outlook") {
                val initError = microsoftAuthManager.initialize()
                if (initError != null) {
                    notifyReauthRequired(profile.email, profile.provider)
                    return
                }
                val newToken = microsoftAuthManager.getAccessTokenSilently(profile.id)
                if (newToken != null && newToken != profile.accessToken) {
                    val updated = profile.copy(accessToken = newToken)
                    updateAccessToken(updated)
                } else if (newToken == null) {
                    // Silent token refresh failed — notify user to re-authenticate
                    notifyReauthRequired(profile.email, profile.provider)
                }
            }
        } catch (e: UserRecoverableAuthException) {
            notifyReauthRequired(profile.email, profile.provider)
        } catch (e: Exception) {
            android.util.Log.w("AuthManager", "refreshCurrentToken failed", e)
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
            val idToken     = googleIdTokenCredential.idToken
            requestAccessToken(activityContext, email, displayName, photoUrl, idToken)
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
        val profile = _userProfile
            ?: return SignInResult.Failure(IllegalStateException("No profile available"))
        return requestAccessToken(
            activityContext, profile.email, profile.displayName,
            profile.photoUrl, "" 
        )
    }
    suspend fun getAccounts(): List<UserProfile> = accountManager.getAccounts()
    suspend fun addAccount(profile: UserProfile) {
        accountManager.addAccount(profile)
        try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w("AuthManager", "registerForPushNotifications failed", e) }
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
            } catch (e: Exception) { }
        }
        accountManager.removeAccount(active.id)
        try { pushNotificationManager.unregisterForPushNotifications(active.id) } catch (e: Exception) { }
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
            try { pushNotificationManager.unregisterForPushNotifications(it.id) } catch (e: Exception) { }
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
        photoUrl: String?,
        idToken: String
    ): SignInResult {
        return try {
            var accessToken = withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(
                    activityContext,
                    Account(email, "com.google"),
                    GMAIL_SCOPE
                )
            }
            val responseCode = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://gmail.googleapis.com/gmail/v1/users/me/profile")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.responseCode
                } catch (e: Exception) {
                    throw e
                }
            }
            if (responseCode == 401 || responseCode == 403) {
                accessToken = withContext(Dispatchers.IO) {
                    GoogleAuthUtil.clearToken(activityContext, accessToken)
                    GoogleAuthUtil.getToken(
                        activityContext,
                        Account(email, "com.google"),
                        GMAIL_SCOPE
                    )
                }
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
            try { pushNotificationManager.registerForPushNotifications(profile) } catch (e: Exception) { android.util.Log.w("AuthManager", "registerForPushNotifications failed", e) }
            SignInResult.Success(profile)
        } catch (e: UserRecoverableAuthException) {
            _userProfile = UserProfile(
                id = "gmail_$email", displayName = displayName, email = email,
                photoUrl = photoUrl, accessToken = "", provider = "gmail", refreshToken = ""
            )
            val consentIntent = e.intent
            if (consentIntent != null) {
                SignInResult.NeedsConsent(consentIntent)
            } else {
                SignInResult.Failure(e)
            }
        } catch (e: Exception) {
            SignInResult.Failure(e)
        }
    }
}