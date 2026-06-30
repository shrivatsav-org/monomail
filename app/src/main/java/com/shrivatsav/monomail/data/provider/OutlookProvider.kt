package com.shrivatsav.monomail.data.provider
import android.content.Context
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.remote.OutlookApi
import com.shrivatsav.monomail.data.remote.OutlookBody
import com.shrivatsav.monomail.data.remote.OutlookDraftAttachment
import com.shrivatsav.monomail.data.remote.OutlookDraftMessage
import com.shrivatsav.monomail.data.remote.OutlookEmailAddress
import com.shrivatsav.monomail.data.remote.OutlookMessage
import com.shrivatsav.monomail.data.remote.OutlookMoveMessageRequest
import com.shrivatsav.monomail.data.remote.OutlookRecipient
import com.shrivatsav.monomail.data.remote.OutlookSendMailRequest
import com.shrivatsav.monomail.data.remote.OutlookUpdateMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import java.util.Locale
class OutlookProvider(
    private val api: OutlookApi,
    private val context: Context
) : EmailProvider {
    override val providerName: String = "outlook"
    private val folderIdCache = mutableMapOf<EmailFolder, String>()
    private val outlookFolderNames = mapOf(
        EmailFolder.INBOX to "inbox",
        EmailFolder.SENT to "sentitems",
        EmailFolder.ARCHIVE to "archive",
        EmailFolder.TRASH to "deleteditems",
        EmailFolder.SPAM to "junkemail"
    )

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            try {
                java.time.ZonedDateTime.parse(dateStr, java.time.format.DateTimeFormatter.ISO_DATE_TIME).toInstant().toEpochMilli()
            } catch (e2: Exception) {
                android.util.Log.w("OutlookProvider", "Failed to parse date: $dateStr", e2)
                0L
            }
        }
    }

    private fun messageDate(message: OutlookMessage): Long =
        parseDate(message.receivedDateTime ?: message.sentDateTime)

    private suspend fun ensureFolderIdCache() {
        outlookFolderNames.forEach { (folder, outlookName) ->
            if (!folderIdCache.containsKey(folder)) {
                try {
                    folderIdCache[folder] = api.getMailFolder(outlookName).id
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun folderFromParentFolderId(parentFolderId: String?): EmailFolder? {
        val normalized = parentFolderId?.lowercase(Locale.US) ?: return null
        outlookFolderNames.entries.firstOrNull { it.value == normalized }?.let { return it.key }
        return folderIdCache.entries.firstOrNull { (_, id) ->
            id.equals(parentFolderId, ignoreCase = true)
        }?.key
    }

    private fun foldersForMessage(
        message: OutlookMessage,
        fallback: EmailFolder? = null
    ): Set<EmailFolder> {
        val folders = mutableSetOf<EmailFolder>()
        folderFromParentFolderId(message.parentFolderId)?.let { folders.add(it) }
        if (message.categories?.contains("Yellow category") == true) {
            folders.add(EmailFolder.STARRED)
        }
        if (folders.isEmpty() && fallback != null) {
            folders.add(fallback)
        }
        return folders
    }

    private fun graphFolderName(folder: EmailFolder): String? = outlookFolderNames[folder]

    override suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int,
        pageToken: String?,
        query: String?
    ): ProviderThreadListResult {
        val skip = pageToken?.toIntOrNull() ?: 0
        ensureFolderIdCache()
        val response = when {
            !query.isNullOrEmpty() -> api.listMessages(
                maxResults = maxResults,
                skip = skip,
                search = query,
                orderby = "receivedDateTime DESC"
            )
            folder == EmailFolder.STARRED -> api.listMessages(
                maxResults = maxResults,
                skip = skip,
                filter = "categories/any(c:c eq 'Yellow category')",
                orderby = "receivedDateTime DESC"
            )
            else -> {
                val folderName = graphFolderName(folder)
                if (folderName != null) {
                    api.listFolderMessages(
                        folderId = folderName,
                        maxResults = maxResults,
                        skip = skip,
                        orderby = "receivedDateTime DESC"
                    )
                } else {
                    api.listMessages(
                        maxResults = maxResults,
                        skip = skip,
                        orderby = "receivedDateTime DESC"
                    )
                }
            }
        }
        val threadsMap = response.value.groupBy { it.conversationId }
        val providerThreads = threadsMap.map { (convId, messages) ->
            val providerMessages = messages.map { msg ->
                val date = messageDate(msg)
                ProviderMessage(
                    id = msg.id,
                    threadId = convId,
                    subject = msg.subject ?: "(no subject)",
                    from = msg.from?.emailAddress?.name ?: msg.from?.emailAddress?.address ?: "Unknown",
                    fromEmail = msg.from?.emailAddress?.address ?: "",
                    to = msg.toRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                    cc = msg.ccRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                    bcc = msg.bccRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                    snippet = msg.bodyPreview ?: "",
                    body = msg.body?.content ?: msg.bodyPreview ?: "",
                    bodyIsHtml = true,
                    date = date,
                    isRead = msg.isRead,
                    isStarred = msg.categories?.contains("Yellow category") == true,
                    folders = foldersForMessage(msg, fallback = folder),
                    attachments = emptyList()
                )
            }
            ProviderThread(convId, providerMessages)
        }
        val nextSkip = if (response.nextLink != null) (skip + maxResults).toString() else null
        return ProviderThreadListResult(providerThreads, nextSkip)
    }
    override suspend fun getThread(threadId: String, folderHints: List<String>): ProviderThread = withContext(Dispatchers.IO) {
        ensureFolderIdCache()
        val response = api.listMessages(
            maxResults = 100,
            filter = sanitizeFilter(threadId)
        )
        val providerMessages = response.value.map { msg ->
            val date = messageDate(msg)
            val attachments = if (msg.hasAttachments == true) {
                try {
                    api.getAttachments(msg.id).value.map { att ->
                        EmailAttachmentInfo(
                            id = att.id,
                            messageId = msg.id,
                            name = att.name,
                            mimeType = att.contentType,
                            size = 0 
                        )
                    }
                } catch (e: Exception) { emptyList() }
            } else { emptyList() }
            ProviderMessage(
                id = msg.id,
                threadId = msg.conversationId,
                subject = msg.subject ?: "(no subject)",
                from = msg.from?.emailAddress?.name ?: msg.from?.emailAddress?.address ?: "Unknown",
                fromEmail = msg.from?.emailAddress?.address ?: "",
                to = msg.toRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                cc = msg.ccRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                bcc = msg.bccRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                snippet = msg.bodyPreview ?: "",
                body = msg.body?.content ?: msg.bodyPreview ?: "",
                bodyIsHtml = true,
                date = date,
                isRead = msg.isRead,
                isStarred = msg.categories?.contains("Yellow category") == true,
                folders = foldersForMessage(msg),
                attachments = attachments
            )
        }
        ProviderThread(threadId, providerMessages.sortedBy { it.date })
    }
    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val att = api.getAttachment(messageId, attachmentId)
                att.contentBytes?.let {
                    android.util.Base64.decode(it, android.util.Base64.DEFAULT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }
    private fun sanitizeFilter(threadId: String): String = "conversationId eq '${threadId.replace("'", "''")}'"

    private fun outlookRecipientForEmail(email: String): OutlookRecipient? {
        if (email.isBlank()) return null
        return OutlookRecipient(OutlookEmailAddress(null, email.trim()))
    }

    override suspend fun archiveThread(threadId: String) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("archive")) }
    }
    override suspend fun unarchiveThread(threadId: String) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }
    override suspend fun trashThread(threadId: String) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("deleteditems")) }
    }
    override suspend fun restoreThread(threadId: String) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }
    override suspend fun permanentlyDeleteThread(threadId: String) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { api.deleteMessage(it.id) }
    }
    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { msg ->
            val categories = msg.categories?.toMutableList() ?: mutableListOf()
            if (starred) {
                if (!categories.contains("Yellow category")) categories.add("Yellow category")
            } else {
                categories.remove("Yellow category")
            }
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(categories = categories))
        }
    }
    override suspend fun markRead(threadId: String, read: Boolean) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        msgs.forEach { msg ->
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(isRead = read))
        }
    }
    override suspend fun batchMarkRead(messageIds: List<String>) {
        withContext(Dispatchers.IO) {
            val limitedParallelism = Dispatchers.IO.limitedParallelism(3)
            coroutineScope {
                messageIds.map { id ->
                    async(limitedParallelism) {
                        try { api.updateMessage(id, OutlookUpdateMessageRequest(isRead = true)) } catch (_: Exception) {}
                    }
                }.awaitAll()
            }
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
        val recipients = to.split(",").map {
            OutlookRecipient(OutlookEmailAddress(null, it.trim()))
        }
        val ccRecipients = if (cc.isNotBlank()) {
            cc.split(",").map { OutlookRecipient(OutlookEmailAddress(null, it.trim())) }
        } else null
        val bccRecipients = if (bcc.isNotBlank()) {
            bcc.split(",").map { OutlookRecipient(OutlookEmailAddress(null, it.trim())) }
        } else null
        val draftAttachments = attachments.mapNotNull { att ->
            context.contentResolver.openInputStream(att.uri)?.use { stream ->
                var size = stream.available().toLong()
                try {
                    context.contentResolver.query(att.uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex != -1) {
                                size = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (_: Exception) {}
                if (size > 3 * 1024 * 1024) {
                    throw IllegalArgumentException("Attachment ${att.name} exceeds the 3MB limit for Outlook.")
                }
                val bytes = stream.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                OutlookDraftAttachment(
                    name = att.name,
                    contentType = att.mimeType,
                    contentBytes = base64
                )
            }
        }
        val senderRecipient = outlookRecipientForEmail(from)

        val msg = OutlookDraftMessage(
            subject = subject,
            body = OutlookBody("HTML", body),
            toRecipients = recipients,
            ccRecipients = ccRecipients,
            bccRecipients = bccRecipients,
            attachments = draftAttachments.takeIf { it.isNotEmpty() },
            sender = senderRecipient
        )
        api.sendMail(OutlookSendMailRequest(msg))
        return null
    }

    override suspend fun getSendAsAliases(): List<SendAsAlias> {
        // Personal Microsoft accounts (the current scope) don't support send-as
        // Organizational accounts would require /users/{id}/mailboxSettings
        return emptyList()
    }
}
