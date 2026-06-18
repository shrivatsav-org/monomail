package com.shrivatsav.monomail.shared.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class OutlookMessageListResponse(
    val value: List<OutlookMessage>,
    @SerialName("@odata.nextLink") val nextLink: String? = null
)

@Serializable
data class OutlookMessage(
    val id: String,
    val conversationId: String,
    val subject: String? = null,
    val from: OutlookRecipient? = null,
    val toRecipients: List<OutlookRecipient>? = null,
    val bodyPreview: String? = null,
    val body: OutlookBody? = null,
    val receivedDateTime: String,
    val isRead: Boolean,
    val categories: List<String>? = null,
    val hasAttachments: Boolean? = null
)

@Serializable
data class OutlookRecipient(
    val emailAddress: OutlookEmailAddress
)

@Serializable
data class OutlookEmailAddress(
    val name: String? = null,
    val address: String
)

@Serializable
data class OutlookBody(
    val contentType: String,
    val content: String
)

@Serializable
data class OutlookAttachmentListResponse(
    val value: List<OutlookAttachment>
)

@Serializable
data class OutlookAttachment(
    val id: String,
    val name: String,
    val contentType: String,
    val contentBytes: String? = null
)

@Serializable
data class OutlookUpdateMessageRequest(
    val isRead: Boolean? = null,
    val categories: List<String>? = null
)

@Serializable
data class OutlookMoveMessageRequest(
    val destinationId: String 
)

@Serializable
data class OutlookSendMailRequest(
    val message: OutlookDraftMessage,
    val saveToSentItems: Boolean = true
)

@Serializable
data class OutlookDraftMessage(
    val subject: String,
    val body: OutlookBody,
    val toRecipients: List<OutlookRecipient>,
    val attachments: List<OutlookDraftAttachment>? = null
)

@Serializable
data class OutlookDraftAttachment(
    @SerialName("@odata.type") val odataType: String = "#microsoft.graph.fileAttachment",
    val name: String,
    val contentType: String,
    val contentBytes: String 
)
