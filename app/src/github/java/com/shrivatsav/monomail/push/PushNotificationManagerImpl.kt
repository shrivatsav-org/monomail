package com.shrivatsav.monomail.push

import android.util.Log
import com.shrivatsav.monomail.auth.UserProfile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PushNotificationManagerImpl @Inject constructor() : PushNotificationManager {
    override suspend fun registerForPushNotifications(account: UserProfile) {
        Log.i("PushManager", "GitHub build: Push notifications disabled. Relying on WorkManager periodic sync.")
    }

    override suspend fun unregisterForPushNotifications(accountId: String) {
        Log.i("PushManager", "GitHub build: unregisterForPushNotifications no-op.")
    }

    override suspend fun onTokenRefresh(newToken: String) {
        Log.i("PushManager", "GitHub build: onTokenRefresh no-op.")
    }
}
