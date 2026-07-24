package com.shrivatsav.monomail.core.network.provider
import android.content.Context
import com.shrivatsav.monomail.core.network.mapper.EmailMapper.toEmail
import com.shrivatsav.monomail.core.network.remote.BatchModifyMessagesRequest
import com.shrivatsav.monomail.core.network.remote.GmailApi
import com.shrivatsav.monomail.core.network.remote.ModifyThreadRequest
import com.shrivatsav.monomail.core.network.remote.RetrofitClient
import com.shrivatsav.monomail.core.network.remote.SendMessageRequest
import jakarta.mail.Message
import jakarta.mail.Multipart
import jakarta.mail.Session
import jakarta.mail.internet.InternetAddress
import jakarta.mail.internet.MimeBodyPart
import jakarta.mail.internet.MimeMessage
import jakarta.mail.internet.MimeMultipart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.ByteArrayOutputStream
import java.util.Properties
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
            folder == EmailFolder.DRAFT -> "DRAFT"
            folder == EmailFolder.ARCHIVE -> null
            else -> "INBOX"
        }
        val finalQuery = when {
            !query.isNullOrEmpty() -> {
                val folderLabel = when (folder) {
                    EmailFolder.SENT -> "label:sent"
                    EmailFolder.TRASH -> "label:trash"
                    EmailFolder.SPAM -> "label:spam"
                    EmailFolder.STARRED -> "label:starred"
                    else -> ""
                }
                if (folderLabel.isNotEmpty() && !query.contains("label:")) "$query $folderLabel" else query
            }
            folder == EmailFolder.ARCHIVE -> "-label:inbox -label:trash -label:sent"
            else -> null
        }
        val response = api.listThreads(
            maxResults = maxResults,
            pageToken = pageToken,
            labelIds = labelIds,
            query = finalQuery
        )
        val threadRefs = response.threads.orEmpty()

        // Fetch full thread details in parallel (bounded concurrency)
        val semaphore = Semaphore(5)
        val fullThreads = coroutineScope {
            threadRefs.map { ref ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        try {
                            api.getThread(ref.id, format = "metadata")
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }.awaitAll()
        }

        val providerThreads = threadRefs.mapIndexed { idx, ref ->
            val full = fullThreads[idx]
            if (full != null) {
                val messages = full.messages.orEmpty()
                val latest = messages.maxByOrNull { it.internalDate?.toLongOrNull() ?: 0L }
                val latestHeaders = latest?.payload?.headers.orEmpty()
                val subject = (latestHeaders.firstOrNull { it.name.equals("Subject", true) }?.value
                    ?: messages.firstOrNull()?.payload?.headers?.firstOrNull { it.name.equals("Subject", true) }?.value)
                    ?.ifBlank { null }
                    ?: ref.snippet
                    ?: "(no subject)"
                val fromRaw = latestHeaders.firstOrNull { it.name.equals("From", true) }?.value ?: ""
                val (fromName, fromEmail) = parseFromHeader(fromRaw)
                val isRead = messages.all { "UNREAD" !in it.labelIds.orEmpty() }
                val isStarred = messages.any { "STARRED" in it.labelIds.orEmpty() }
                ProviderThread(
                    threadId = ref.id,
                    messages = listOf(
                        ProviderMessage(
                            id = latest?.id ?: ref.id,
                            threadId = ref.id,
                            subject = subject,
                            from = fromName,
                            fromEmail = fromEmail,
                            to = latestHeaders.firstOrNull { it.name.equals("To", true) }?.value ?: "",
                            cc = latestHeaders.firstOrNull { it.name.equals("Cc", true) }?.value ?: "",
                            bcc = "",
                            snippet = ref.snippet ?: "",
                            body = "",
                            bodyIsHtml = false,
                            date = latest?.internalDate?.toLongOrNull() ?: 0L,
                            isRead = isRead,
                            isStarred = isStarred,
                            folders = setOf(folder),
                            attachments = emptyList()
                        )
                    )
                )
            } else {
                // Fallback: snippet only (no full data available)
                ProviderThread(
                    threadId = ref.id,
                    messages = listOf(
                        ProviderMessage(
                            id = ref.id,
                            threadId = ref.id,
                            subject = ref.snippet?.ifBlank { null } ?: "(no subject)",
                            from = "",
                            fromEmail = "",
                            to = "",
                            cc = "",
                            bcc = "",
                            snippet = ref.snippet ?: "",
                            body = "",
                            bodyIsHtml = false,
                            date = 0L,
                            isRead = false,
                            isStarred = false,
                            folders = setOf(folder),
                            attachments = emptyList()
                        )
                    )
                )
            }
        }
        return ProviderThreadListResult(providerThreads, response.nextPageToken)
    }
    override suspend fun getThread(threadId: String, folderHints: List<String>): ProviderThread = withContext(Dispatchers.IO) {
        val rawThread = try {
            api.getThread(threadId)
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
        val messages = rawThread.messages.orEmpty().map { msg ->
            val email = msg.toEmail()
            ProviderMessage(
                id = email.id, threadId = email.threadId, subject = email.subject,
                from = email.from, fromEmail = email.fromEmail, to = email.`to`,
                cc = email.cc, bcc = email.bcc, snippet = email.snippet,
                body = email.body, bodyIsHtml = email.bodyIsHtml, date = email.date,
                isRead = email.isRead, isStarred = email.isStarred,
                folders = labelsToFolders(email.labels), attachments = email.attachments
            )
        }
        ProviderThread(rawThread.id, messages)
    }

    private fun labelsToFolders(labels: List<String>): Set<EmailFolder> {
        val folders = mutableSetOf<EmailFolder>()
        if ("INBOX" in labels) folders.add(EmailFolder.INBOX)
        if ("SENT" in labels) folders.add(EmailFolder.SENT)
        if ("STARRED" in labels) folders.add(EmailFolder.STARRED)
        if ("TRASH" in labels) folders.add(EmailFolder.TRASH)
        if ("SPAM" in labels) folders.add(EmailFolder.SPAM)
        if ("DRAFT" in labels) folders.add(EmailFolder.DRAFT)
        val known = setOf("INBOX", "TRASH", "SENT", "SPAM", "DRAFT")
        if (labels.none { it in known }) folders.add(EmailFolder.ARCHIVE)
        return folders
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
                android.util.Log.e("GmailProvider", "getAttachmentBytes error", e)
                null
            }
        }
    }
    override suspend fun archiveThread(threadId: String) {
        try {
            api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("INBOX")))
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun unarchiveThread(threadId: String) {
        try {
            api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("INBOX")))
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun trashThread(threadId: String) {
        try {
            api.trashThread(threadId)
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun restoreThread(threadId: String) {
        try {
            api.untrashThread(threadId)
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun permanentlyDeleteThread(threadId: String) {
        try {
            api.permanentlyDeleteThread(threadId)
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        try {
            if (starred) {
                api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("STARRED")))
            } else {
                api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("STARRED")))
            }
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun markRead(threadId: String, read: Boolean) {
        try {
            if (read) {
                api.modifyThread(threadId, ModifyThreadRequest(removeLabelIds = listOf("UNREAD")))
            } else {
                api.modifyThread(threadId, ModifyThreadRequest(addLabelIds = listOf("UNREAD")))
            }
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
    }
    override suspend fun batchMarkRead(messageIds: List<String>) {
        messageIds.filter { it.isNotBlank() }.chunked(1000).forEach { chunk ->
            if (chunk.isEmpty()) return@forEach
            try {
                api.batchModifyMessages(BatchModifyMessagesRequest(ids = chunk, removeLabelIds = listOf("UNREAD")))
            } catch (e: com.shrivatsav.monomail.core.network.remote.RetrofitClient.AuthFailedException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("GmailProvider", "batchMarkRead error", e)
            }
        }
    }

    override suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        options: SendEmailOptions
    ): SendEmailResult? = withContext(Dispatchers.IO) {
        try {
            val session = Session.getInstance(Properties())
            val message = MimeMessage(session).apply {
                setFrom(InternetAddress(from))
                setRecipients(Message.RecipientType.TO, InternetAddress.parse(to))
                if (options.cc.isNotBlank()) setRecipients(Message.RecipientType.CC, InternetAddress.parse(options.cc))
                if (options.bcc.isNotBlank()) setRecipients(Message.RecipientType.BCC, InternetAddress.parse(options.bcc))
                setSubject(subject, "utf-8")
                if (options.attachments.isEmpty()) {
                    setContent(body, "text/html; charset=utf-8")
                } else {
                    val multipart = MimeMultipart()
                    val textPart = MimeBodyPart()
                    textPart.setContent(body, "text/html; charset=utf-8")
                    multipart.addBodyPart(textPart)
                    for (att in options.attachments) {
                        val bytes = context.contentResolver.openInputStream(att.uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val attPart = MimeBodyPart()
                            val source = jakarta.mail.util.ByteArrayDataSource(bytes, att.mimeType)
                            attPart.dataHandler = jakarta.activation.DataHandler(source)
                            attPart.fileName = att.name
                            multipart.addBodyPart(attPart)
                        }
                    }
                    setContent(multipart)
                }
                saveChanges()
            }

            val rawBytes = ByteArrayOutputStream().also { message.writeTo(it) }.toByteArray()
            val raw = android.util.Base64.encodeToString(
                rawBytes,
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            val response = api.sendMessage(SendMessageRequest(raw = raw, threadId = options.threadId))
            SendEmailResult(messageId = response.id, threadId = response.threadId)
        } catch (e: HttpException) {
            val msg = when (e.code()) {
                400 -> "Invalid message format"
                403 -> "Insufficient permissions to send email"
                429 -> "Too many requests, try again later"
                else -> "Send failed (HTTP ${e.code()})"
            }
            throw RuntimeException(msg, e)
        } catch (e: java.io.IOException) {
            throw RuntimeException("Network error — check your connection and try again", e)
        } catch (e: Exception) {
            throw RuntimeException("Send failed: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }

    override suspend fun getSendAsAliases(): List<SendAsAlias> {
        return try {
            val response = api.getSendAsAliases()
            response.sendAs?.map { alias ->
                SendAsAlias(
                    email = alias.sendAsEmail,
                    displayName = alias.displayName ?: "",
                    isDefault = alias.isDefault,
                    isVerified = alias.isVerified
                )
            } ?: emptyList()
        } catch (e: Exception) {
            android.util.Log.e("GmailProvider", "Failed to fetch send-as aliases", e)
            emptyList()
        }
    }

    private fun parseFromHeader(raw: String): Pair<String, String> {
        val match = Regex("""^(.*?)\s*<(.+?)>$""").find(raw.trim())
        return if (match != null) {
            val name = match.groupValues[1].trim().removeSurrounding("\"")
            val email = match.groupValues[2].trim()
            Pair(name.ifEmpty { email }, email)
        } else {
            Pair(raw.trim(), raw.trim())
        }
    }
}
