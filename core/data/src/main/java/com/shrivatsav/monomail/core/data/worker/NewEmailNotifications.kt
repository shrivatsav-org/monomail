package com.shrivatsav.monomail.core.data.worker

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import androidx.core.text.HtmlCompat
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.core.data.settings.NotificationPreview
import com.shrivatsav.monomail.core.data.settings.NotificationProfile

private const val TAG = "NewEmailNotification"

/** Channel id used for per-account new-email notifications. */
fun channelIdForAccount(accountId: String): String = "monomail_$accountId"

/**
 * Posts the new-email notification for [thread] on [accountId]'s channel,
 * including only the quick actions the user enabled in settings.
 * Used by [EmailSyncWorker] and the Settings "test notification" button.
 */
fun showNewEmailNotification(
    context: Context,
    account: UserProfile,
    thread: com.shrivatsav.monomail.data.model.EmailThread,
    notificationId: Int,
    quickActions: Set<String>,
    profile: NotificationProfile
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        Log.e(TAG, "POST_NOTIFICATIONS permission not granted! Aborting notification display.")
        return
    }
    NotificationChannelManager.ensureNewEmailChannel(context, account, profile)

    val openIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    } ?: Intent()
    val openPendingIntent = PendingIntent.getActivity(
        context, 0, openIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val replyPendingIntent = NotificationActionReceiver.createReplyPendingIntent(
        context = context,
        params = NotificationActionReceiver.ReplyParams(
            accountId = account.id,
            threadId = thread.threadId,
            messageId = thread.latestMessageId,
            subject = thread.subject,
            fromEmail = thread.fromEmail,
            fromName = thread.from,
            notificationId = notificationId
        )
    )
    val replyRemoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
        .setLabel("Reply")
        .build()
    val replyAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_send, "Reply", replyPendingIntent
    ).addRemoteInput(replyRemoteInput).build()

    val archivePendingIntent = NotificationActionReceiver.createArchivePendingIntent(
        context = context,
        accountId = account.id,
        threadId = thread.threadId,
        notificationId = notificationId
    )
    val archiveAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_edit, "Archive", archivePendingIntent
    ).build()

    val deletePendingIntent = NotificationActionReceiver.createDeletePendingIntent(
        context = context,
        accountId = account.id,
        threadId = thread.threadId,
        notificationId = notificationId
    )
    val deleteAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_delete, "Trash", deletePendingIntent
    ).build()

    val snoozePendingIntent = NotificationActionReceiver.createSnoozePendingIntent(
        context = context,
        accountId = account.id,
        threadId = thread.threadId,
        notificationId = notificationId
    )
    val snoozeAction = NotificationCompat.Action.Builder(
        android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent
    ).build()

    val actions = listOf(
        "reply" to replyAction,
        "archive" to archiveAction,
        "delete" to deleteAction,
        "snooze" to snoozeAction
    ).filter { it.first in quickActions }.map { it.second }

    val cleanSnippet = thread.snippet.replace(Regex("\\bOn\\s+[A-Z][a-z]{2},.*?wrote:.*"), "").trim()
    val channelId = channelIdForAccount(account.id)
    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(com.shrivatsav.monomail.core.data.R.drawable.ic_notification_leaf)
        .setContentTitle(thread.from)
        .setContentText(thread.subject)
        .setStyle(
            NotificationCompat.BigTextStyle()
                .setBigContentTitle(thread.from)
                .bigText(HtmlCompat.fromHtml("<b>" + thread.subject + "</b><br>" + cleanSnippet, HtmlCompat.FROM_HTML_MODE_LEGACY))
        )
        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        .setContentIntent(openPendingIntent)
        .setAutoCancel(true)
        .setVisibility(
            when (profile.preview) {
                NotificationPreview.FULL -> NotificationCompat.VISIBILITY_PUBLIC
                NotificationPreview.PRIVATE -> NotificationCompat.VISIBILITY_PRIVATE
                NotificationPreview.NONE -> NotificationCompat.VISIBILITY_SECRET
            }
        )
        .apply {
            if (!profile.badge) setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
        }
    actions.forEach(builder::addAction)

    NotificationManagerCompat.from(context).notify(account.id, notificationId, builder.build())
    Log.i(TAG, "Notification successfully sent to NotificationManagerCompat (id: $notificationId)")
}

