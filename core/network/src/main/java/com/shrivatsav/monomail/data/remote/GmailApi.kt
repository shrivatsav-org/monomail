package com.shrivatsav.monomail.data.remote
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.DELETE
interface GmailApi {
    @GET("users/me/messages")
    suspend fun listMessages(
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("labelIds") labelIds: String? = "INBOX",
        @Query("q") query: String? = null
    ): MessageListResponse
    @GET("users/me/messages/{id}")
    suspend fun getMessage(
        @Path("id") id: String,
        @Query("format") format: String = "full"
    ): GmailMessage
    @GET("users/me/messages/{messageId}/attachments/{id}")
    suspend fun getAttachment(
        @Path("messageId") messageId: String,
        @Path("id") id: String
    ): MessagePartBody
    @GET("users/me/settings/sendAs")
    suspend fun getSendAsAliases(): GmailSendAsListResponse

    @POST("users/me/messages/batchModify")
    suspend fun batchModifyMessages(
        @Body request: BatchModifyMessagesRequest
    )
    @POST("users/me/messages/send")
    suspend fun sendMessage(
        @Body body: SendMessageRequest
    ): GmailMessage
    @GET("users/me/threads")
    suspend fun listThreads(
        @Query("maxResults") maxResults: Int = 20,
        @Query("pageToken") pageToken: String? = null,
        @Query("labelIds") labelIds: String? = "INBOX",
        @Query("q") query: String? = null
    ): ThreadListResponse
    @GET("users/me/threads/{id}")
    suspend fun getThread(
        @Path("id") id: String,
        @Query("format") format: String = "full"
    ): GmailThread
    @POST("users/me/threads/{id}/modify")
    suspend fun modifyThread(
        @Path("id") id: String,
        @Body request: ModifyThreadRequest
    ): GmailThread
    @POST("users/me/threads/{id}/trash")
    suspend fun trashThread(
        @Path("id") id: String
    ): GmailThread
    @POST("users/me/threads/{id}/untrash")
    suspend fun untrashThread(
        @Path("id") id: String
    ): GmailThread
    @DELETE("users/me/threads/{id}")
    suspend fun permanentlyDeleteThread(
        @Path("id") id: String
    )
}
