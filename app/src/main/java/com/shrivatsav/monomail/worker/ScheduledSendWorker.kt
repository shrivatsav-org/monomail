package com.shrivatsav.monomail.worker
import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.MonoMailApp
import com.shrivatsav.monomail.data.model.EmailAttachment
import java.io.File
class ScheduledSendWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_SCHEDULED_MESSAGE_ID = "scheduled_message_id"
    }
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_SCHEDULED_MESSAGE_ID) ?: return Result.failure()
        val app = applicationContext as MonoMailApp
        val dao = app.database.scheduledMessageDao()
        val message = dao.getScheduledMessageById(messageId) ?: return Result.failure()
        if (message.isSent) return Result.success()
        val attachments = parseStoredAttachments(message.attachmentsJson)
        return try {
            val provider = app.emailRepository.getProviderForAccount(message.accountId)
            if (provider == null) return Result.retry()
            provider.sendEmail(
                from = message.fromEmail,
                to = message.to,
                cc = message.cc,
                bcc = message.bcc,
                subject = message.subject,
                body = message.body,
                attachments = attachments
            )
            dao.markAsSent(messageId)
            cleanupCachedFiles(attachments)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
    private fun parseStoredAttachments(json: String): List<EmailAttachment> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = Gson().fromJson(json, type)
            list.mapNotNull { m ->
                val path = m["localPath"] as? String ?: return@mapNotNull null
                val file = File(path)
                if (!file.exists()) return@mapNotNull null
                EmailAttachment(
                    uri = Uri.fromFile(file),
                    name = (m["name"] as? String) ?: file.name,
                    size = ((m["size"] as? Double)?.toLong() ?: file.length()),
                    mimeType = (m["mimeType"] as? String) ?: "application/octet-stream"
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    private fun cleanupCachedFiles(attachments: List<EmailAttachment>) {
        attachments.forEach { a ->
            try {
                val file = File(a.uri.path ?: return@forEach)
                file.delete()
                file.parentFile?.delete()
            } catch (_: Exception) { }
        }
    }
}
