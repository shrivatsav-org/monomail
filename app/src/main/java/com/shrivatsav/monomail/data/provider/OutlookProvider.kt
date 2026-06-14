package com.shrivatsav.monomail.data.provider

import android.content.Context
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.data.remote.OutlookApi
import com.shrivatsav.monomail.data.remote.OutlookBody
import com.shrivatsav.monomail.data.remote.OutlookDraftAttachment
import com.shrivatsav.monomail.data.remote.OutlookDraftMessage
import com.shrivatsav.monomail.data.remote.OutlookEmailAddress
import com.shrivatsav.monomail.data.remote.OutlookMoveMessageRequest
import com.shrivatsav.monomail.data.remote.OutlookRecipient
import com.shrivatsav.monomail.data.remote.OutlookSendMailRequest
import com.shrivatsav.monomail.data.remote.OutlookUpdateMessageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OutlookProvider(
    private val api: OutlookApi,
    private val context: Context
) : EmailProvider {

    override val providerName: String = "outlook"

    // Helper to parse ISO8601 date, Graph API uses format like 2024-01-15T10:30:00.0000000Z
    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            java.time.Instant.parse(dateStr).toEpochMilli()
        } catch (e: Exception) {
            0L
        }
    }

    override suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int,
        pageToken: String?,
        query: String?
    ): ProviderThreadListResult {
        // MS Graph API uses $skip for pagination if using offset, or nextLink
        val skip = pageToken?.toIntOrNull() ?: 0

        val filter = buildString {
            if (!query.isNullOrEmpty()) {
                // Not ideal, Outlook uses $search instead of $filter for text searches, but we map it
            } else {
                when (folder) {
                    EmailFolder.INBOX -> append("parentFolderId eq 'inbox'")
                    EmailFolder.SENT -> append("parentFolderId eq 'sentitems'")
                    EmailFolder.ARCHIVE -> append("parentFolderId eq 'archive'")
                    EmailFolder.TRASH -> append("parentFolderId eq 'deleteditems'")
                    EmailFolder.STARRED -> append("categories/any(c:c eq 'Yellow category')") // Approximating Starred
                }
            }
        }.takeIf { it.isNotEmpty() }

        val search = query.takeIf { !it.isNullOrEmpty() }

        val response = api.listMessages(
            maxResults = maxResults,
            skip = skip,
            filter = filter,
            search = search
        )

        // Group messages into threads by conversationId
        val threadsMap = response.value.groupBy { it.conversationId }
        val providerThreads = threadsMap.map { (convId, messages) ->
            val providerMessages = messages.map { msg ->
                val date = parseDate(msg.receivedDateTime)
                
                // Fetch attachments if hasAttachments is true (this requires another API call per message, 
                // but for listing we might just skip attachment details or fetch them lazily. Let's just pass empty for now)
                
                val folders = mutableSetOf<EmailFolder>()
                // Simplification: We assign the requested folder since Graph API doesn't easily return all folder names in the message payload
                folders.add(folder)
                
                ProviderMessage(
                    id = msg.id,
                    threadId = convId,
                    subject = msg.subject ?: "(no subject)",
                    from = msg.from?.emailAddress?.name ?: msg.from?.emailAddress?.address ?: "Unknown",
                    fromEmail = msg.from?.emailAddress?.address ?: "",
                    to = msg.toRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                    snippet = msg.bodyPreview ?: "",
                    body = msg.body?.content ?: msg.bodyPreview ?: "",
                    date = date,
                    isRead = msg.isRead,
                    isStarred = msg.categories?.contains("Yellow category") == true,
                    folders = folders,
                    attachments = emptyList() // Needs separate API call
                )
            }
            ProviderThread(convId, providerMessages)
        }

        // MSAL pagination: nextLink is a full URL. We just use a simple offset for now.
        val nextSkip = if (response.nextLink != null) (skip + maxResults).toString() else null

        return ProviderThreadListResult(providerThreads, nextSkip)
    }

    override suspend fun getThread(threadId: String): ProviderThread {
        // Outlook Graph API doesn't have a direct "get thread" endpoint that returns all messages with full bodies easily,
        // unless we query messages filtered by conversationId
        val response = api.listMessages(
            maxResults = 100,
            filter = "conversationId eq '$threadId'"
        )

        val providerMessages = response.value.map { msg ->
            val date = parseDate(msg.receivedDateTime)
            
            val attachments = if (msg.hasAttachments == true) {
                try {
                    api.getAttachments(msg.id).value.map { att ->
                        EmailAttachmentInfo(
                            id = att.id,
                            messageId = msg.id,
                            name = att.name,
                            mimeType = att.contentType,
                            size = 0 // size not always available in summary
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
                snippet = msg.bodyPreview ?: "",
                body = msg.body?.content ?: msg.bodyPreview ?: "",
                date = date,
                isRead = msg.isRead,
                isStarred = msg.categories?.contains("Yellow category") == true,
                folders = setOf(EmailFolder.INBOX), // Simplification
                attachments = attachments
            )
        }

        return ProviderThread(threadId, providerMessages.sortedBy { it.date })
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

    override suspend fun archiveThread(threadId: String) {
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("archive")) }
    }

    override suspend fun unarchiveThread(threadId: String) {
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }

    override suspend fun trashThread(threadId: String) {
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("deleteditems")) }
    }

    override suspend fun restoreThread(threadId: String) {
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
        msgs.forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }

    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
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
        val msgs = api.listMessages(filter = "conversationId eq '$threadId'").value
        msgs.forEach { msg ->
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(isRead = read))
        }
    }

    override suspend fun batchMarkRead(messageIds: List<String>) {
        // Outlook API doesn't have a direct batch endpoint without using $batch API which is complex.
        // We do it sequentially or concurrently.
        withContext(Dispatchers.IO) {
            messageIds.forEach { id ->
                try { api.updateMessage(id, OutlookUpdateMessageRequest(isRead = true)) } catch (e: Exception) {}
            }
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
        val recipients = to.split(",").map {
            OutlookRecipient(OutlookEmailAddress(null, it.trim()))
        }

        val draftAttachments = attachments.mapNotNull { att ->
            context.contentResolver.openInputStream(att.uri)?.use { stream ->
                val bytes = stream.readBytes()
                val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                OutlookDraftAttachment(
                    name = att.name,
                    contentType = att.mimeType,
                    contentBytes = base64
                )
            }
        }

        val msg = OutlookDraftMessage(
            subject = subject,
            body = OutlookBody("HTML", body),
            toRecipients = recipients,
            attachments = draftAttachments.takeIf { it.isNotEmpty() }
        )

        api.sendMail(OutlookSendMailRequest(msg))
    }
}
