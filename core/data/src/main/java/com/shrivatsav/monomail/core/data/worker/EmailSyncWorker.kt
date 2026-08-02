package com.shrivatsav.monomail.core.data.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.repository.EmailRepository
import com.shrivatsav.monomail.model.InboxTab
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
    private val settingsDataStore: com.shrivatsav.monomail.core.data.settings.SettingsDataStore,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_ACCOUNT_ID = "account_id"
        private const val TAG = "EmailSyncWorker"
        private const val ADAPTIVE_INTERVAL_MINUTES = 2L
        private const val ADAPTIVE_ACTIVITY_WINDOW_MINUTES = 5L
        private const val FALLBACK_INTERVAL_MINUTES = 15L
        private const val ADAPTIVE_SYNC_WORK_NAME = "adaptive_email_sync"

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

    private suspend fun syncAccount(account: com.shrivatsav.monomail.core.data.auth.UserProfile): Pair<Boolean, Boolean> {
        val accountId = account.id
        val lastKnownTimestamp = accountManager.getLastKnownEmailId(accountId)
        Log.i("EmailSyncWorker", "Starting sync for account $accountId (lastKnownTimestamp: $lastKnownTimestamp)")
        val refreshResult = emailRepository.refreshInbox(InboxTab.INBOX, accountId = accountId)
        Log.i("EmailSyncWorker", "Refresh result for $accountId: isSuccess=${refreshResult.isSuccess}")

        if (refreshResult.isFailure) {
            return handleSyncFailure(accountId, refreshResult.exceptionOrNull())
        }

        // Trigger body backfill for any emails that synced without body content
        emailRepository.triggerBodyBackfill(accountId)

        val newestThread = emailRepository.getLatestInboxThread(accountId) ?: run {
            Log.w("EmailSyncWorker", "getLatestInboxThread returned null for $accountId")
            return Pair(false, false)
        }

        val newTimestamp = newestThread.date
        Log.i("EmailSyncWorker", "Latest thread for $accountId: subject='${newestThread.subject}', date=$newTimestamp")
        
        // Check if the latest message is a draft or was sent by this account to avoid false notifications
        val latestEmail = emailRepository.getEmailEntityById(newestThread.latestMessageId, accountId)
        val isLatestDraft = latestEmail?.inDrafts == true
        val isSelfSent = latestEmail?.fromEmail?.equals(account.email, ignoreCase = true) == true

        if (lastKnownTimestamp != null && newTimestamp.toString() != lastKnownTimestamp && !isLatestDraft && !isSelfSent) {
            val disabledAccounts = settingsDataStore.settingsFlow.value.disabledNotificationAccounts
            if (disabledAccounts.contains(accountId)) {
                Log.i("EmailSyncWorker", "New email detected for $accountId, but notifications are disabled for this account. Skipping notification banner.")
            } else {
                Log.i("EmailSyncWorker", "New email detected for $accountId! Showing notification...")
                showNotification(accountId, newestThread, accountId.hashCode())
            }
        } else if (isLatestDraft) {
            Log.i("EmailSyncWorker", "Newest message is a draft. Skipping notification banner.")
        } else if (isSelfSent) {
            Log.i("EmailSyncWorker", "Newest message was sent by this account. Skipping notification banner.")
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
            ADAPTIVE_SYNC_WORK_NAME, ExistingWorkPolicy.REPLACE, workRequest
        )
    }

    private fun showNotification(
        accountId: String,
        thread: com.shrivatsav.monomail.data.model.EmailThread,
        notificationId: Int
    ) {
        showNewEmailNotification(
            context = applicationContext,
            accountId = accountId,
            thread = thread,
            notificationId = notificationId,
            quickActions = settingsDataStore.settingsFlow.value.notificationQuickActions
        )
    }
}
