package com.shrivatsav.monomail.data.repository

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmail
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmailList
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmailThread
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.remote.GmailApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class InboxPage(
    val emails: List<Email>,
    val nextPageToken: String?
)

data class ThreadPage(
    val threads: List<EmailThread>,
    val nextPageToken: String?
)

class EmailRepository(private val api: GmailApi, private val context: Context) {

    private val prefs = context.getSharedPreferences("emails_cache", Context.MODE_PRIVATE)
    private val gson = Gson()

    // ── Thread-based inbox ───────────────────────────────────────────

    /** Get locally cached threads for instant loading. */
    fun getCachedInboxThreads(): Result<ThreadPage> {
        return try {
            val json = prefs.getString("cached_threads", null)
            if (json != null) {
                val type = object : TypeToken<List<EmailThread>>() {}.type
                val threads: List<EmailThread> = gson.fromJson(json, type)
                Result.success(ThreadPage(threads, null))
            } else {
                Result.success(ThreadPage(emptyList(), null))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch a page of inbox threads. Threads are fetched concurrently for speed. */
    suspend fun getInboxThreads(pageToken: String? = null, query: String? = null): Result<ThreadPage> {
        return try {
            val listResponse = api.listThreads(
                maxResults = 20,
                pageToken  = pageToken,
                labelIds   = if (query.isNullOrEmpty()) "INBOX" else null,
                query      = query
            )

            val threadRefs = listResponse.threads.orEmpty()
            if (threadRefs.isEmpty()) {
                return Result.success(ThreadPage(emptyList(), null))
            }

            // Fetch full threads concurrently
            val threads = coroutineScope {
                threadRefs.map { ref ->
                    async { api.getThread(ref.id) }
                }.awaitAll()
            }.map { it.toEmailThread() }
             .sortedByDescending { it.date }

            // Cache the first page of the main inbox
            if (pageToken == null && query.isNullOrEmpty()) {
                prefs.edit().putString("cached_threads", gson.toJson(threads)).apply()
            }

            Result.success(ThreadPage(threads, listResponse.nextPageToken))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch all messages in a thread for conversation view. */
    suspend fun getThread(threadId: String): Result<List<Email>> {
        return try {
            val thread = api.getThread(threadId)
            Result.success(thread.toEmailList())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Legacy message-based methods ─────────────────────────────────

    /** Get locally cached emails for instant loading. */
    fun getCachedInboxEmails(): Result<InboxPage> {
        return try {
            val json = prefs.getString("cached_inbox", null)
            if (json != null) {
                val type = object : TypeToken<List<Email>>() {}.type
                val emails: List<Email> = gson.fromJson(json, type)
                Result.success(InboxPage(emails, null))
            } else {
                Result.success(InboxPage(emptyList(), null))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch a page of inbox emails. Messages are fetched concurrently for speed. */
    suspend fun getInboxEmails(pageToken: String? = null, query: String? = null): Result<InboxPage> {
        return try {
            val listResponse = api.listMessages(
                maxResults = 20,
                pageToken  = pageToken,
                labelIds   = if (query.isNullOrEmpty()) "INBOX" else null,
                query      = query
            )

            val messageRefs = listResponse.messages.orEmpty()
            if (messageRefs.isEmpty()) {
                return Result.success(InboxPage(emptyList(), null))
            }

            val emails = coroutineScope {
                messageRefs.map { ref ->
                    async { api.getMessage(ref.id) }
                }.awaitAll()
            }.map { it.toEmail() }
             .sortedByDescending { it.date }

            if (pageToken == null && query.isNullOrEmpty()) {
                prefs.edit().putString("cached_inbox", gson.toJson(emails)).apply()
            }

            Result.success(InboxPage(emails, listResponse.nextPageToken))

        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Fetch a single email by ID. */
    suspend fun getEmail(id: String): Result<Email> {
        return try {
            val message = api.getMessage(id)
            Result.success(message.toEmail())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Send an email via Gmail API. */
    suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        threadId: String? = null,
        inReplyToMessageId: String? = null,
        references: String? = null,
        attachments: List<EmailAttachment> = emptyList()
    ): Result<Unit> {
        return try {
            val headers = buildString {
                appendLine("From: $from")
                appendLine("To: $to")
                appendLine("Subject: $subject")
                appendLine("MIME-Version: 1.0")
                if (inReplyToMessageId != null) {
                    appendLine("In-Reply-To: $inReplyToMessageId")
                }
                if (references != null) {
                    appendLine("References: $references")
                }

                if (attachments.isEmpty()) {
                    appendLine("Content-Type: text/html; charset=UTF-8")
                    appendLine()
                    append(body)
                } else {
                    val boundary = "==Multipart_Boundary_x${System.currentTimeMillis()}x"
                    appendLine("Content-Type: multipart/mixed; boundary=\"$boundary\"")
                    appendLine()
                    
                    // Body part
                    appendLine("--$boundary")
                    appendLine("Content-Type: text/html; charset=UTF-8")
                    appendLine()
                    appendLine(body)
                    
                    // Attachments
                    for (attachment in attachments) {
                        appendLine("--$boundary")
                        appendLine("Content-Type: ${attachment.mimeType}; name=\"${attachment.name}\"")
                        appendLine("Content-Disposition: attachment; filename=\"${attachment.name}\"")
                        appendLine("Content-Transfer-Encoding: base64")
                        appendLine()
                        
                        // Read and encode file
                        val bytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT).replace("\n", "\r\n")
                            appendLine(base64)
                        }
                    }
                    appendLine("--$boundary--")
                }
            }
            val raw = android.util.Base64.encodeToString(
                headers.toByteArray(),
                android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
            )
            api.sendMessage(
                com.shrivatsav.monomail.data.remote.SendMessageRequest(
                    raw = raw,
                    threadId = threadId
                )
            )
            
            // Send & Archive: remove INBOX label from thread
            if (threadId != null) {
                archiveThread(threadId)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Mark a batch of emails as read by removing the UNREAD label. */
    suspend fun markEmailsAsRead(emailIds: List<String>): Result<Unit> {
        if (emailIds.isEmpty()) return Result.success(Unit)
        return try {
            emailIds.chunked(1000).forEach { chunk ->
                val request = com.shrivatsav.monomail.data.remote.BatchModifyMessagesRequest(
                    ids = chunk,
                    removeLabelIds = listOf("UNREAD")
                )
                api.batchModifyMessages(request)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Archive a thread by removing the INBOX label. */
    suspend fun archiveThread(threadId: String): Result<Unit> {
        return try {
            api.modifyThread(
                id = threadId,
                request = com.shrivatsav.monomail.data.remote.ModifyThreadRequest(
                    removeLabelIds = listOf("INBOX")
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Unarchive a thread by adding the INBOX label. */
    suspend fun unarchiveThread(threadId: String): Result<Unit> {
        return try {
            api.modifyThread(
                id = threadId,
                request = com.shrivatsav.monomail.data.remote.ModifyThreadRequest(
                    addLabelIds = listOf("INBOX")
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Star a thread. */
    suspend fun starThread(threadId: String): Result<Unit> {
        return try {
            api.modifyThread(
                id = threadId,
                request = com.shrivatsav.monomail.data.remote.ModifyThreadRequest(
                    addLabelIds = listOf("STARRED")
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Unstar a thread. */
    suspend fun unstarThread(threadId: String): Result<Unit> {
        return try {
            api.modifyThread(
                id = threadId,
                request = com.shrivatsav.monomail.data.remote.ModifyThreadRequest(
                    removeLabelIds = listOf("STARRED")
                )
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
