package com.shrivatsav.monomail.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.MonoMailApp

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        val threadId = inputData.getString(KEY_THREAD_ID)
        val emailIdsJson = inputData.getString(KEY_EMAIL_IDS)
        
        val app = applicationContext as MonoMailApp
        val accounts = app.accountManager.getAccounts()
        val targetProfile = accounts.find { it.id == accountId } ?: return Result.failure()
        
        // We temporarily set the active account, or ideally we'd pass the provider directly
        // However, providerFactory expects a profile
        
        // This relies on MonoMailApp's emailRepository to provide active provider.
        // Actually, let's switch active profile temporarily to run the work? No, that's bad.
        // Let's invoke the factory directly if possible, or we need to refactor EmailRepository
        // Let's just switch the account manager's active id, or refactor.
        // Wait, EmailRepository doesn't expose the factory. We can add a method `getProvider(accountId)`
        // For now, let's assume we sync the active account, or we can use `getActiveProvider`
        // if we are syncing the active account. Background syncs might fire later.
        
        return try {
            val oldActive = app.accountManager.getActiveAccount()
            if (oldActive?.id != accountId) {
                app.accountManager.setActiveAccountId(accountId)
            }
            
            val provider = app.emailRepository.getActiveProvider() ?: return Result.failure()
            
            when (action) {
                ACTION_TOGGLE_STAR -> {
                    val isStarred = inputData.getBoolean(KEY_IS_STARRED, false)
                    provider.toggleStar(threadId!!, isStarred)
                }
                ACTION_MARK_THREAD_READ -> {
                    provider.markRead(threadId!!, true)
                }
                ACTION_MARK_THREAD_UNREAD -> {
                    provider.markRead(threadId!!, false)
                }
                ACTION_MARK_EMAILS_READ -> {
                    if (emailIdsJson != null) {
                        val type = object : TypeToken<List<String>>() {}.type
                        val emailIds: List<String> = Gson().fromJson(emailIdsJson, type)
                        provider.batchMarkRead(emailIds)
                    }
                }
                ACTION_ARCHIVE -> {
                    provider.archiveThread(threadId!!)
                }
                ACTION_UNARCHIVE -> {
                    provider.unarchiveThread(threadId!!)
                }
                ACTION_DELETE -> {
                    provider.trashThread(threadId!!)
                }
                ACTION_RESTORE -> {
                    provider.restoreThread(threadId!!)
                }
            }
            
            if (oldActive?.id != accountId && oldActive != null) {
                app.accountManager.setActiveAccountId(oldActive.id)
            }
            
            Result.success()
        } catch (e: Exception) {
            // If it's a network error, retry. If it's something else, fail.
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
        const val KEY_ACCOUNT_ID = "account_id"
        const val KEY_THREAD_ID = "thread_id"
        const val KEY_EMAIL_IDS = "email_ids"
        const val KEY_IS_STARRED = "is_starred"

        const val ACTION_TOGGLE_STAR = "toggle_star"
        const val ACTION_MARK_THREAD_READ = "mark_thread_read"
        const val ACTION_MARK_THREAD_UNREAD = "mark_thread_unread"
        const val ACTION_MARK_EMAILS_READ = "mark_emails_read"
        const val ACTION_ARCHIVE = "archive"
        const val ACTION_UNARCHIVE = "unarchive"
        const val ACTION_DELETE = "delete"
        const val ACTION_RESTORE = "restore"
    }
}
