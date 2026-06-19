package com.shrivatsav.monomail.worker
import android.Manifest
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.MainActivity
import com.shrivatsav.monomail.MonoMailApp
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import kotlinx.coroutines.flow.firstOrNull
class EmailSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val CHANNEL_ID = "monomail_notifications"
        private const val NOTIFICATION_ID = 1001
    }
    override suspend fun doWork(): Result {
        val app = applicationContext as? MonoMailApp ?: return Result.failure()
        val repository = app.emailRepository
        val accountManager = app.accountManager
        val accounts = accountManager.getAccounts()
        if (accounts.isEmpty()) return Result.success()
        var hasFailure = false
        val database = app.emailRepository.getDatabase() 
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
                        title = newestThread.from,
                        text = newestThread.subject,
                        emailId = newestThread.threadId,
                        notificationId = accountId.hashCode()
                    )
                }
                accountManager.setLastKnownEmailId(accountId, newTimestamp)
            }
        }
        return if (hasFailure) Result.retry() else Result.success()
    }
    private fun showNotification(title: String, text: String, emailId: String, notificationId: Int) {
        val context = applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }
        createNotificationChannel(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_email)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
        with(NotificationManagerCompat.from(context)) {
            notify(notificationId, builder.build())
        }
    }
    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "New Emails"
            val descriptionText = "Notifications for new incoming emails"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}
