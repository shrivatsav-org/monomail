package com.shrivatsav.monomail.data.remote

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.POST
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.DELETE

interface OutlookApi {

    @GET("me/messages")
    suspend fun listMessages(
        @Query("\$top") maxResults: Int = 20,
        @Query("\$skip") skip: Int = 0,
        @Query("\$filter") filter: String? = null,
        @Query("\$search") search: String? = null,
        @Query("\$select") select: String? = "id,conversationId,subject,from,toRecipients,bodyPreview,body,receivedDateTime,isRead,categories,hasAttachments",
        @Query("\$orderby") orderby: String? = null
    ): OutlookMessageListResponse

    @GET("me/messages/{id}")
    suspend fun getMessage(
        @Path("id") id: String,
        @Query("\$select") select: String? = "id,conversationId,subject,from,toRecipients,bodyPreview,body,receivedDateTime,isRead,categories,hasAttachments"
    ): OutlookMessage

    @GET("me/messages/{id}/attachments")
    suspend fun getAttachments(
        @Path("id") messageId: String
    ): OutlookAttachmentListResponse

    @GET("me/messages/{messageId}/attachments/{attachmentId}")
    suspend fun getAttachment(
        @Path("messageId") messageId: String,
        @Path("attachmentId") attachmentId: String
    ): OutlookAttachment

    @PATCH("me/messages/{id}")
    suspend fun updateMessage(
        @Path("id") id: String,
        @Body request: OutlookUpdateMessageRequest
    )

    @POST("me/messages/{id}/move")
    suspend fun moveMessage(
        @Path("id") id: String,
        @Body request: OutlookMoveMessageRequest
    )

    @DELETE("me/messages/{id}")
    suspend fun deleteMessage(
        @Path("id") id: String
    )

    @POST("me/sendMail")
    suspend fun sendMail(
        @Body request: OutlookSendMailRequest
    )
}

data class OutlookMessageListResponse(
    val value: List<OutlookMessage>,
    @com.google.gson.annotations.SerializedName("@odata.nextLink") val nextLink: String?
)

data class OutlookMessage(
    val id: String,
    val conversationId: String,
    val subject: String?,
    val from: OutlookRecipient?,
    val toRecipients: List<OutlookRecipient>?,
    val bodyPreview: String?,
    val body: OutlookBody?,
    val receivedDateTime: String,
    val isRead: Boolean,
    val categories: List<String>?,
    val hasAttachments: Boolean?
)

data class OutlookRecipient(
    val emailAddress: OutlookEmailAddress
)

data class OutlookEmailAddress(
    val name: String?,
    val address: String
)

data class OutlookBody(
    val contentType: String,
    val content: String
)

data class OutlookAttachmentListResponse(
    val value: List<OutlookAttachment>
)

data class OutlookAttachment(
    val id: String,
    val name: String,
    val contentType: String,
    val contentBytes: String? // Base64 encoded
)

data class OutlookUpdateMessageRequest(
    val isRead: Boolean? = null,
    val categories: List<String>? = null
)

data class OutlookMoveMessageRequest(
    val destinationId: String // e.g. "deleteditems", "archive"
)

data class OutlookSendMailRequest(
    val message: OutlookDraftMessage,
    val saveToSentItems: Boolean = true
)

data class OutlookDraftMessage(
    val subject: String,
    val body: OutlookBody,
    val toRecipients: List<OutlookRecipient>,
    val attachments: List<OutlookDraftAttachment>? = null
)

data class OutlookDraftAttachment(
    @com.google.gson.annotations.SerializedName("@odata.type") val odataType: String = "#microsoft.graph.fileAttachment",
    val name: String,
    val contentType: String,
    val contentBytes: String // Base64 encoded
)
