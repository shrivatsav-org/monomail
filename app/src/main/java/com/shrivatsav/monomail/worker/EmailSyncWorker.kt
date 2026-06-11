package com.shrivatsav.monomail.worker

import android.Manifest
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
        val tokenManager = app.tokenManager

        // Skip if not logged in
        if (app.authManager.currentUser == null) return Result.success()

        val lastKnownId = tokenManager.getLastKnownEmailId()

        // Fetch latest emails
        val result = repository.getInboxEmails()
        if (result.isFailure) {
            return Result.retry()
        }

        val emails = result.getOrNull()?.emails ?: emptyList()
        if (emails.isEmpty()) return Result.success()

        val newestEmail = emails.first()

        if (lastKnownId != null && newestEmail.id != lastKnownId) {
            // New email arrived!
            showNotification(
                title = newestEmail.from,
                text = newestEmail.subject,
                emailId = newestEmail.id
            )
        }

        // Update the last known ID
        tokenManager.setLastKnownEmailId(newestEmail.id)

        return Result.success()
    }

    private fun showNotification(title: String, text: String, emailId: String) {
        val context = applicationContext

        // Check permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                return
            }
        }

        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // We could pass emailId to deep link directly to it later
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
            notify(NOTIFICATION_ID, builder.build())
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
