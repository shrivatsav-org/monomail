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
import androidx.core.text.HtmlCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.MainActivity
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    private val emailRepository: EmailRepository,
    private val accountManager: AccountManager,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "EmailSyncWorker"
        const val KEY_ACCOUNT_ID = "account_id"
        private const val ADAPTIVE_INTERVAL_MINUTES = 2L
        private const val ADAPTIVE_ACTIVITY_WINDOW_MINUTES = 5L
        private const val FALLBACK_INTERVAL_MINUTES = 15L
        private const val ADAPTIVE_SYNC_WORK_NAME = "adaptive_email_sync"

        internal fun channelIdForAccount(accountId: String): String = "monomail_$accountId"
    }

    override suspend fun doWork(): Result {
        val specificAccountId = inputData.getString(KEY_ACCOUNT_ID)
        val allAccounts = accountManager.getAccounts()
        val accounts = if (specificAccountId != null) {
            allAccounts.filter { it.id == specificAccountId }
        } else {
            allAccounts
        }
        if (accounts.isEmpty()) {
            Log.w(TAG, "No accounts found to sync")
            return Result.success()
        }
        val results = coroutineScope {
            accounts.map { account ->
                async(Dispatchers.IO) { syncAccount(account) }
            }.awaitAll()
        }
        scheduleNextAdaptiveSync(applicationContext, accountManager)
        val overallHasFailure = results.any { it.first }
        val overallHasAuthFailure = results.any { it.second }
        return if (overallHasFailure && !overallHasAuthFailure) Result.retry() else Result.success()
    }

    private suspend fun syncAccount(account: com.shrivatsav.monomail.auth.UserProfile): Pair<Boolean, Boolean> {
        val accountId = account.id
        val lastKnownTimestamp = accountManager.getLastKnownEmailId(accountId)
        Log.i("EmailSyncWorker", "Starting sync for account $accountId (lastKnownTimestamp: $lastKnownTimestamp)")
        val refreshResult = emailRepository.refreshInbox(InboxTab.INBOX, accountId = accountId)
        Log.i("EmailSyncWorker", "Refresh result for $accountId: isSuccess=${refreshResult.isSuccess}")

        if (refreshResult.isFailure) {
            return handleSyncFailure(accountId, refreshResult.exceptionOrNull())
        }

        val newestThread = emailRepository.getLatestInboxThread(accountId) ?: run {
            Log.w("EmailSyncWorker", "getLatestInboxThread returned null for $accountId")
            return Pair(false, false)
        }

        val newTimestamp = newestThread.date
        Log.i("EmailSyncWorker", "Latest thread for $accountId: subject='${newestThread.subject}', date=$newTimestamp")
        if (lastKnownTimestamp != null && newTimestamp.toString() != lastKnownTimestamp) {
            Log.i("EmailSyncWorker", "New email detected for $accountId! Showing notification...")
            showNotification(accountId, newestThread, accountId.hashCode())
        } else if (lastKnownTimestamp == null) {
            Log.i("EmailSyncWorker", "lastKnownTimestamp was null (first sync baseline). Skipping notification banner.")
        } else {
            Log.i("EmailSyncWorker", "No new emails detected (timestamp matched lastKnownTimestamp).")
        }
        accountManager.setLastKnownEmailId(accountId, newTimestamp.toString())
        return Pair(false, false)
    }

    private fun handleSyncFailure(accountId: String, error: Throwable?): Pair<Boolean, Boolean> {
        val msg = error?.message ?: ""
        return if (msg.contains("sign in", ignoreCase = true) || msg.contains("Session expired", ignoreCase = true) || msg.contains("Authentication failed", ignoreCase = true)) {
            Log.w("EmailSyncWorker", "Auth failure for account $accountId — skipping retry")
            Pair(false, true)
        } else {
            Log.e("EmailSyncWorker", "refreshInbox failed for account $accountId", error)
            Pair(true, false)
        }
    }

    private suspend fun scheduleNextAdaptiveSync(context: Context, accountManager: AccountManager) {
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) {
            Log.e(TAG, "POST_NOTIFICATIONS permission not granted! Aborting notification display.")
            return
        }
        Log.i(TAG, "Creating notification channel and building notification for $accountId...")
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
            params = NotificationActionReceiver.ReplyParams(
                accountId = accountId,
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
            accountId = accountId,
            threadId = thread.threadId,
            notificationId = notificationId
        )
        val archiveAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_edit, "Archive", archivePendingIntent
        ).build()

        val snoozePendingIntent = NotificationActionReceiver.createSnoozePendingIntent(
            context = context,
            accountId = accountId,
            threadId = thread.threadId,
            notificationId = notificationId
        )
        val snoozeAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_recent_history, "Snooze", snoozePendingIntent
        ).build()

        val cleanSnippet = thread.snippet.replace(Regex("\\bOn\\s+[A-Z][a-z]{2},.*?wrote:.*"), "").trim()
        val channelId = channelIdForAccount(accountId)
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(com.shrivatsav.monomail.R.drawable.ic_notification_leaf)
            .setContentTitle(thread.from)
            .setContentText(thread.subject)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(thread.from)
                    .bigText(HtmlCompat.fromHtml("<b>" + thread.subject + "</b><br>" + cleanSnippet, HtmlCompat.FROM_HTML_MODE_LEGACY))
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPendingIntent)
            .addAction(replyAction)
            .addAction(archiveAction)
            .addAction(snoozeAction)
            .setAutoCancel(true)

        NotificationManagerCompat.from(context).notify(accountId, notificationId, builder.build())
        Log.i(TAG, "Notification successfully sent to NotificationManagerCompat (id: $notificationId)")
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
