package com.shrivatsav.monomail.data.repository

import android.content.Context
import com.shrivatsav.monomail.data.local.AppDatabase
import com.shrivatsav.monomail.data.local.toEntity
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmail
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmailList
import com.shrivatsav.monomail.data.mapper.EmailMapper.toEmailThread
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.shrivatsav.monomail.data.worker.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

import com.shrivatsav.monomail.data.remote.GmailApi
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab

class EmailRepository(
    val api: GmailApi, 
    private val database: AppDatabase,
    private val context: Context
) {
    private val threadDao = database.threadDao()
    private val emailDao = database.emailDao()
    
    private val workManager = WorkManager.getInstance(context)
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    private fun enqueueSync(data: Data) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data)
            .build()
        workManager.enqueue(request)
    }

    // ── Local Database Flows ─────────────────────────────────────────

    fun getInboxThreadsFlow(tab: InboxTab): Flow<List<EmailThread>> {
        return when (tab) {
            InboxTab.INBOX -> threadDao.getInboxThreads()
            InboxTab.SENT -> threadDao.getSentThreads()
            InboxTab.ARCHIVED -> threadDao.getArchivedThreads()
            InboxTab.STARRED -> threadDao.getStarredThreads()
        }.map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun getEmailById(id: String): Email? {
        return emailDao.getEmailById(id)?.toDomainModel()
    }

    fun getThreadEmailsFlow(threadId: String): Flow<List<Email>> {
        return emailDao.getEmailsForThread(threadId).map { list -> list.map { it.toDomainModel() } }
    }

    // ── Sync with Network ────────────────────────────────────────────

    suspend fun refreshInbox(tab: InboxTab, pageToken: String? = null, query: String? = null): Result<String?> {
        return try {
            val labelIds = when {
                !query.isNullOrEmpty() -> null
                tab == InboxTab.INBOX -> "INBOX"
                tab == InboxTab.SENT -> "SENT"
                tab == InboxTab.STARRED -> "STARRED"
                tab == InboxTab.ARCHIVED -> null // Archived is typically -label:inbox but we handle it manually or query
                else -> "INBOX"
            }
            
            // For archived, we might need a specific query if labelIds doesn't work out of the box
            val finalQuery = if (tab == InboxTab.ARCHIVED && query.isNullOrEmpty()) "-label:inbox -label:trash -label:sent" else query

            val listResponse = api.listThreads(
                maxResults = 20,
                pageToken  = pageToken,
                labelIds   = labelIds,
                query      = finalQuery
            )

            val threadRefs = listResponse.threads.orEmpty()
            if (threadRefs.isNotEmpty()) {
                val rawThreads = coroutineScope {
                    threadRefs.map { ref ->
                        async { api.getThread(ref.id) }
                    }.awaitAll()
                }

                // Insert into DB
                val entities = rawThreads.map { rawThread ->
                    val domainThread = rawThread.toEmailThread()
                    val labels = rawThread.messages.orEmpty().flatMap { it.labelIds.orEmpty() }.toSet()
                    
                    domainThread.toEntity(
                        inInbox = "INBOX" in labels || (tab == InboxTab.INBOX),
                        inSent = "SENT" in labels || (tab == InboxTab.SENT),
                        inArchived = tab == InboxTab.ARCHIVED
                    )
                }
                threadDao.insertThreads(entities)
            }

            Result.success(listResponse.nextPageToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshThread(threadId: String): Result<Unit> {
        return try {
            val threadResponse = api.getThread(threadId)
            val emails = threadResponse.toEmailList()
            emailDao.insertEmails(emails.map { it.toEntity() })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Optimistic UI Actions ────────────────────────────────────────

    suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
        val newStarred = !currentStarred
        // Optimistic update
        threadDao.updateThreadStarred(threadId, newStarred)
        emailDao.updateThreadStarred(threadId, newStarred)

        // Enqueue background sync
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_TOGGLE_STAR)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .putBoolean(SyncWorker.KEY_IS_STARRED, newStarred)
            .build()
        enqueueSync(data)
    }

    suspend fun markEmailsAsRead(emailIds: List<String>) {
        if (emailIds.isEmpty()) return
        
        // Optimistic update
        emailDao.markEmailsAsRead(emailIds)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_EMAILS_READ)
            .putString(SyncWorker.KEY_EMAIL_IDS, Gson().toJson(emailIds))
            .build()
        enqueueSync(data)
    }

    suspend fun markThreadAsRead(threadId: String) {
        // Optimistic update
        threadDao.updateThreadReadStatus(threadId, true)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_THREAD_READ)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }
    
    suspend fun markThreadAsUnread(threadId: String) {
        // Optimistic update
        threadDao.updateThreadReadStatus(threadId, false)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_THREAD_UNREAD)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun archiveThread(threadId: String) {
        // Optimistic update
        threadDao.archiveThread(threadId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_ARCHIVE)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun unarchiveThread(threadId: String) {
        // Optimistic update
        threadDao.unarchiveThread(threadId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_UNARCHIVE)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun deleteThread(threadId: String) {
        // Optimistic update
        threadDao.deleteThread(threadId)
        emailDao.deleteThreadEmails(threadId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_DELETE)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val part = api.getAttachment(messageId, attachmentId)
                part.data?.let { data ->
                    val base64 = data.replace("-", "+").replace("_", "/")
                    android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
    }

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
                append("From: $from\r\n")
                append("To: $to\r\n")
                append("Subject: $subject\r\n")
                append("MIME-Version: 1.0\r\n")
                if (inReplyToMessageId != null) {
                    append("In-Reply-To: $inReplyToMessageId\r\n")
                }
                if (references != null) {
                    append("References: $references\r\n")
                }

                if (attachments.isEmpty()) {
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("\r\n")
                    append(body)
                } else {
                    val boundary = "==Multipart_Boundary_x${System.currentTimeMillis()}x"
                    append("Content-Type: multipart/mixed; boundary=\"$boundary\"\r\n")
                    append("\r\n")
                    
                    append("--$boundary\r\n")
                    append("Content-Type: text/html; charset=UTF-8\r\n")
                    append("\r\n")
                    append(body)
                    append("\r\n")
                    
                    for (attachment in attachments) {
                        append("--$boundary\r\n")
                        append("Content-Type: ${attachment.mimeType}; name=\"${attachment.name}\"\r\n")
                        append("Content-Disposition: attachment; filename=\"${attachment.name}\"\r\n")
                        append("Content-Transfer-Encoding: base64\r\n")
                        append("\r\n")
                        
                        val bytes = context.contentResolver.openInputStream(attachment.uri)?.use { it.readBytes() }
                        if (bytes != null) {
                            val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.DEFAULT).replace("\n", "\r\n")
                            append(base64)
                            if (!base64.endsWith("\n") && !base64.endsWith("\r\n")) {
                                append("\r\n")
                            }
                        }
                    }
                    append("--$boundary--\r\n")
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
            
            // Send & Archive
            if (threadId != null) {
                archiveThread(threadId)
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
