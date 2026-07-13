package com.shrivatsav.monomail.data.model
data class Email(
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
    val labels: List<String>,
    val attachments: List<EmailAttachmentInfo> = emptyList()
)
data class EmailAttachmentInfo(
    val id: String, 
    val messageId: String,
    val mimeType: String,
    val name: String,
    val size: Int
)
