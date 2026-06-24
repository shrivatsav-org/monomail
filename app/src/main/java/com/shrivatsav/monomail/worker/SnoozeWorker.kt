package com.shrivatsav.monomail.worker
import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.data.local.AppDatabase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class SnoozeWorker @AssistedInject constructor(
    private val database: AppDatabase,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val dao = database.threadDao()
        val dueThreads = dao.getDueUnsnoozeThreads(System.currentTimeMillis())
        for (thread in dueThreads) {
            dao.unsnoozeThread(thread.threadId, thread.accountId)
            database.emailDao().unsnoozeThreadEmails(thread.threadId, thread.accountId)
        }
        return Result.success()
    }
}
