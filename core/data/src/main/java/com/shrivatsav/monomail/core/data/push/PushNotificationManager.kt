package com.shrivatsav.monomail.core.data.push

import com.shrivatsav.monomail.core.data.auth.UserProfile

interface PushNotificationManager {
    suspend fun registerForPushNotifications(account: UserProfile)
    suspend fun unregisterForPushNotifications(accountId: String)
    suspend fun onTokenRefresh(newToken: String)
}
