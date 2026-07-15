package com.shrivatsav.monomail.core.network.provider
import android.content.Context
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.core.network.remote.OutlookApi
import com.shrivatsav.monomail.core.network.remote.OutlookBody
import com.shrivatsav.monomail.core.network.remote.OutlookDraftAttachment
import com.shrivatsav.monomail.core.network.remote.OutlookDraftMessage
import com.shrivatsav.monomail.core.network.remote.OutlookEmailAddress
import com.shrivatsav.monomail.core.network.remote.OutlookMessage
import com.shrivatsav.monomail.core.network.remote.OutlookMoveMessageRequest
import com.shrivatsav.monomail.core.network.remote.OutlookRecipient
import com.shrivatsav.monomail.core.network.remote.OutlookSendMailRequest
import com.shrivatsav.monomail.core.network.remote.OutlookUpdateMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.util.Locale
import java.util.UUID
class OutlookProvider(
    private val api: OutlookApi,
    private val context: Context
) : EmailProvider {
    companion object {
        private const val STAR_CATEGORY = "Yellow category"
        private const val RECEIVED_DATE_ORDER = "receivedDateTime DESC"
    }
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
                } catch (e: Exception) {
                    android.util.Log.w("OutlookProvider", "Failed to cache folder id for $outlookName", e)
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
        if (message.categories?.contains(STAR_CATEGORY) == true) {
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
                orderby = RECEIVED_DATE_ORDER
            )
            folder == EmailFolder.STARRED -> api.listMessages(
                maxResults = maxResults,
                skip = skip,
                filter = "categories/any(c:c eq 'Yellow category')",
                orderby = RECEIVED_DATE_ORDER
            )
            else -> {
                val folderName = graphFolderName(folder)
                if (folderName != null) {
                    api.listFolderMessages(
                        folderId = folderName,
                        maxResults = maxResults,
                        skip = skip,
                        orderby = RECEIVED_DATE_ORDER
                    )
                } else {
                    api.listMessages(
                        maxResults = maxResults,
                        skip = skip,
                        orderby = RECEIVED_DATE_ORDER
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
                    isStarred = msg.categories?.contains(STAR_CATEGORY) == true,
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
        val response = try {
            api.listMessages(
                maxResults = 100,
                filter = sanitizeFilter(threadId)
            )
        } catch (e: HttpException) {
            if (e.code() in setOf(404, 410)) throw ResourceNotFoundException("Thread $threadId not found", e)
            throw e
        }
        if (response.value.isEmpty()) {
            throw ResourceNotFoundException("Thread $threadId not found — empty result set")
        }
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
                isStarred = msg.categories?.contains(STAR_CATEGORY) == true,
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
                android.util.Log.e("OutlookProvider", "getAttachmentBytes error", e)
                null
            }
        }
    }
    private fun sanitizeFilter(threadId: String): String = "conversationId eq '${threadId.replace("'", "''")}'"

    /**
     * Lists all messages in a thread and applies [operation] to each
     * concurrently using [limitedParallelism] to avoid N+1 sequential
     * API calls. Falls back to sequential on error.
     */
    private suspend fun forEachMessageInThread(
        threadId: String,
        parallelism: Int = 3,
        operation: suspend (OutlookMessage) -> Unit
    ) {
        val msgs = api.listMessages(filter = sanitizeFilter(threadId)).value
        if (msgs.size <= 1) {
            msgs.forEach { operation(it) }
            return
        }
        coroutineScope {
            msgs.map { msg ->
                async(Dispatchers.IO.limitedParallelism(parallelism)) {
                    try { operation(msg) } catch (e: Exception) {
                        android.util.Log.w("OutlookProvider", "Failed operation for msg ${msg.id} in thread $threadId", e)
                    }
                }
            }.awaitAll()
        }
    }

    /** Reusable move-to-folder helper used by archive/unarchive/trash/restore. */
    private fun moveToFolder(destinationId: String): suspend (OutlookMessage) -> Unit =
        { msg -> api.moveMessage(msg.id, OutlookMoveMessageRequest(destinationId)) }

    override suspend fun archiveThread(threadId: String) {
        forEachMessageInThread(threadId, operation = moveToFolder("archive"))
    }
    override suspend fun unarchiveThread(threadId: String) {
        forEachMessageInThread(threadId, operation = moveToFolder("inbox"))
    }
    override suspend fun trashThread(threadId: String) {
        forEachMessageInThread(threadId, operation = moveToFolder("deleteditems"))
    }
    override suspend fun restoreThread(threadId: String) {
        forEachMessageInThread(threadId, operation = moveToFolder("inbox"))
    }
    override suspend fun permanentlyDeleteThread(threadId: String) {
        forEachMessageInThread(threadId) { msg ->
            api.deleteMessage(msg.id)
        }
    }
    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        forEachMessageInThread(threadId) { msg ->
            val categories = msg.categories?.toMutableList() ?: mutableListOf()
            if (starred) {
                if (!categories.contains(STAR_CATEGORY)) categories.add(STAR_CATEGORY)
            } else {
                categories.remove(STAR_CATEGORY)
            }
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(categories = categories))
        }
    }
    override suspend fun markRead(threadId: String, read: Boolean) {
        forEachMessageInThread(threadId) { msg ->
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(isRead = read))
        }
    }
    override suspend fun batchMarkRead(messageIds: List<String>) {
        withContext(Dispatchers.IO) {
            val limitedParallelism = Dispatchers.IO.limitedParallelism(3)
            coroutineScope {
                messageIds.map { id ->
                    async(limitedParallelism) {
                        try { api.updateMessage(id, OutlookUpdateMessageRequest(isRead = true)) } catch (e: Exception) {
                            android.util.Log.w("OutlookProvider", "Failed to mark message $id as read", e)
                        }
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
        options: SendEmailOptions
    ): SendEmailResult? {
        val recipients = parseRecipients(to)
        val ccRecipients = options.cc.takeIf { it.isNotBlank() }?.let { parseRecipients(it) }
        val bccRecipients = options.bcc.takeIf { it.isNotBlank() }?.let { parseRecipients(it) }
        val draftAttachments = options.attachments.mapNotNull { encodeAttachment(it) }

        val msg = OutlookDraftMessage(
            subject = subject,
            body = OutlookBody("HTML", body),
            toRecipients = recipients,
            ccRecipients = ccRecipients,
            bccRecipients = bccRecipients,
            attachments = draftAttachments.takeIf { it.isNotEmpty() }
        )
        api.sendMail(OutlookSendMailRequest(msg))
        val threadId = options.threadId ?: UUID.randomUUID().toString()
        return SendEmailResult(messageId = null, threadId = threadId)
    }

    private fun parseRecipients(csv: String): List<OutlookRecipient> {
        return csv.split(",").map { OutlookRecipient(OutlookEmailAddress(null, it.trim())) }
    }

    private fun encodeAttachment(att: EmailAttachment): OutlookDraftAttachment? {
        return context.contentResolver.openInputStream(att.uri)?.use { stream ->
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
            } catch (e: Exception) {
                android.util.Log.w("OutlookProvider", "Failed to query attachment size for ${att.name}", e)
            }
            require(size <= 3 * 1024 * 1024) { "Attachment ${att.name} exceeds the 3MB limit for Outlook." }
            val base64 = android.util.Base64.encodeToString(stream.readBytes(), android.util.Base64.NO_WRAP)
            OutlookDraftAttachment(name = att.name, contentType = att.mimeType, contentBytes = base64)
        }
    }

    override suspend fun getSendAsAliases(): List<SendAsAlias> {
        // Personal Microsoft accounts (the current scope) don't support send-as
        // Organizational accounts would require /users/{id}/mailboxSettings
        return emptyList()
    }
}
