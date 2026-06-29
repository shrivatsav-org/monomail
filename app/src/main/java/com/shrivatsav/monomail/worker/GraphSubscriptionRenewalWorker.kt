package com.shrivatsav.monomail.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.auth.AuthManager
import com.shrivatsav.monomail.push.PushNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class GraphSubscriptionRenewalWorker @AssistedInject constructor(
    private val authManager: AuthManager,
    private val pushNotificationManager: PushNotificationManager,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val accounts = authManager.getAccounts().filter { it.provider == "outlook" }
        if (accounts.isEmpty()) {
            Log.i("GraphRenewalWorker", "No Outlook accounts found for subscription renewal.")
            return Result.success()
        }
        var hasFailure = false
        for (account in accounts) {
            try {
                Log.i("GraphRenewalWorker", "Starting subscription renewal for account ${account.id}")
                val initError = authManager.microsoftAuthManager.initialize()
                if (initError != null) {
                    Log.e("GraphRenewalWorker", "MSAL init failed for ${account.id}: $initError")
                    hasFailure = true
                    continue
                }
                val freshToken = authManager.microsoftAuthManager.getAccessTokenSilently(account.id)
                if (freshToken != null) {
                    val updatedAccount = account.copy(accessToken = freshToken)
                    authManager.updateAccessToken(updatedAccount)
                    pushNotificationManager.registerForPushNotifications(updatedAccount)
                    Log.i("GraphRenewalWorker", "Successfully initiated subscription renewal for ${account.id}")
                } else {
                    Log.e("GraphRenewalWorker", "Silent token refresh failed for ${account.id}")
                    hasFailure = true
                }
            } catch (e: Exception) {
                Log.e("GraphRenewalWorker", "Exception during renewal for ${account.id}", e)
                hasFailure = true
            }
        }
        return if (hasFailure) Result.retry() else Result.success()
    }
}
