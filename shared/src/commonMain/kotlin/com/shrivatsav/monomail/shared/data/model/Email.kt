package com.shrivatsav.monomail.shared.data.model

import kotlinx.serialization.Serializable

data class Email(
    val id: String,
    val threadId: String,
    val subject: String,
    val from: String,        
    val fromEmail: String,   
    val to: String,
    val snippet: String,
    val body: String,
    val date: Long,          
    val isRead: Boolean,
    val isStarred: Boolean,
    val labels: List<String>,
    val attachments: List<EmailAttachmentInfo> = emptyList()
)
@Serializable
data class EmailAttachmentInfo(
    val id: String,
    val messageId: String,
    val mimeType: String,
    val name: String,
    val size: Int
)
