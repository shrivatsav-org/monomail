package com.shrivatsav.monomail.push
import com.shrivatsav.monomail.core.data.push.PushNotificationManager

import android.content.Context
import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.auth.UserProfile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pushBackendClient: PushBackendClient,
    private val accountManager: AccountManager
) : PushNotificationManager {

    override suspend fun registerForPushNotifications(account: UserProfile) {
        if (account.provider != "gmail" && account.provider != "outlook") {
            Log.i("PushManager", "Push notifications not supported for provider: ${account.provider}")
            return
        }
        try {
            val fcmToken = FirebaseMessaging.getInstance().token.await()
            val result = pushBackendClient.registerDevice(
                accountId = account.id,
                email = account.email,
                fcmToken = fcmToken,
                provider = account.provider,
                accessToken = account.accessToken
            )
            if (result.isSuccess) {
                Log.i("PushManager", "Successfully registered device for push notifications (${account.email})")
            } else {
                Log.e("PushManager", "Failed to register device with push backend, falling back to WorkManager sync", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e("PushManager", "Failed to retrieve FCM token, falling back to WorkManager sync", e)
        }
    }

    override suspend fun unregisterForPushNotifications(accountId: String) {
        Log.i("PushManager", "Unregistered push notifications for account: $accountId")
    }

    override suspend fun onTokenRefresh(newToken: String) {
        try {
            val accounts = accountManager.getAccounts()
            for (account in accounts) {
                if (account.provider == "gmail" || account.provider == "outlook") {
                    pushBackendClient.registerDevice(
                        accountId = account.id,
                        email = account.email,
                        fcmToken = newToken,
                        provider = account.provider,
                        accessToken = account.accessToken
                    )
                }
            }
            Log.i("PushManager", "Successfully updated backend with refreshed FCM token")
        } catch (e: Exception) {
            Log.e("PushManager", "Failed to handle FCM token refresh", e)
        }
    }
}
