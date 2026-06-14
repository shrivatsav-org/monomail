package com.shrivatsav.monomail.data.provider

import com.shrivatsav.monomail.data.model.EmailAttachmentInfo

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
    val snippet: String, 
    val body: String,
    val date: Long, 
    val isRead: Boolean, 
    val isStarred: Boolean,
    val folders: Set<EmailFolder>,
    val attachments: List<EmailAttachmentInfo>
)
