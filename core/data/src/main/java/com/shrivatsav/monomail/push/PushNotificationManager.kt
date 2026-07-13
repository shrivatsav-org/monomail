package com.shrivatsav.monomail.push

import com.shrivatsav.monomail.auth.UserProfile

interface PushNotificationManager {
    suspend fun registerForPushNotifications(account: UserProfile)
    suspend fun unregisterForPushNotifications(accountId: String)
    suspend fun onTokenRefresh(newToken: String)
}
