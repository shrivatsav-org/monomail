package com.shrivatsav.monomail.core.network.remote
import com.google.gson.annotations.SerializedName
data class MessageListResponse(
    @SerializedName("messages") val messages: List<MessageRef>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("resultSizeEstimate") val resultSizeEstimate: Int?
)
data class MessageRef(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String
)
data class GmailMessage(
    @SerializedName("id") val id: String,
    @SerializedName("threadId") val threadId: String,
    @SerializedName("labelIds") val labelIds: List<String>?,
    @SerializedName("snippet") val snippet: String?,
    @SerializedName("payload") val payload: MessagePart?,
    @SerializedName("internalDate") val internalDate: String?,
    @SerializedName("sizeEstimate") val sizeEstimate: Int?
)
data class MessagePart(
    @SerializedName("mimeType") val mimeType: String?,
    @SerializedName("filename") val filename: String?,
    @SerializedName("headers") val headers: List<Header>?,
    @SerializedName("body") val body: MessagePartBody?,
    @SerializedName("parts") val parts: List<MessagePart>?
)
data class Header(
    @SerializedName("name") val name: String,
    @SerializedName("value") val value: String
)
data class MessagePartBody(
    @SerializedName("size") val size: Int?,
    @SerializedName("data") val data: String?,
    @SerializedName("attachmentId") val attachmentId: String?
)
data class GmailSendAsAlias(
    @SerializedName("sendAsEmail") val sendAsEmail: String,
    @SerializedName("displayName") val displayName: String? = null,
    @SerializedName("isDefault") val isDefault: Boolean = false,
    @SerializedName("isVerified") val isVerified: Boolean = false
)

data class GmailSendAsListResponse(
    @SerializedName("sendAs") val sendAs: List<GmailSendAsAlias>?
)

data class BatchModifyMessagesRequest(
    @SerializedName("ids") val ids: List<String>,
    @SerializedName("addLabelIds") val addLabelIds: List<String> = emptyList(),
    @SerializedName("removeLabelIds") val removeLabelIds: List<String> = emptyList()
)
data class ModifyThreadRequest(
    @SerializedName("addLabelIds") val addLabelIds: List<String> = emptyList(),
    @SerializedName("removeLabelIds") val removeLabelIds: List<String> = emptyList()
)
data class SendMessageRequest(
    @SerializedName("raw") val raw: String,
    @SerializedName("threadId") val threadId: String? = null
)
data class ThreadListResponse(
    @SerializedName("threads") val threads: List<ThreadRef>?,
    @SerializedName("nextPageToken") val nextPageToken: String?,
    @SerializedName("resultSizeEstimate") val resultSizeEstimate: Int?
)
data class ThreadRef(
    @SerializedName("id") val id: String,
    @SerializedName("snippet") val snippet: String?,
    @SerializedName("historyId") val historyId: String?
)
data class GmailThread(
    @SerializedName("id") val id: String,
    @SerializedName("messages") val messages: List<GmailMessage>?,
    @SerializedName("historyId") val historyId: String?
)
