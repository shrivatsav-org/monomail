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
class MicrosoftAuthManager(private val context: Context, private val accountManager: AccountManager) {
    private var msalApp: com.microsoft.identity.client.IMultipleAccountPublicClientApplication? = null
    private val scopes = arrayOf("User.Read", "Mail.Read", "Mail.ReadWrite", "Mail.Send")
    suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        if (msalApp != null) {
            continuation.resume(true)
            return@suspendCancellableCoroutine
        }
        PublicClientApplication.createMultipleAccountPublicClientApplication(
            context,
            R.raw.msal_config,
            object : IPublicClientApplication.IMultipleAccountApplicationCreatedListener {
                override fun onCreated(application: com.microsoft.identity.client.IMultipleAccountPublicClientApplication) {
                    msalApp = application
                    continuation.resume(true)
                }
                override fun onError(exception: MsalException) {
                    android.util.Log.e("MicrosoftAuth", "MSAL init failed", exception)
                    continuation.resume(false)
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
    suspend fun getAccessTokenSilently(accountId: String): String? = suspendCancellableCoroutine { continuation ->
        val app = msalApp
        if (app == null) {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val msalAccountId = accountId.removePrefix("outlook_")
        app.getAccount(
            msalAccountId,
            object : com.microsoft.identity.client.IMultipleAccountPublicClientApplication.GetAccountCallback {
                override fun onTaskCompleted(account: com.microsoft.identity.client.IAccount?) {
                    if (account != null) {
                        val authority = account.authority ?: "https://login.microsoftonline.com/common"
                        app.acquireTokenSilentAsync(
                            scopes,
                            account,
                            authority,
                            object : AuthenticationCallback {
                                override fun onSuccess(authenticationResult: IAuthenticationResult) {
                                    continuation.resume(authenticationResult.accessToken)
                                }
                                override fun onError(exception: MsalException) {
                                    continuation.resume(null)
                                }
                                override fun onCancel() {
                                    continuation.resume(null)
                                }
                            }
                        )
                    } else {
                        continuation.resume(null)
                    }
                }
                override fun onError(exception: MsalException) {
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
