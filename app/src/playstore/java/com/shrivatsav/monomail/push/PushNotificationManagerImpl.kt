package com.shrivatsav.monomail.push

import com.shrivatsav.monomail.BuildConfig
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

            // Gmail: app calls watch API directly — OAuth token stays on device
            if (account.provider == "gmail" && BuildConfig.PUBSUB_TOPIC.isNotBlank()) {
                val watchResult = pushBackendClient.callGmailWatch(account.accessToken, BuildConfig.PUBSUB_TOPIC)
                if (watchResult.isFailure) {
                    Log.w("PushManager", "Gmail watch API call failed (non-fatal)", watchResult.exceptionOrNull())
                }
            }

            // Register FCM token with backend — no OAuth token included
            val result = pushBackendClient.registerDevice(
                accountId = account.id,
                email = account.email,
                fcmToken = fcmToken,
                provider = account.provider
            )
            if (result.isSuccess) {
                Log.i("PushManager", "Successfully registered device for push notifications (${account.email})")
            } else {
                Log.e("PushManager", "Failed to register device with push backend", result.exceptionOrNull())
            }
        } catch (e: Exception) {
            Log.e("PushManager", "Failed to retrieve FCM token", e)
        }
    }

    override suspend fun unregisterForPushNotifications(accountId: String) {
        val account = accountManager.getAccounts().find { it.id == accountId }
        if (account == null) {
            Log.w("PushManager", "Account $accountId not found for unregistration")
            return
        }
        // Stop Gmail push subscription on-device
        if (account.provider == "gmail" && account.accessToken.isNotBlank()) {
            val stopResult = pushBackendClient.callGmailStop(account.accessToken)
            if (stopResult.isFailure) {
                Log.w("PushManager", "Gmail stop failed (non-fatal)", stopResult.exceptionOrNull())
            }
        }
        // Delete FCM mapping from backend
        val result = pushBackendClient.unregisterDevice(accountId = account.id, email = account.email)
        if (result.isSuccess) {
            Log.i("PushManager", "Successfully unregistered push notifications for ${account.email}")
        } else {
            Log.w("PushManager", "Failed to unregister from push backend (non-fatal)", result.exceptionOrNull())
        }
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
                        provider = account.provider
                    )
                }
            }
            Log.i("PushManager", "Successfully updated backend with refreshed FCM token")
        } catch (e: Exception) {
            Log.e("PushManager", "Failed to handle FCM token refresh", e)
        }
    }
}
