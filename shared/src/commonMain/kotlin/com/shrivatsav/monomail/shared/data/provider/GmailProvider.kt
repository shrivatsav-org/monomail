package com.shrivatsav.monomail.shared.data.provider

import com.shrivatsav.monomail.shared.data.mapper.EmailMapper.toEmail
import com.shrivatsav.monomail.shared.data.model.EmailAttachment
import com.shrivatsav.monomail.shared.data.remote.BatchModifyMessagesRequest
import com.shrivatsav.monomail.shared.data.remote.GmailApi
import com.shrivatsav.monomail.shared.data.remote.ModifyThreadRequest
import com.shrivatsav.monomail.shared.data.remote.SendMessageRequest
import com.shrivatsav.monomail.shared.util.Base64Util
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.random.Random

class GmailProvider(
    private val api: GmailApi
) : EmailProvider {
    override val providerName: String = "gmail"

    private fun foldersFor(labels: List<String>): Set<EmailFolder> {
        val folders = mutableSetOf<EmailFolder>()
        if ("INBOX" in labels) folders.add(EmailFolder.INBOX)
        if ("SENT" in labels) folders.add(EmailFolder.SENT)
        if ("STARRED" in labels) folders.add(EmailFolder.STARRED)
        if ("TRASH" in labels) folders.add(EmailFolder.TRASH)
        if ("INBOX" !in labels && "TRASH" !in labels && "SENT" !in labels) folders.add(EmailFolder.ARCHIVE)
        return folders
    }

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
            folder == EmailFolder.ARCHIVE -> null
            else -> "INBOX"
        }
        val finalQuery = if (folder == EmailFolder.ARCHIVE && query.isNullOrEmpty()) {
            "-label:inbox -label:trash -label:sent"
        } else query
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
                        null
                    }
                }
            }.awaitAll().filterNotNull()
        }
        val providerThreads = rawThreads.map { rawThread ->
            val messages = rawThread.messages.orEmpty().map { msg ->
                val email = msg.toEmail()
                ProviderMessage(
                    id = email.id,
                    threadId = email.threadId,
                    subject = email.subject,
                    from = email.from,
                    fromEmail = email.fromEmail,
                    to = email.to,
                    snippet = email.snippet,
                    body = email.body,
                    date = email.date,
                    isRead = email.isRead,
                    isStarred = email.isStarred,
                    folders = foldersFor(email.labels),
                    attachments = email.attachments
                )
            }
            ProviderThread(rawThread.id, messages)
        }
        return ProviderThreadListResult(providerThreads, response.nextPageToken)
    }

    override suspend fun getThread(threadId: String): ProviderThread {
        val rawThread = api.getThread(threadId)
        val messages = rawThread.messages.orEmpty().map { msg ->
            val email = msg.toEmail()
            ProviderMessage(
                id = email.id,
                threadId = email.threadId,
                subject = email.subject,
                from = email.from,
                fromEmail = email.fromEmail,
                to = email.to,
                snippet = email.snippet,
                body = email.body,
                date = email.date,
                isRead = email.isRead,
                isStarred = email.isStarred,
                folders = foldersFor(email.labels),
                attachments = email.attachments
            )
        }
        return ProviderThread(rawThread.id, messages)
    }

    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return try {
            val part = api.getAttachment(messageId, attachmentId)
            part.data?.let { Base64Util.decodeUrl(it) }
        } catch (e: Exception) {
            null
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
        threadId: String?,
        attachments: List<EmailAttachment>
    ) {
        val mime = buildString {
            append("From: $from\r\n")
            append("To: $to\r\n")
            append("Subject: $subject\r\n")
            append("MIME-Version: 1.0\r\n")
            if (attachments.isEmpty()) {
                append("Content-Type: text/html; charset=UTF-8\r\n")
                append("\r\n")
                append(body)
            } else {
                val boundary = "==Multipart_Boundary_x${Random.nextLong().toString(16)}x"
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
                    val base64 = Base64Util.encodeMime(attachment.bytes)
                    append(base64)
                    if (!base64.endsWith("\r\n")) append("\r\n")
                }
                append("--$boundary--\r\n")
            }
        }
        val raw = Base64Util.encodeUrl(mime.encodeToByteArray())
        api.sendMessage(SendMessageRequest(raw = raw, threadId = threadId))
    }
}
