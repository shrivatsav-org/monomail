package com.shrivatsav.monomail.auth
import android.app.Activity
import android.content.Context
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.MultipleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.shrivatsav.monomail.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
@Suppress("DEPRECATION")
class MicrosoftAuthManager(private val context: Context, private val accountManager: AccountManager) {
    private var msalApp: com.microsoft.identity.client.IMultipleAccountPublicClientApplication? = null
    private val scopes = arrayOf("User.Read", "Mail.Read", "Mail.ReadWrite", "Mail.Send")
    suspend fun initialize(): String? = suspendCancellableCoroutine { continuation ->
        if (msalApp != null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        PublicClientApplication.createMultipleAccountPublicClientApplication(
            context,
            R.raw.msal_config,
            object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                override fun onCreated(application: com.microsoft.identity.client.IMultipleAccountPublicClientApplication) {
                    msalApp = application
                    continuation.resume(null)
                }
                override fun onError(exception: MsalException) {
                    android.util.Log.e("MicrosoftAuth", "MSAL init failed", exception)
                    continuation.resume(exception.message ?: "MSAL initialization failed")
                }
            }
        )
    }
    suspend fun signIn(activity: Activity): SignInResult = suspendCancellableCoroutine { continuation ->
        if (msalApp == null) {
            continuation.resume(SignInResult.Failure(IllegalStateException("MSAL not initialized")))
            return@suspendCancellableCoroutine
        }
        msalApp?.acquireToken(
            activity,
            scopes,
            object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                    val account = authenticationResult.account
                    val profile = UserProfile(
                        id = "outlook_${account.id}",
                        displayName = account.username ?: "Outlook User", 
                        email = account.username ?: "",
                        photoUrl = null,
                        accessToken = authenticationResult.accessToken,
                        provider = "outlook",
                        refreshToken = "" 
                    )
                    continuation.resume(SignInResult.Success(profile))
                }
                override fun onError(exception: MsalException) {
                    continuation.resume(SignInResult.Failure(exception))
                }
                override fun onCancel() {
                    continuation.resume(SignInResult.Failure(Exception("Sign in cancelled")))
                }
            }
        )
    }
    suspend fun getAccessTokenSilently(accountId: String): String? {
        // Ensure MSAL is initialized before attempting silent token acquisition.
        // This handles cases where the process was killed/recreated or the app
        // cold-started without an explicit initialize() call.
        if (msalApp == null) {
            val initError = initialize()
            if (initError != null) {
                android.util.Log.w("MicrosoftAuth", "Auto-init failed during silent token: $initError")
                return null
            }
        }
        return suspendCancellableCoroutine { continuation ->
        val app = msalApp
        if (app == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val msalAccountId = accountId.removePrefix("outlook_")
        // Attempt by prefixed client ID first, then fall back to iterating all
        // known accounts. MSAL internal identifiers can shift across app restarts
        // or account re-adds.
        app.getAccount(
            msalAccountId,
            object : com.microsoft.identity.client.IMultipleAccountPublicClientApplication.GetAccountCallback {
                override fun onTaskCompleted(account: com.microsoft.identity.client.IAccount?) {
                    if (account != null) {
                        acquireTokenForAccount(app, account, accountId, continuation)
                    } else {
                        // Fallback: try to find the account by iterating all accounts
                        android.util.Log.w("MicrosoftAuth", "Direct lookup failed for $accountId, trying fallback by email")
                        try {
                            app.getAccounts(object : IPublicClientApplication.LoadAccountsCallback {
                                override fun onTaskCompleted(accounts: List<com.microsoft.identity.client.IAccount>) {
                                    val match = accounts.firstOrNull { acct ->
                                        acct.username?.contains(msalAccountId, ignoreCase = true) == true ||
                                                msalAccountId.contains(acct.username ?: "", ignoreCase = true)
                                    }
                                    if (match != null) {
                                        android.util.Log.i("MicrosoftAuth", "Found MSAL account by email fallback: ${match.username}")
                                        acquireTokenForAccount(app, match, accountId, continuation)
                                    } else {
                                        android.util.Log.w("MicrosoftAuth", "Fallback also failed — no MSAL account found for $accountId")
                                        continuation.resume(null)
                                    }
                                }
                                override fun onError(exception: MsalException) {
                                    android.util.Log.w("MicrosoftAuth", "getAccounts fallback failed for $accountId", exception)
                                    continuation.resume(null)
                                }
                            })
                        } catch (e: Exception) {
                            android.util.Log.w("MicrosoftAuth", "getAccounts API not available", e)
                            continuation.resume(null)
                        }
                    }
                }
                override fun onError(exception: MsalException) {
                    android.util.Log.w("MicrosoftAuth", "getAccount failed for $accountId", exception)
                    continuation.resume(null)
                }
            }
        )
        }
    }

    private fun acquireTokenForAccount(
        app: com.microsoft.identity.client.IMultipleAccountPublicClientApplication,
        account: com.microsoft.identity.client.IAccount,
        accountId: String,
        continuation: kotlin.coroutines.Continuation<String?>
    ) {
        val authority = account.authority ?: "https://login.microsoftonline.com/common"
        app.acquireTokenSilentAsync(
            scopes, account, authority,
            object : AuthenticationCallback {
                override fun onSuccess(result: IAuthenticationResult) {
                    continuation.resume(result.accessToken)
                }
                override fun onError(exception: MsalException) {
                    android.util.Log.w("MicrosoftAuth", "Silent token acquisition failed for $accountId", exception)
                    continuation.resume(null)
                }
                override fun onCancel() {
                    android.util.Log.w("MicrosoftAuth", "Silent token acquisition cancelled for $accountId")
                    continuation.resume(null)
                }
            }
        )
    }
    suspend fun signOut(accountId: String) = suspendCancellableCoroutine { continuation ->
        val app = msalApp
        if (app == null) {
            continuation.resume(Unit)
            return@suspendCancellableCoroutine
        }
        val msalAccountId = accountId.removePrefix("outlook_")
        app.getAccount(
            msalAccountId,
            object : com.microsoft.identity.client.IMultipleAccountPublicClientApplication.GetAccountCallback {
                override fun onTaskCompleted(account: com.microsoft.identity.client.IAccount?) {
                    if (account != null) {
                        app.removeAccount(
                            account,
                            object : com.microsoft.identity.client.IMultipleAccountPublicClientApplication.RemoveAccountCallback {
                                override fun onRemoved() {
                                    continuation.resume(Unit)
                                }
                                override fun onError(exception: MsalException) {
                                    continuation.resume(Unit)
                                }
                            }
                        )
                    } else {
                        continuation.resume(Unit)
                    }
                }
                override fun onError(exception: MsalException) {
                    continuation.resume(Unit)
                }
            }
        )
    }
}
