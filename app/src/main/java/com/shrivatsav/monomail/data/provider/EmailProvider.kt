package com.shrivatsav.monomail.data.provider
import com.shrivatsav.monomail.data.model.EmailAttachment
interface EmailProvider {
    val providerName: String  
    suspend fun listThreads(
        folder: EmailFolder,
        maxResults: Int = 20,
        pageToken: String? = null,
        query: String? = null
    ): ProviderThreadListResult
    suspend fun getThread(threadId: String): ProviderThread
    suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray?
    suspend fun archiveThread(threadId: String)
    suspend fun unarchiveThread(threadId: String)
    suspend fun trashThread(threadId: String)
    suspend fun restoreThread(threadId: String)
    suspend fun toggleStar(threadId: String, starred: Boolean)
    suspend fun markRead(threadId: String, read: Boolean)
    suspend fun batchMarkRead(messageIds: List<String>)
    suspend fun sendEmail(
        from: String, to: String, subject: String, body: String,
        cc: String = "", bcc: String = "",
        threadId: String? = null, attachments: List<EmailAttachment> = emptyList()
    ): String?
}
enum class EmailFolder { INBOX, SENT, ARCHIVE, STARRED, TRASH }
