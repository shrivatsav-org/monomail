package com.shrivatsav.monomail.core.data.worker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service that runs the initial deep sync for a new account.
 * Shows a progress notification that survives app minimize or close.
 * Starts [BodyBackfillService] when header sync completes.
 */
@AndroidEntryPoint
class DeepSyncService : Service() {

    @Inject lateinit var emailRepository: EmailRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val channelId = "deep_sync"
    private val notificationId = 0xDE

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accountId = intent?.getStringExtra("accountId") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        val days = intent?.getIntExtra("days", 30) ?: 30

        startForeground(notificationId, buildNotification(0, indeterminate = true))

        scope.launch {
            // Observe sync progress and update notification
            val progressJob = launch {
                emailRepository.syncProgress.collect { progress ->
                    val pct = ((progress ?: 0f) * 100).toInt().coerceIn(0, 100)
                    updateNotification(pct)
                }
            }

            try {
                emailRepository.startBackgroundDeepSync(days, accountId)
                showDoneNotification()
                emailRepository.triggerBodyBackfill(accountId)
            } catch (e: Exception) {
                android.util.Log.e("DeepSyncSvc", "Deep sync failed", e)
                showErrorNotification(e.message ?: "Unknown error")
            } finally {
                progressJob.cancel()
                delay(3000)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Inbox sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress while your inbox is being synced"
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, indeterminate: Boolean): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Syncing your inbox")
            .setContentText(if (indeterminate) "Starting..." else "$progress%")
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(progress: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(notificationId, buildNotification(progress, false))
    }

    private fun showDoneNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val done = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Inbox synced")
            .setContentText("Your emails have been synced")
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        nm.notify(notificationId, done)
    }

    private fun showErrorNotification(error: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val err = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Sync incomplete")
            .setContentText(error)
            .setAutoCancel(true)
            .setSilent(true)
            .build()
        nm.notify(notificationId, err)
    }

    companion object {
        fun start(context: Context, accountId: String, days: Int = 30) {
            val intent = Intent(context, DeepSyncService::class.java).apply {
                putExtra("accountId", accountId)
                putExtra("days", days)
            }
            context.startForegroundService(intent)
        }
    }
}
