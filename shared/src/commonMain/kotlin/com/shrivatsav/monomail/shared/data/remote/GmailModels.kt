package com.shrivatsav.monomail.shared.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class MessageListResponse(
    val messages: List<MessageRef>? = null,
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int? = null
)

@Serializable
data class MessageRef(
    val id: String,
    val threadId: String
)

@Serializable
data class GmailMessage(
    val id: String,
    val threadId: String,
    val labelIds: List<String>? = null,
    val snippet: String? = null,
    val payload: MessagePart? = null,
    val internalDate: String? = null,
    val sizeEstimate: Int? = null
)

@Serializable
data class MessagePart(
    val mimeType: String? = null,
    val filename: String? = null,
    val headers: List<Header>? = null,
    val body: MessagePartBody? = null,
    val parts: List<MessagePart>? = null
)

@Serializable
data class Header(
    val name: String,
    val value: String
)

@Serializable
data class MessagePartBody(
    val size: Int? = null,
    val data: String? = null,
    val attachmentId: String? = null
)

@Serializable
data class GmailProfile(
    val emailAddress: String? = null,
    val messagesTotal: Int? = null,
    val threadsTotal: Int? = null,
    val historyId: String? = null
)

@Serializable
data class BatchModifyMessagesRequest(
    val ids: List<String>,
    val addLabelIds: List<String> = emptyList(),
    val removeLabelIds: List<String> = emptyList()
)

@Serializable
data class ModifyThreadRequest(
    val addLabelIds: List<String> = emptyList(),
    val removeLabelIds: List<String> = emptyList()
)

@Serializable
data class SendMessageRequest(
    val raw: String,
    val threadId: String? = null
)

@Serializable
data class ThreadListResponse(
    val threads: List<ThreadRef>? = null,
    val nextPageToken: String? = null,
    val resultSizeEstimate: Int? = null
)

@Serializable
data class ThreadRef(
    val id: String,
    val snippet: String? = null,
    val historyId: String? = null
)

@Serializable
data class GmailThread(
    val id: String,
    val messages: List<GmailMessage>? = null,
    val historyId: String? = null
)
