package com.shrivatsav.monomail.core.network.provider
import com.shrivatsav.monomail.data.model.EmailAttachment

data class SendEmailOptions(
    val cc: String = "",
    val bcc: String = "",
    val threadId: String? = null,
    val inReplyToMessageId: String? = null,
    val references: String? = null,
    val attachments: List<EmailAttachment> = emptyList()
)

interface EmailProvider {
    val providerName: String  
    suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int = 20,
        pageToken: String? = null,
        query: String? = null
    ): ProviderThreadListResult
    suspend fun getThread(threadId: String, folderHints: List<String> = emptyList()): ProviderThread
    suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray?
    suspend fun archiveThread(threadId: String)
    suspend fun unarchiveThread(threadId: String)
    suspend fun trashThread(threadId: String)
    suspend fun restoreThread(threadId: String)
    suspend fun permanentlyDeleteThread(threadId: String)
    suspend fun toggleStar(threadId: String, starred: Boolean)
    suspend fun markRead(threadId: String, read: Boolean)
    suspend fun batchMarkRead(messageIds: List<String>)
    suspend fun sendEmail(
        from: String, to: String, subject: String, body: String,
        options: SendEmailOptions = SendEmailOptions()
    ): String?

    suspend fun getSendAsAliases(): List<SendAsAlias>
}
enum class EmailFolder { INBOX, SENT, ARCHIVE, STARRED, TRASH, SPAM }
