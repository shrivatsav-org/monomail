package com.shrivatsav.monomail.worker
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.shrivatsav.monomail.MonoMailApp
class SnoozeWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as MonoMailApp
        val dao = app.database.threadDao()
        val dueThreads = dao.getDueUnsnoozeThreads(System.currentTimeMillis())
        for (thread in dueThreads) {
            dao.unsnoozeThread(thread.threadId, thread.accountId)
            app.database.emailDao().unsnoozeThreadEmails(thread.threadId, thread.accountId)
        }
        return Result.success()
    }
}
