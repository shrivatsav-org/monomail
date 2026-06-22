package com.shrivatsav.monomail.data.provider
import android.content.Context
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmail
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.remote.BatchModifyMessagesRequest
import com.shrivatsav.monomail.data.remote.GmailApi
import com.shrivatsav.monomail.data.remote.ModifyThreadRequest
import com.shrivatsav.monomail.data.remote.SendMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
class GmailProvider(
    private val api: GmailApi,
    private val context: Context
) : EmailProvider {
    override val providerName: String = "gmail"
    override suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int,
        pageToken: String?,
        query: String?
    ): ProviderThreadListResult {
        val labelIds = when {
            !query.isNullOrEmpty() -> null
            folder == EmailFolder.INBOX -> "INBOX"
            folder == EmailFolder.SENT -> "SENT"
            folder == EmailFolder.STARRED -> "STARRED"
            folder == EmailFolder.TRASH -> "TRASH"
            folder == EmailFolder.SPAM -> "SPAM"
            folder == EmailFolder.ARCHIVE -> null
            else -> "INBOX"
        }
        val finalQuery = if (folder == EmailFolder.ARCHIVE && query.isNullOrEmpty()) "-label:inbox -label:trash -label:sent" else query
        val response = api.listThreads(
            maxResults = maxResults,
            pageToken = pageToken,
            labelIds = labelIds,
            query = finalQuery
        )
        val threadRefs = response.threads.orEmpty()
        val rawThreads = coroutineScope {
            threadRefs.map { ref ->
                async {
                    try {
                        api.getThread(ref.id)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val providerThreads = rawThreads.map { rawThread ->
            val messages = rawThread.messages.orEmpty().map { msg ->
                val email = msg.toEmail()
                val labels = email.labels
                val folders = mutableSetOf<EmailFolder>()
                if ("INBOX" in labels) folders.add(EmailFolder.INBOX)
                if ("SENT" in labels) folders.add(EmailFolder.SENT)
                if ("STARRED" in labels) folders.add(EmailFolder.STARRED)
                if ("TRASH" in labels) folders.add(EmailFolder.TRASH)
                if ("SPAM" in labels) folders.add(EmailFolder.SPAM)
                if ("INBOX" !in labels && "TRASH" !in labels && "SENT" !in labels && "SPAM" !in labels) folders.add(EmailFolder.ARCHIVE)
                ProviderMessage(
                    id = email.id,
                    threadId = email.threadId,
                    subject = email.subject,
                    from = email.from,
                    fromEmail = email.fromEmail,
                    to = email.to,
                    cc = email.cc,
                    bcc = email.bcc,
                    snippet = email.snippet,
                    body = email.body,
                    date = email.date,
                    isRead = email.isRead,
                    isStarred = email.isStarred,
                    folders = folders,
                    attachments = email.attachments
                )
            }
            ProviderThread(rawThread.id, messages)
        }
        return ProviderThreadListResult(providerThreads, response.nextPageToken)
    }
    override suspend fun getThread(threadId: String, folderHints: List<String>): ProviderThread = withContext(Dispatchers.IO) {
        val rawThread = api.getThread(threadId)
        val messages = rawThread.messages.orEmpty().map { msg ->
            val email = msg.toEmail()
            val labels = email.labels
            val folders = mutableSetOf<EmailFolder>()
            if ("INBOX" in labels) folders.add(EmailFolder.INBOX)
            if ("SENT" in labels) folders.add(EmailFolder.SENT)
            if ("STARRED" in labels) folders.add(EmailFolder.STARRED)
            if ("TRASH" in labels) folders.add(EmailFolder.TRASH)
            if ("SPAM" in labels) folders.add(EmailFolder.SPAM)
            if ("INBOX" !in labels && "TRASH" !in labels && "SENT" !in labels && "SPAM" !in labels) folders.add(EmailFolder.ARCHIVE)
            ProviderMessage(
                id = email.id,
                threadId = email.threadId,
                subject = email.subject,
                from = email.from,
                fromEmail = email.fromEmail,
                to = email.to,
                cc = email.cc,
                bcc = email.bcc,
                snippet = email.snippet,
                body = email.body,
                date = email.date,
                isRead = email.isRead,
                isStarred = email.isStarred,
                folders = folders,
                attachments = email.attachments
            )
        }
        ProviderThread(rawThread.id, messages)
    }
    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val part = api.getAttachment(messageId, attachmentId)
                part.data?.let { data ->
                    val base64 = data.replace("-", "+").replace("_", "/")
                    android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    override suspend fun archiveThread(threadId: String) {
        api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("INBOX")))
    }
    override suspend fun unarchiveThread(threadId: String) {
        api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("INBOX")))
    }
    override suspend fun trashThread(threadId: String) {
        api.trashThread(threadId)
    }
    override suspend fun restoreThread(threadId: String) {
        api.untrashThread(threadId)
    }
    override suspend fun permanentlyDeleteThread(threadId: String) {
        api.permanentlyDeleteThread(threadId)
    }
    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        if (starred) {
            api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("STARRED")))
        } else {
            api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("STARRED")))
        }
    }
    override suspend fun markRead(threadId: String, read: Boolean) {
        if (read) {
            api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("UNREAD")))
        } else {
            api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("UNREAD")))
        }
    }
    override suspend fun batchMarkRead(messageIds: List<String>) {
        messageIds.chunked(1000).forEach { chunk ->
            api.batchModifyMessages(BatchModifyMessagesRequest(ids = chunk, removeLabelIds = listOf("UNREAD")))
        }
    }
    override suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        cc: String,
        bcc: String,
        threadId: String?,
        attachments: List<EmailAttachment>
    ): String? {
        val headers = buildString {
            append("From: $from\r\n")
            append("To: $to\r\n")
            if (cc.isNotBlank()) append("Cc: $cc\r\n")
            if (bcc.isNotBlank()) append("Bcc: $bcc\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            if (attachments.isEmpty()) {
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("\r\n")
                append(body)
            } else {
                val boundary = "==Multipart_Boundary_x${System.currentTimeMillis()}x"
                append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
                append("\r\n")
                append("--$boundary\r\n")
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("\r\n")
                append(body)
                append("\r\n")
                for (attachment in attachments) {
                    append("--$boundary\r\n")
                    append("Content-Type: ${attachment.mimeType}; name=\"${attachment.name}\"\r\n")
                    append("Content-Disposition: attachment; filename=\"${attachment.name}\"\r\n")
                    append("Content-Transfer-Encoding: base64\r\n")
                    append("\r\n")
                    val bytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT).replace("\n", "\r\n")
                        append(base64)
                        if (!base64.endsWith("\n") && !base64.endsWith("\r\n")) {
                            append("\r\n")
                        }
                    }
                }
                append("--$boundary--\r\n")
            }
        }
        val raw = android.util.Base64.encodeToString(
            headers.toByteArray(),
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        val response = api.sendMessage(
            SendMessageRequest(
                raw = raw,
                threadId = threadId
            )
        )
        return response.threadId
    }
}
