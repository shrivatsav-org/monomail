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
 * Runs the body backfill inline once header sync completes, then posts the
 * completion notification only after both have finished.
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

        var postedDone = false
        scope.launch {
            // Observe sync progress and update notification
            val progressJob = launch {
                emailRepository.syncProgress.collect { progress ->
                    val pct = ((progress?.fraction ?: 0f) * 100).toInt().coerceIn(0, 100)
                    updateNotification(pct, progress?.folder)
                }
            }

            try {
                emailRepository.startBackgroundDeepSync(days, accountId)
                // Run body backfill directly in this FGS scope — no need to start
                // a second service.  startBodyBackfill is safe to call from here
                // because DeepSyncService IS a foreground service, and the cooldown
                // is reset to 0 for the very first sweep after a deep sync.
                emailRepository.startBodyBackfill(accountId)
                // Only announce completion once BOTH the header sync and the
                // email content download have finished.
                showDoneNotification()
                postedDone = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Cancelled via the notification's Cancel action — no error
                // notification for an intentional stop.
                throw e
            } catch (e: Exception) {
                android.util.Log.e("DeepSyncSvc", "Deep sync failed", e)
                showErrorNotification(e.message ?: "Unknown error")
            } finally {
                progressJob.cancel()
                delay(3000)
                // DETACH keeps the just-posted "Inbox synced" notification
                // visible as a regular notification — it auto-dismisses via
                // setTimeoutAfter. REMOVE would delete it with the FGS.
                stopForeground(if (postedDone) STOP_FOREGROUND_DETACH else STOP_FOREGROUND_REMOVE)
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
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            // Recreate once if it exists with an older importance, because
            // channel importance is immutable after creation.
            if (nm.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_DEFAULT) return
            nm.deleteNotificationChannel(channelId)
            val channel = NotificationChannel(
                channelId,
                "Inbox sync",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows progress while your inbox is being synced"
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(progress: Int, indeterminate: Boolean, folder: String? = null): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(if (folder != null) "Syncing $folder" else "Syncing your inbox")
            .setContentText(
                when {
                    indeterminate && folder != null -> "Searching $folder…"
                    indeterminate -> "Starting..."
                    folder != null -> "$folder · $progress%"
                    else -> "$progress%"
                }
            )
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(0, "Cancel", NotificationActionReceiver.createCancelDeepSyncPendingIntent(this))
            .build()
    }

    private fun updateNotification(progress: Int, folder: String? = null) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // 0% is the SEARCH phase — the folder search completes before the first
        // message is processed, so a progress bar pinned at 0% would look stuck.
        // Render it as an animated indeterminate "Searching…" state instead.
        val searching = progress == 0
        nm.notify(notificationId, buildNotification(progress, searching, folder))
    }


    private fun showDoneNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val done = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Inbox synced")
            .setContentText("Your emails have been synced")
            .setAutoCancel(true)
            .setSilent(true)
            .setTimeoutAfter(5000)
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
