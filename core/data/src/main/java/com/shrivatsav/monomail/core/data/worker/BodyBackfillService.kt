package com.shrivatsav.monomail.core.data.worker

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.shrivatsav.monomail.core.data.repository.BODY_BACKFILL_NOTIFICATION_ID
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.core.data.repository.buildBodyBackfillNotification
import com.shrivatsav.monomail.core.data.repository.ensureBodyBackfillChannel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import javax.inject.Inject

@AndroidEntryPoint
class BodyBackfillService : Service() {

    @Inject lateinit var emailRepository: EmailRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var backfillJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ensureBodyBackfillChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val accountId = intent?.getStringExtra("accountId") ?: run {
            stopSelf()
            return START_NOT_STICKY
        }

        // Single-flight: a running sweep owns the notification; ignore repeat
        // triggers (deep-sync completion, worker sync, pull-to-refresh) instead of
        // launching a second concurrent sweep with a different total that fights
        // the first over the same notification ID.
        if (backfillJob?.isActive == true) {
            android.util.Log.d("BodyBackfillSvc", "Sweep already running, ignoring trigger")
            return START_NOT_STICKY
        }

        startForeground(BODY_BACKFILL_NOTIFICATION_ID, buildBodyBackfillNotification(this, 0, 0))

        backfillJob = scope.launch {
            try {
                emailRepository.startBodyBackfill(accountId)
            } catch (e: Exception) {
                android.util.Log.e("BodyBackfillSvc", "Backfill failed unexpectedly", e)
            } finally {
                backfillJob = null
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



    companion object {
        fun start(context: Context, accountId: String) {
            val intent = Intent(context, BodyBackfillService::class.java).apply {
                putExtra("accountId", accountId)
            }
            context.startForegroundService(intent)
        }
    }
}
