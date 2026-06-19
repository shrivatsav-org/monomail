package com.shrivatsav.monomail.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.shrivatsav.monomail.MainActivity
import com.shrivatsav.monomail.MonoMailApp
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import java.util.concurrent.TimeUnit

class EmailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val ADAPTIVE_INTERVAL_MINUTES = 2L
        private const val ADAPTIVE_ACTIVITY_WINDOW_MINUTES = 5L
        private const val FALLBACK_INTERVAL_MINUTES = 15L
        private const val ADAPTIVE_SYNC_WORK_NAME = "adaptive_email_sync"

        internal fun channelIdForAccount(accountId: String): String = "monomail_$accountId"
    }

    override suspend fun doWork(): Result {
        val app = applicationContext as? MonoMailApp ?: return Result.failure()
        val repository = app.emailRepository
        val accountManager = app.accountManager
        val accounts = accountManager.getAccounts()
        if (accounts.isEmpty()) return Result.success()
        var hasFailure = false
        for (account in accounts) {
            val accountId = account.id
            val lastKnownTimestamp = accountManager.getLastKnownEmailId(accountId)
            val result = repository.refreshInbox(InboxTab.INBOX, accountId = accountId)
            if (result.isFailure) {
                Log.e("EmailSyncWorker", "refreshInbox failed for account $accountId", result.exceptionOrNull())
                hasFailure = true
                continue
            }
            val newestThread = repository.getLatestInboxThread(accountId)
            if (newestThread != null) {
                val newTimestamp = newestThread.date.toString()
                if (lastKnownTimestamp != null && newTimestamp != lastKnownTimestamp) {
                    showNotification(
                        accountId = accountId,
                        thread = newestThread,
                        notificationId = accountId.hashCode()
                    )
                }
                accountManager.setLastKnownEmailId(accountId, newTimestamp)
            }
        }
        scheduleNextAdaptiveSync(applicationContext, accountManager)
        return if (hasFailure) Result.retry() else Result.success()
    }

    private suspend fun scheduleNextAdaptiveSync(context: Context, accountManager: com.shrivatsav.monomail.auth.AccountManager) {
        val lastActive = accountManager.getLastActiveTime()
        val now = System.currentTimeMillis()
        val isRecentlyActive = lastActive > 0 && (now - lastActive) < TimeUnit.MINUTES.toMillis(ADAPTIVE_ACTIVITY_WINDOW_MINUTES)

        val delayMinutes = if (isRecentlyActive) ADAPTIVE_INTERVAL_MINUTES else FALLBACK_INTERVAL_MINUTES
        val workRequest = OneTimeWorkRequestBuilder<EmailSyncWorker>()
            .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ADAPTIVE_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            workRequest
        )
    }

    private fun showNotification(
        accountId: String,
        thread: com.shrivatsav.monomail.data.model.EmailThread,
        notificationId: Int
    ) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
        }
        createNotificationChannel(context, accountId, thread.from)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openPendingIntent = PendingIntent.getActivity(
            context, 0, openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val replyPendingIntent = NotificationActionReceiver.createReplyPendingIntent(
            context = context,
            accountId = accountId,
            threadId = thread.threadId,
            messageId = thread.latestMessageId,
            subject = thread.subject,
            fromEmail = thread.fromEmail,
            fromName = thread.from,
            notificationId = notificationId
        )
        val replyRemoteInput = RemoteInput.Builder(NotificationActionReceiver.KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()
        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send, "Reply", replyPendingIntent
        ).addRemoteInput(replyRemoteInput).build()

        val archivePendingIntent = NotificationActionReceiver.createArchivePendingIntent(
            context = context,
            accountId = accountId,
            threadId = thread.threadId,
            notificationId = notificationId
        )
        val archiveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Archive", archivePendingIntent
        ).build()

        val channelId = channelIdForAccount(accountId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(thread.from)
            .setContentText(thread.subject)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(archiveAction)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun createNotificationChannel(context: Context, accountId: String, accountName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = channelIdForAccount(accountId)
            val channelName = "$accountName ($accountId)"
            val descriptionText = "Notifications for $accountName"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
