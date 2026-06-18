package com.shrivatsav.monomail.shared.data.provider

import com.shrivatsav.monomail.shared.data.model.EmailAttachment
import com.shrivatsav.monomail.shared.data.model.EmailAttachmentInfo
import com.shrivatsav.monomail.shared.data.remote.OutlookApi
import com.shrivatsav.monomail.shared.data.remote.OutlookBody
import com.shrivatsav.monomail.shared.data.remote.OutlookDraftAttachment
import com.shrivatsav.monomail.shared.data.remote.OutlookDraftMessage
import com.shrivatsav.monomail.shared.data.remote.OutlookEmailAddress
import com.shrivatsav.monomail.shared.data.remote.OutlookMoveMessageRequest
import com.shrivatsav.monomail.shared.data.remote.OutlookRecipient
import com.shrivatsav.monomail.shared.data.remote.OutlookSendMailRequest
import com.shrivatsav.monomail.shared.data.remote.OutlookUpdateMessageRequest
import com.shrivatsav.monomail.shared.util.Base64Util
import kotlinx.datetime.Instant

class OutlookProvider(
    private val api: OutlookApi
) : EmailProvider {
    override val providerName: String = "outlook"

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            Instant.parse(dateStr).toEpochMilliseconds()
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
        val skip = pageToken?.toIntOrNull() ?: 0
        val filter = buildString {
            if (!query.isNullOrEmpty()) {
                // free-text search handled via search param below
            } else {
                when (folder) {
                    EmailFolder.INBOX -> append("parentFolderId eq 'inbox'")
                    EmailFolder.SENT -> append("parentFolderId eq 'sentitems'")
                    EmailFolder.ARCHIVE -> append("parentFolderId eq 'archive'")
                    EmailFolder.TRASH -> append("parentFolderId eq 'deleteditems'")
                    EmailFolder.STARRED -> append("categories/any(c:c eq 'Yellow category')")
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
        val threadsMap = response.value.groupBy { it.conversationId }
        val providerThreads = threadsMap.map { (convId, messages) ->
            val providerMessages = messages.map { msg ->
                ProviderMessage(
                    id = msg.id,
                    threadId = convId,
                    subject = msg.subject ?: "(no subject)",
                    from = msg.from?.emailAddress?.name ?: msg.from?.emailAddress?.address ?: "Unknown",
                    fromEmail = msg.from?.emailAddress?.address ?: "",
                    to = msg.toRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                    snippet = msg.bodyPreview ?: "",
                    body = msg.body?.content ?: msg.bodyPreview ?: "",
                    date = parseDate(msg.receivedDateTime),
                    isRead = msg.isRead,
                    isStarred = msg.categories?.contains("Yellow category") == true,
                    folders = setOf(folder),
                    attachments = emptyList()
                )
            }
            ProviderThread(convId, providerMessages)
        }
        val nextSkip = if (response.nextLink != null) (skip + maxResults).toString() else null
        return ProviderThreadListResult(providerThreads, nextSkip)
    }

    override suspend fun getThread(threadId: String): ProviderThread {
        val response = api.listMessages(
            maxResults = 100,
            filter = "conversationId eq '$threadId'"
        )
        val providerMessages = response.value.map { msg ->
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
                } catch (e: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            ProviderMessage(
                id = msg.id,
                threadId = msg.conversationId,
                subject = msg.subject ?: "(no subject)",
                from = msg.from?.emailAddress?.name ?: msg.from?.emailAddress?.address ?: "Unknown",
                fromEmail = msg.from?.emailAddress?.address ?: "",
                to = msg.toRecipients?.joinToString(", ") { it.emailAddress.name ?: it.emailAddress.address } ?: "",
                snippet = msg.bodyPreview ?: "",
                body = msg.body?.content ?: msg.bodyPreview ?: "",
                date = parseDate(msg.receivedDateTime),
                isRead = msg.isRead,
                isStarred = msg.categories?.contains("Yellow category") == true,
                folders = setOf(EmailFolder.INBOX),
                attachments = attachments
            )
        }
        return ProviderThread(threadId, providerMessages.sortedBy { it.date })
    }

    override suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return try {
            val att = api.getAttachment(messageId, attachmentId)
            att.contentBytes?.let { Base64Util.decodeStandard(it) }
        } catch (e: Exception) {
            null
        }
    }

    override suspend fun archiveThread(threadId: String) {
        api.listMessages(filter = "conversationId eq '$threadId'").value
            .forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("archive")) }
    }

    override suspend fun unarchiveThread(threadId: String) {
        api.listMessages(filter = "conversationId eq '$threadId'").value
            .forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }

    override suspend fun trashThread(threadId: String) {
        api.listMessages(filter = "conversationId eq '$threadId'").value
            .forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("deleteditems")) }
    }

    override suspend fun restoreThread(threadId: String) {
        api.listMessages(filter = "conversationId eq '$threadId'").value
            .forEach { api.moveMessage(it.id, OutlookMoveMessageRequest("inbox")) }
    }

    override suspend fun toggleStar(threadId: String, starred: Boolean) {
        api.listMessages(filter = "conversationId eq '$threadId'").value.forEach { msg ->
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
        api.listMessages(filter = "conversationId eq '$threadId'").value.forEach { msg ->
            api.updateMessage(msg.id, OutlookUpdateMessageRequest(isRead = read))
        }
    }

    override suspend fun batchMarkRead(messageIds: List<String>) {
        messageIds.forEach { id ->
            try {
                api.updateMessage(id, OutlookUpdateMessageRequest(isRead = true))
            } catch (e: Exception) {
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
        val draftAttachments = attachments.map { att ->
            if (att.size > 3 * 1024 * 1024) {
                throw IllegalArgumentException("Attachment ${att.name} exceeds the 3MB limit for Outlook.")
            }
            OutlookDraftAttachment(
                name = att.name,
                contentType = att.mimeType,
                contentBytes = Base64Util.encodeStandardNoWrap(att.bytes)
            )
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
