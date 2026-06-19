package com.shrivatsav.monomail.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.shrivatsav.monomail.MonoMailApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_REPLY = "com.shrivatsav.monomail.REPLY"
        const val ACTION_ARCHIVE = "com.shrivatsav.monomail.ARCHIVE"
        const val ACTION_UNDO_ARCHIVE = "com.shrivatsav.monomail.UNDO_ARCHIVE"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val REPLY_STATUS_CHANNEL = "monomail_reply_status"
        const val ARCHIVE_CONFIRM_CHANNEL = "monomail_archive_confirm"
        const val REPLY_NOTIFICATION_ID_BASE = 2000
        const val UNDO_NOTIFICATION_ID_BASE = 3000
        const val EXTRA_ACCOUNT_ID = "account_id"
        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_MESSAGE_ID = "message_id"
        const val EXTRA_SUBJECT = "subject"
        const val EXTRA_FROM_EMAIL = "from_email"
        const val EXTRA_FROM_NAME = "from_name"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        const val REPLY_REQUEST_CODE_BASE = 10000
        const val ARCHIVE_REQUEST_CODE_BASE = 20000
        const val UNDO_REQUEST_CODE_BASE = 30000

        fun createReplyPendingIntent(
            context: Context, accountId: String, threadId: String,
            messageId: String, subject: String, fromEmail: String,
            fromName: String, notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_REPLY
                putExtra(EXTRA_ACCOUNT_ID, accountId)
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_MESSAGE_ID, messageId)
                putExtra(EXTRA_SUBJECT, subject)
                putExtra(EXTRA_FROM_EMAIL, fromEmail)
                putExtra(EXTRA_FROM_NAME, fromName)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context, REPLY_REQUEST_CODE_BASE + notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        fun createArchivePendingIntent(
            context: Context, accountId: String, threadId: String, notificationId: Int
        ): PendingIntent {
            val intent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_ARCHIVE
                putExtra(EXTRA_ACCOUNT_ID, accountId)
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            return PendingIntent.getBroadcast(
                context, ARCHIVE_REQUEST_CODE_BASE + notificationId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                when (intent.action) {
                    ACTION_REPLY -> handleReply(context, intent)
                    ACTION_ARCHIVE -> handleArchive(context, intent)
                    ACTION_UNDO_ARCHIVE -> handleUndoArchive(context, intent)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleReply(context: Context, intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent)
        val replyText = remoteInput?.getCharSequence(KEY_TEXT_REPLY)?.toString() ?: return

        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return
        val messageId = intent.getStringExtra(EXTRA_MESSAGE_ID) ?: return
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: ""
        val fromEmail = intent.getStringExtra(EXTRA_FROM_EMAIL) ?: return
        val originalNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val replyNotificationId = REPLY_NOTIFICATION_ID_BASE + originalNotificationId

        val app = context.applicationContext as MonoMailApp
        val account = app.accountManager.getAccounts().find { it.id == accountId } ?: return
        val repository = app.emailRepository

        val oldActive = app.accountManager.getActiveAccount()
        if (oldActive?.id != accountId) {
            app.accountManager.setActiveAccountId(accountId)
        }

        createReplyStatusChannel(context)
        showReplyNotification(context, replyNotificationId, "Sending reply...", isProgress = true)

        val result = repository.sendEmail(
            from = account.email,
            to = fromEmail,
            subject = if (subject.startsWith("Re:", true)) subject else "Re: $subject",
            body = replyText,
            threadId = threadId,
            inReplyToMessageId = messageId,
            references = messageId
        )

        if (oldActive?.id != accountId && oldActive != null) {
            app.accountManager.setActiveAccountId(oldActive.id)
        }

        if (result.isSuccess) {
            showReplyNotification(context, replyNotificationId, "Reply sent", isProgress = false)
        } else {
            showReplyNotification(
                context, replyNotificationId,
                "Failed to send reply", isProgress = false
            )
        }
    }

    private suspend fun handleArchive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return
        val originalNotificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)

        val app = context.applicationContext as MonoMailApp

        val oldActive = app.accountManager.getActiveAccount()
        if (oldActive?.id != accountId) {
            app.accountManager.setActiveAccountId(accountId)
        }

        app.emailRepository.archiveThread(threadId)

        if (oldActive?.id != accountId && oldActive != null) {
            app.accountManager.setActiveAccountId(oldActive.id)
        }

        val undoIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = ACTION_UNDO_ARCHIVE
            putExtra(EXTRA_ACCOUNT_ID, accountId)
            putExtra(EXTRA_THREAD_ID, threadId)
            putExtra(EXTRA_NOTIFICATION_ID, originalNotificationId)
        }
        val undoPendingIntent = PendingIntent.getBroadcast(
            context, UNDO_REQUEST_CODE_BASE + originalNotificationId, undoIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        createArchiveConfirmChannel(context)
        val undoBuilder = NotificationCompat.Builder(context, ARCHIVE_CONFIRM_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("Archived")
            .setContentText("Thread has been archived")
            .addAction(android.R.drawable.ic_menu_revert, "Undo", undoPendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(60000)

        NotificationManagerCompat.from(context).notify(
            UNDO_NOTIFICATION_ID_BASE + originalNotificationId, undoBuilder.build()
        )
    }

    private suspend fun handleUndoArchive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID) ?: return
        val threadId = intent.getStringExtra(EXTRA_THREAD_ID) ?: return

        val app = context.applicationContext as MonoMailApp

        val oldActive = app.accountManager.getActiveAccount()
        if (oldActive?.id != accountId) {
            app.accountManager.setActiveAccountId(accountId)
        }

        app.emailRepository.unarchiveThread(threadId)

        if (oldActive?.id != accountId && oldActive != null) {
            app.accountManager.setActiveAccountId(oldActive.id)
        }

        val notificationId = UNDO_NOTIFICATION_ID_BASE + intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    private fun showReplyNotification(
        context: Context, notificationId: Int, text: String, isProgress: Boolean
    ) {
        val builder = NotificationCompat.Builder(context, REPLY_STATUS_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Reply")
            .setContentText(text)
            .setAutoCancel(true)

        if (isProgress) {
            builder.setProgress(0, 0, true).setOngoing(true)
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    private fun createReplyStatusChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                REPLY_STATUS_CHANNEL, "Reply Status",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows reply sending progress"
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun createArchiveConfirmChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ARCHIVE_CONFIRM_CHANNEL, "Archive Confirmation",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows archive confirmation with undo option"
                setShowBadge(false)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }
}
