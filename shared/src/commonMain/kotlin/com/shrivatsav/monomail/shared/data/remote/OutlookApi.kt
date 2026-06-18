package com.shrivatsav.monomail.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class OutlookApi(private val client: HttpClient) {

    suspend fun listMessages(
        maxResults: Int = 20,
        skip: Int = 0,
        filter: String? = null,
        search: String? = null,
        select: String? = "id,conversationId,subject,from,toRecipients,bodyPreview,body,receivedDateTime,isRead,categories,hasAttachments",
        orderby: String? = null
    ): OutlookMessageListResponse {
        return client.get("https://graph.microsoft.com/v1.0/me/messages") {
            parameter("\$top", maxResults)
            parameter("\$skip", skip)
            filter?.let { parameter("\$filter", it) }
            search?.let { parameter("\$search", it) }
            select?.let { parameter("\$select", it) }
            orderby?.let { parameter("\$orderby", it) }
        }.body()
    }

    suspend fun getMessage(
        id: String,
        select: String? = "id,conversationId,subject,from,toRecipients,bodyPreview,body,receivedDateTime,isRead,categories,hasAttachments"
    ): OutlookMessage {
        return client.get("https://graph.microsoft.com/v1.0/me/messages/$id") {
            select?.let { parameter("\$select", it) }
        }.body()
    }

    suspend fun getAttachments(
        messageId: String
    ): OutlookAttachmentListResponse {
        return client.get("https://graph.microsoft.com/v1.0/me/messages/$messageId/attachments").body()
    }

    suspend fun getAttachment(
        messageId: String,
        attachmentId: String
    ): OutlookAttachment {
        return client.get("https://graph.microsoft.com/v1.0/me/messages/$messageId/attachments/$attachmentId").body()
    }

    suspend fun updateMessage(
        id: String,
        request: OutlookUpdateMessageRequest
    ) {
        client.patch("https://graph.microsoft.com/v1.0/me/messages/$id") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun moveMessage(
        id: String,
        request: OutlookMoveMessageRequest
    ) {
        client.post("https://graph.microsoft.com/v1.0/me/messages/$id/move") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun deleteMessage(
        id: String
    ) {
        client.delete("https://graph.microsoft.com/v1.0/me/messages/$id")
    }

    suspend fun sendMail(
        request: OutlookSendMailRequest
    ) {
        client.post("https://graph.microsoft.com/v1.0/me/sendMail") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }
}
