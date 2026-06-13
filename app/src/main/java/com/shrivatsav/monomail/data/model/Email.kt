package com.shrivatsav.monomail.data.model

/**
 * Clean domain model for an email.
 * Decoupled from the Gmail API response structure.
 */
data class Email(
    val id: String,
    val threadId: String,
    val subject: String,
    val from: String,        // display name (e.g. "John Doe")
    val fromEmail: String,   // raw email (e.g. "john@example.com")
    val to: String,
    val snippet: String,
    val body: String,
    val date: Long,          // epoch millis
    val isRead: Boolean,
    val isStarred: Boolean,
    val labels: List<String>,
    val attachments: List<EmailAttachmentInfo> = emptyList()
)

data class EmailAttachmentInfo(
    val id: String, // attachmentId
    val messageId: String,
    val mimeType: String,
    val name: String,
    val size: Int
)
