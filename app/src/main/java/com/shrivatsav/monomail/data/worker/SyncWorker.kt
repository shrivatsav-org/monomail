package com.shrivatsav.monomail.data.worker
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.data.remote.RetrofitClient
import com.shrivatsav.monomail.data.repository.EmailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SyncWorker @AssistedInject constructor(
    private val accountManager: AccountManager,
    private val emailRepository: EmailRepository,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val accountId = inputData.getString(KEY_ACCOUNT_ID) ?: return Result.failure()
        val threadId = inputData.getString(KEY_THREAD_ID)
        val emailIdsJson = inputData.getString(KEY_EMAIL_IDS)
        val accounts = accountManager.getAccounts()
        val targetProfile = accounts.find { it.id == accountId } ?: return Result.failure()
        return try {
            val oldActive = accountManager.getActiveAccount()
            if (oldActive?.id != accountId) {
                accountManager.setActiveAccountId(accountId)
            }
            val provider = emailRepository.getActiveProvider() ?: return Result.failure()
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
                accountManager.setActiveAccountId(oldActive.id)
            }
            Result.success()
        } catch (e: RetrofitClient.AuthFailedException) {
            Result.failure()
        } catch (e: Exception) {
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
