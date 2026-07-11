package com.shrivatsav.monomail.worker
import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.data.local.AppDatabase
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.repository.EmailRepository
import com.shrivatsav.monomail.data.repository.SendEmailParams
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.File

@HiltWorker
class ScheduledSendWorker @AssistedInject constructor(
    private val database: AppDatabase,
    private val emailRepository: EmailRepository,
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        const val KEY_SCHEDULED_MESSAGE_ID = "scheduled_message_id"
    }
    override suspend fun doWork(): Result {
        val messageId = inputData.getString(KEY_SCHEDULED_MESSAGE_ID) ?: return Result.failure()
        val dao = database.scheduledMessageDao()
        val message = dao.getScheduledMessageById(messageId) ?: return Result.failure()
        if (message.isSent) return Result.success()
        val attachments = parseStoredAttachments(message.attachmentsJson)
        return try {
            val provider = emailRepository.getProviderForAccount(message.accountId)
            if (provider == null) return Result.retry()
            emailRepository.sendEmail(
                from = message.fromEmail,
                to = message.to,
                subject = message.subject,
                body = message.body,
                params = SendEmailParams(cc = message.cc, bcc = message.bcc, attachments = attachments, threadId = message.threadId, inReplyToMessageId = message.messageId, references = message.messageId),
                explicitAccountId = message.accountId
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
            } catch (e: Exception) {
                android.util.Log.w("ScheduledSendWorker", "Failed to cleanup cached file: ${a.uri.path}", e)
            }
        }
    }
}
