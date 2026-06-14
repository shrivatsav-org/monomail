package com.shrivatsav.monomail.data.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.MonoMailApp
import com.shrivatsav.monomail.data.remote.BatchModifyMessagesRequest
import com.shrivatsav.monomail.data.remote.ModifyThreadRequest

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val action = inputData.getString(KEY_ACTION) ?: return Result.failure()
        val threadId = inputData.getString(KEY_THREAD_ID)
        val emailIdsJson = inputData.getString(KEY_EMAIL_IDS)
        
        val app = applicationContext as MonoMailApp
        // We'll expose api via emailRepository
        val api = app.emailRepository.api

        return try {
            when (action) {
                ACTION_TOGGLE_STAR -> {
                    val isStarred = inputData.getBoolean(KEY_IS_STARRED, false)
                    if (isStarred) {
                        api.modifyThread(threadId!!, ModifyThreadRequest(addLabelIds = listOf("STARRED")))
                    } else {
                        api.modifyThread(threadId!!, ModifyThreadRequest(removeLabelIds = listOf("STARRED")))
                    }
                }
                ACTION_MARK_THREAD_READ -> {
                    api.modifyThread(threadId!!, ModifyThreadRequest(removeLabelIds = listOf("UNREAD")))
                }
                ACTION_MARK_THREAD_UNREAD -> {
                    api.modifyThread(threadId!!, ModifyThreadRequest(addLabelIds = listOf("UNREAD")))
                }
                ACTION_MARK_EMAILS_READ -> {
                    if (emailIdsJson != null) {
                        val type = object : TypeToken<List<String>>() {}.type
                        val emailIds: List<String> = Gson().fromJson(emailIdsJson, type)
                        emailIds.chunked(1000).forEach { chunk ->
                            api.batchModifyMessages(BatchModifyMessagesRequest(ids = chunk, removeLabelIds = listOf("UNREAD")))
                        }
                    }
                }
                ACTION_ARCHIVE -> {
                    api.modifyThread(threadId!!, ModifyThreadRequest(removeLabelIds = listOf("INBOX")))
                }
                ACTION_UNARCHIVE -> {
                    api.modifyThread(threadId!!, ModifyThreadRequest(addLabelIds = listOf("INBOX")))
                }
                ACTION_DELETE -> {
                    api.trashThread(threadId!!)
                }
                ACTION_RESTORE -> {
                    api.untrashThread(threadId!!)
                }
            }
            Result.success()
        } catch (e: Exception) {
            // If it's a network error, retry. If it's something else, fail.
            // Simplified: always retry on exception until max retries.
            Result.retry()
        }
    }

    companion object {
        const val KEY_ACTION = "action"
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
