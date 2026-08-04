package com.shrivatsav.monomail.core.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.core.data.settings.NotificationImportance
import com.shrivatsav.monomail.core.data.settings.NotificationProfile
import com.shrivatsav.monomail.core.data.settings.NotificationSound

/**
 * Owns the per-account new-email notification channel lifecycle.
 *
 * Channel properties (name, importance, sound, vibration, badge) are immutable
 * after creation, so any change requires delete + recreate. The name is the
 * account email — never the first sender's name. Recreating also repairs
 * channels created by older builds that were mislabeled with a sender name.
 */
object NotificationChannelManager {

    fun channelImportance(importance: NotificationImportance): Int = when (importance) {
        NotificationImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationImportance.URGENT -> NotificationManager.IMPORTANCE_HIGH
        NotificationImportance.SILENT -> NotificationManager.IMPORTANCE_LOW
    }

    /**
     * Creates (or repairs) the new-email channel for [account] so it matches
     * [profile]. Deletes and recreates when any immutable property differs.
     */
    fun ensureNewEmailChannel(context: Context, account: UserProfile, profile: NotificationProfile) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = channelIdForAccount(account.id)
        val existing = nm.getNotificationChannel(channelId)

        val wantsSilentSound = profile.sound == NotificationSound.SILENT
        val wantsImportance = channelImportance(profile.importance)

        val matches =
            existing != null &&
                existing.name == account.email &&
                existing.importance == wantsImportance &&
                (existing.sound == null) == !wantsSilentSound &&
                existing.shouldVibrate() == profile.vibrate &&
                existing.canShowBadge() == profile.badge
        if (matches) return

        if (existing != null) nm.deleteNotificationChannel(channelId)

        val channel = NotificationChannel(channelId, account.email, wantsImportance).apply {
            description = "Notifications for ${account.email}"
            setShowBadge(profile.badge)
            if (wantsSilentSound) {
                setSound(null, null)
            }
            if (!profile.vibrate) {
                enableVibration(false)
            }
        }
        nm.createNotificationChannel(channel)
    }

    /** Deletes an account's channel (called when the account is removed). */
    fun deleteChannel(context: Context, accountId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel(channelIdForAccount(accountId))
    }
}
