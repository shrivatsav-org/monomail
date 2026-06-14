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

/** Outcome of a sign-in attempt. */
sealed class SignInResult {
    data class Success(val profile: UserProfile) : SignInResult()
    data class NeedsConsent(val intent: Intent)  : SignInResult()
    data class Failure(val error: Exception)     : SignInResult()
}

class AuthManager(
    private val context: Context,
    private val accountManager: AccountManager
) {

    val microsoftAuthManager = MicrosoftAuthManager(context, accountManager)

    companion object {
        val CLIENT_ID = com.shrivatsav.monomail.BuildConfig.GOOGLE_CLIENT_ID
        const val GMAIL_SCOPE = "oauth2:https://www.googleapis.com/auth/gmail.modify"
    }

    private val credentialManager = CredentialManager.create(context)

    private val _isSignedIn = MutableStateFlow(false)
    val isSignedIn: StateFlow<Boolean> = _isSignedIn.asStateFlow()

    private var _userProfile: UserProfile? = null
    val currentUser: UserProfile? get() = _userProfile

    val activeAccountFlow = accountManager.activeAccountFlow

    // ── Restore session on cold start ────────────────────────────────────

    /** Returns true if a persisted session was found and restored. */
    suspend fun restoreSession(): Boolean {
        val profile = accountManager.getActiveAccount() ?: return false
        _userProfile = profile
        _isSignedIn.value = true
        return true
    }

    // ── Sign in ──────────────────────────────────────────────────────────

    suspend fun signIn(activityContext: Context): SignInResult {
        return try {
            // Step 1 — Credential Manager: get identity (id token)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(CLIENT_ID)
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

            // Step 2 — GoogleAuthUtil: get Gmail access token
            requestAccessToken(activityContext, email, displayName, photoUrl, idToken)

        } catch (e: GetCredentialException) {
            SignInResult.Failure(e)
        } catch (e: Exception) {
            SignInResult.Failure(e)
        }
    }

    // ── Handle consent callback ──────────────────────────────────────────

    /** Called after the user grants Gmail permission via the consent screen. */
    suspend fun handleConsentResult(activityContext: Context): SignInResult {
        val profile = _userProfile
            ?: return SignInResult.Failure(IllegalStateException("No profile available"))
        return requestAccessToken(
            activityContext, profile.email, profile.displayName,
            profile.photoUrl, "" // we don't strictly need idToken here
        )
    }

    // ── Account Switching & Sign out ─────────────────────────────────────

    suspend fun getAccounts(): List<UserProfile> = accountManager.getAccounts()

    suspend fun addAccount(profile: UserProfile) {
        accountManager.addAccount(profile)
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
                // Ignore errors
            }
        } else if (active.provider == "outlook") {
            try {
                microsoftAuthManager.signOut(active.id)
            } catch (e: Exception) { }
        }
        
        accountManager.removeAccount(active.id)
        
        // Check if there are other accounts
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
        } catch (e: Exception) { }
        
        val accounts = accountManager.getAccounts()
        accounts.filter { it.provider == "outlook" }.forEach {
            try { microsoftAuthManager.signOut(it.id) } catch (e: Exception) { }
        }
        
        accountManager.clearAll()
        _isSignedIn.value = false
        _userProfile = null
    }

    fun isUserSignedIn(): Boolean = _isSignedIn.value

    /** Called by token-refresh interceptor to update the in-memory profile. */
    suspend fun updateAccessToken(updated: UserProfile) {
        _userProfile = updated
        accountManager.updateAccountToken(updated.id, updated.accessToken)
    }

    // ── Private helpers ──────────────────────────────────────────────────

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

            // Verify the token to ensure the user hasn't revoked consent.
            // GoogleAuthUtil caches tokens, so we might get a token that is no longer valid.
            val responseCode = withContext(Dispatchers.IO) {
                try {
                    val url = java.net.URL("https://gmail.googleapis.com/gmail/v1/users/me/profile")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.setRequestProperty("Authorization", "Bearer $accessToken")
                    connection.responseCode
                } catch (e: Exception) {
                    200 // Ignore network errors, assume valid
                }
            }

            if (responseCode == 401 || responseCode == 403) {
                // Token is revoked or invalid. Clear cache and try again.
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
                id          = "gmail_$email", // Use a provider prefix for multi-account
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

            SignInResult.Success(profile)

        } catch (e: UserRecoverableAuthException) {
            // User hasn't granted Gmail scope yet — save partial profile for retry
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