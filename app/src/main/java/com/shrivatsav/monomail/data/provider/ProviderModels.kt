package com.shrivatsav.monomail.data.provider
import com.shrivatsav.monomail.data.model.EmailAttachmentInfo

/**
 * Represents a send-as alias for an email account.
 */
data class SendAsAlias(
    val email: String,
    val displayName: String = "",
    val isDefault: Boolean = false,
    val isVerified: Boolean = false
)
data class ProviderThreadListResult(
    val threads: List<ProviderThread>,
    val nextPageToken: String?
)
data class ProviderThread(
    val threadId: String,
    val messages: List<ProviderMessage>
)
data class ProviderMessage(
    val id: String,
    val threadId: String,
    val subject: String,
    val from: String,
    val fromEmail: String,
    val to: String,
    val cc: String = "",
    val bcc: String = "",
    val snippet: String,
    val body: String,
    val bodyIsHtml: Boolean = true,
    val date: Long,
    val isRead: Boolean,
    val isStarred: Boolean,
    val folders: Set<EmailFolder>,
    val attachments: List<EmailAttachmentInfo>
)
