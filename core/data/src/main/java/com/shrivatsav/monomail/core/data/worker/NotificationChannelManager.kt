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
 * Channel properties (importance, sound, vibration, badge) are immutable after
 * creation, and Android does NOT honor delete + recreate of the same channel id
 * (the recreated channel keeps the original settings — a channel that was once
 * silent stays silent forever). So the channel id is derived from the profile:
 * any profile change maps to a NEW channel id, which is always created with the
 * exact requested settings, and switching back reuses the original channel.
 * Stale channels for the account are deleted once the current one exists.
 */
object NotificationChannelManager {

    fun channelImportance(importance: NotificationImportance): Int = when (importance) {
        NotificationImportance.DEFAULT -> NotificationManager.IMPORTANCE_DEFAULT
        NotificationImportance.URGENT -> NotificationManager.IMPORTANCE_HIGH
        NotificationImportance.SILENT -> NotificationManager.IMPORTANCE_LOW
    }

    /**
     * Stable channel id per (account, profile). Encode every immutable channel
     * property so a changed setting yields a different channel.
     */
    fun channelIdFor(accountId: String, profile: NotificationProfile): String =
        "monomail_${accountId}_${profile.importance.name}_${profile.sound.name}_${profile.vibrate}_${profile.badge}"

    private fun isAccountChannel(id: String, accountId: String): Boolean =
        id == "monomail_$accountId" || id.startsWith("monomail_${accountId}_")

    /**
     * Ensures the channel matching [profile] exists for [account]. Creates it
     * on first use; deletes stale channels (older profile ids and the legacy
     * sender-name-mislabeled channel) afterwards.
     */
    fun ensureNewEmailChannel(context: Context, account: UserProfile, profile: NotificationProfile) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val desiredId = channelIdFor(account.id, profile)
        val existing = nm.getNotificationChannel(desiredId)
        if (existing != null && existing.name == account.email) {
            cleanupStaleChannels(nm, account.id, desiredId)
            return
        }

        val channel = NotificationChannel(desiredId, account.email, channelImportance(profile.importance)).apply {
            description = "Notifications for ${account.email}"
            setShowBadge(profile.badge)
            if (profile.sound == NotificationSound.SILENT) {
                setSound(null, null)
            }
            if (!profile.vibrate) {
                enableVibration(false)
            }
        }
        nm.createNotificationChannel(channel)
        cleanupStaleChannels(nm, account.id, desiredId)
    }

    private fun cleanupStaleChannels(nm: NotificationManager, accountId: String, keepId: String) {
        for (channel in nm.notificationChannels) {
            if (channel.id != keepId && isAccountChannel(channel.id, accountId)) {
                nm.deleteNotificationChannel(channel.id)
            }
        }
    }

    /** Deletes every channel for an account (called when the account is removed). */
    fun deleteChannel(context: Context, accountId: String) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (channel in nm.notificationChannels) {
            if (isAccountChannel(channel.id, accountId)) {
                nm.deleteNotificationChannel(channel.id)
            }
        }
    }
}
