package com.shrivatsav.monomail.data.remote
import com.google.gson.annotations.SerializedName
data class MessageListResponse(
    val messages: List<MessageRef>?,
    val nextPageToken: String?,
    val resultSizeEstimate: Int?
)
data class MessageRef(
    val id: String,
    val threadId: String
)
data class GmailMessage(
    val id: String,
    val threadId: String,
    val labelIds: List<String>?,
    val snippet: String?,
    val payload: MessagePart?,
    val internalDate: String?,
    val sizeEstimate: Int?
)
data class MessagePart(
    val mimeType: String?,
    val filename: String?,
    val headers: List<Header>?,
    val body: MessagePartBody?,
    val parts: List<MessagePart>?
)
data class Header(
    val name: String,
    val value: String
)
data class MessagePartBody(
    val size: Int?,
    val data: String?,
    val attachmentId: String?
)
data class GmailProfile(
    val emailAddress: String?,
    val messagesTotal: Int?,
    val threadsTotal: Int?,
    val historyId: String?
)
data class BatchModifyMessagesRequest(
    val ids: List<String>,
    val addLabelIds: List<String> = emptyList(),
    val removeLabelIds: List<String> = emptyList()
)
data class ModifyThreadRequest(
    val addLabelIds: List<String> = emptyList(),
    val removeLabelIds: List<String> = emptyList()
)
data class SendMessageRequest(
    val raw: String,
    val threadId: String? = null
)
data class ThreadListResponse(
    val threads: List<ThreadRef>?,
    val nextPageToken: String?,
    val resultSizeEstimate: Int?
)
data class ThreadRef(
    val id: String,
    val snippet: String?,
    val historyId: String?
)
data class GmailThread(
    val id: String,
    val messages: List<GmailMessage>?,
    val historyId: String?
)
