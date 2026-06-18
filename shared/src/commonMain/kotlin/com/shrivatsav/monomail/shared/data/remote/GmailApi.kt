package com.shrivatsav.monomail.shared.data.remote

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*

class GmailApi(private val client: HttpClient) {

    suspend fun listMessages(
        maxResults: Int = 20,
        pageToken: String? = null,
        labelIds: String? = "INBOX",
        query: String? = null
    ): MessageListResponse {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/messages") {
            parameter("maxResults", maxResults)
            pageToken?.let { parameter("pageToken", it) }
            labelIds?.let { parameter("labelIds", it) }
            query?.let { parameter("q", it) }
        }.body()
    }

    suspend fun getMessage(
        id: String,
        format: String = "full"
    ): GmailMessage {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$id") {
            parameter("format", format)
        }.body()
    }

    suspend fun getAttachment(
        messageId: String,
        id: String
    ): MessagePartBody {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/messages/$messageId/attachments/$id")
            .body()
    }

    suspend fun getProfile(): GmailProfile {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/profile").body()
    }

    suspend fun batchModifyMessages(
        request: BatchModifyMessagesRequest
    ) {
        client.post("https://gmail.googleapis.com/gmail/v1/users/me/messages/batchModify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    suspend fun sendMessage(
        body: SendMessageRequest
    ): GmailMessage {
        return client.post("https://gmail.googleapis.com/gmail/v1/users/me/messages/send") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }.body()
    }

    suspend fun listThreads(
        maxResults: Int = 20,
        pageToken: String? = null,
        labelIds: String? = "INBOX",
        query: String? = null
    ): ThreadListResponse {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/threads") {
            parameter("maxResults", maxResults)
            pageToken?.let { parameter("pageToken", it) }
            labelIds?.let { parameter("labelIds", it) }
            query?.let { parameter("q", it) }
        }.body()
    }

    suspend fun getThread(
        id: String,
        format: String = "full"
    ): GmailThread {
        return client.get("https://gmail.googleapis.com/gmail/v1/users/me/threads/$id") {
            parameter("format", format)
        }.body()
    }

    suspend fun modifyThread(
        id: String,
        request: ModifyThreadRequest
    ): GmailThread {
        return client.post("https://gmail.googleapis.com/gmail/v1/users/me/threads/$id/modify") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun trashThread(
        id: String
    ): GmailThread {
        return client.post("https://gmail.googleapis.com/gmail/v1/users/me/threads/$id/trash").body()
    }

    suspend fun untrashThread(
        id: String
    ): GmailThread {
        return client.post("https://gmail.googleapis.com/gmail/v1/users/me/threads/$id/untrash").body()
    }
}
