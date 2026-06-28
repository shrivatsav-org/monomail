package com.shrivatsav.monomail.data.repository
import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.room.withTransaction
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shrivatsav.monomail.worker.ScheduledSendWorker
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.local.*
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.provider.EmailFolder
import com.shrivatsav.monomail.data.provider.EmailProvider
import com.shrivatsav.monomail.data.remote.RetrofitClient
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class EmailRepository(
    private val providerFactory: (UserProfile) -> EmailProvider,
    private val database: AppDatabase,
    private val context: Context,
    private val accountManager: AccountManager,
    private val pendingActionDao: PendingActionDao
) {
    private fun cleanSubject(subject: String): String {
        return subject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")
    }
    private val threadDao = database.threadDao()
    private val emailDao = database.emailDao()
    private val scheduledMessageDao = database.scheduledMessageDao()
    private val gson = Gson()

    suspend fun getActiveProvider(): EmailProvider? {
        val activeAccount = accountManager.getActiveAccount() ?: return null
        return providerFactory(activeAccount)
    }
    fun getDatabase(): AppDatabase = database
    suspend fun getActiveAccountId(): String {
        return accountManager.getActiveAccount()?.id ?: "gmail_unknown"
    }
    suspend fun getLatestInboxThread(accountId: String): EmailThread? {
        return threadDao.getLatestInboxThread(accountId)?.toDomainModel()
    }
    suspend fun getProviderForAccount(accountId: String): EmailProvider? {
        val account = accountManager.getAccounts().find { it.id == accountId } ?: return null
        return providerFactory(account)
    }

    private suspend fun insertPendingAction(actionType: PendingActionType, accountId: String, threadId: String, payload: String = "", emailIdsJson: String = "") {
        val action = PendingActionEntity(
            id = UUID.randomUUID().toString(),
            accountId = accountId,
            actionType = actionType,
            threadId = threadId,
            payload = payload,
            emailIdsJson = emailIdsJson
        )
        pendingActionDao.insert(action)
    }

    fun getInboxThreadsFlow(tab: InboxTab, accountId: String): Flow<List<EmailThread>> {
        return when (tab) {
            InboxTab.UNIFIED -> threadDao.getAllInboxThreads()
            else -> threadDao.getInboxThreads(accountId)
        }.map { list -> list.map { it.toDomainModel() } }
    }
    fun getAllInboxThreadsFlow(): Flow<List<EmailThread>> {
        return threadDao.getAllInboxThreads().map { list -> list.map { it.toDomainModel() } }
    }
    fun getSentThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getSentThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getArchivedThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getArchivedThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getStarredThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getStarredThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getTrashThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getTrashThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getSnoozedThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getSnoozedThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getSpamThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getSpamThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getInboxEmailsFlow(tab: InboxTab, accountId: String): Flow<List<Email>> {
        return when (tab) {
            InboxTab.UNIFIED -> emailDao.getAllInboxEmails()
            else -> emailDao.getInboxEmails(accountId)
        }.map { list -> list.map { it.toDomainModel() } }
    }
    fun getSentEmailsFlow(accountId: String): Flow<List<Email>> =
        emailDao.getSentEmails(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getArchivedEmailsFlow(accountId: String): Flow<List<Email>> =
        emailDao.getArchivedEmails(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getStarredEmailsFlow(accountId: String): Flow<List<Email>> =
        emailDao.getStarredEmails(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getTrashEmailsFlow(accountId: String): Flow<List<Email>> =
        emailDao.getTrashEmails(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getSpamEmailsFlow(accountId: String): Flow<List<Email>> =
        emailDao.getSpamEmails(accountId).map { list -> list.map { it.toDomainModel() } }
    suspend fun getEmailById(id: String): Email? {
        val activeAccountId = getActiveAccountId()
        return emailDao.getEmailById(id, activeAccountId)?.toDomainModel()
    }
    fun getThreadEmailsFlow(threadId: String): Flow<List<Email>> = flow {
        val activeAccountId = getActiveAccountId()
        emitAll(emailDao.getEmailsForThread(threadId, activeAccountId).map { list -> list.map { it.toDomainModel() } })
    }

    suspend fun refreshInbox(tab: InboxTab, pageToken: String? = null, query: String? = null, accountId: String? = null): Result<String?> {
        return try {
            val provider = if (accountId != null) getProviderForAccount(accountId) else getActiveProvider()
            if (provider == null) return Result.failure(Exception("No active provider"))
            val targetAccountId = accountId ?: getActiveAccountId()
            if (tab == InboxTab.SNOOZED) return Result.success(null)
            val folder = when (tab) {
                InboxTab.INBOX -> EmailFolder.INBOX
                InboxTab.SENT -> EmailFolder.SENT
                InboxTab.ARCHIVED -> EmailFolder.ARCHIVE
                InboxTab.STARRED -> EmailFolder.STARRED
                InboxTab.TRASH -> EmailFolder.TRASH
                InboxTab.UNIFIED -> EmailFolder.INBOX
                InboxTab.SNOOZED -> EmailFolder.INBOX
                InboxTab.SPAM -> EmailFolder.SPAM
            }
            val listResponse = provider.listThreads(
                folder = folder,
                maxResults = 20,
                pageToken = pageToken,
                query = query
            )
            val existingSnippets = if (provider.providerName == "imap") {
                threadDao.getSnippetsForAccount(targetAccountId)
                    .associateBy { it.threadId }
            } else emptyMap()

            // ponytail: skip threads with pending actions so local changes aren't overwritten by server
            val pendingThreadIds = pendingActionDao.getPendingForAccount(targetAccountId)
                .map { it.threadId }.toSet()

            if (listResponse.threads.isNotEmpty()) {
                val existingThreadReadStatuses = threadDao.getReadStatuses(targetAccountId)
                    .associate { it.threadId to it.isRead }
                val existingEmailReadStatuses = emailDao.getEmailReadStatuses(targetAccountId)
                    .associate { it.id to it.isRead }
                val existingEmailIdSet = existingEmailReadStatuses.keys
                val entities = listResponse.threads.filter { it.threadId !in pendingThreadIds }.map { providerThread ->
                    val messages = providerThread.messages
                    val latest = messages.maxByOrNull { it.date }
                    val allFolders = messages.flatMap { it.folders }.toSet()
                    val participants = messages.map { it.from }.distinct()
                    val serverIsRead = messages.all { it.isRead }
                    val hasNewMessages = messages.any { it.id !in existingEmailIdSet }
                    val isRead = if (hasNewMessages) serverIsRead else (existingThreadReadStatuses[providerThread.threadId] == true || serverIsRead)
                    val isStarred = messages.any { it.isStarred }
                    val finalSnippet = (latest?.snippet ?: "").ifBlank {
                        existingSnippets[providerThread.threadId]?.snippet ?: ""
                    }
                    val domainThread = EmailThread(
                        threadId = providerThread.threadId,
                        subject = cleanSubject(latest?.subject ?: "(no subject)"),
                        from = latest?.from ?: "",
                        fromEmail = latest?.fromEmail ?: "",
                        snippet = finalSnippet,
                        date = latest?.date ?: 0L,
                        messageCount = messages.size,
                        isRead = isRead,
                        isStarred = isStarred,
                        latestMessageId = latest?.id ?: "",
                        participants = participants
                    )
                    domainThread.toEntity(
                        accountId = targetAccountId,
                        inInbox = EmailFolder.INBOX in allFolders,
                        inSent = EmailFolder.SENT in allFolders,
                        inArchived = EmailFolder.ARCHIVE in allFolders,
                        inTrash = EmailFolder.TRASH in allFolders,
                        inSpam = EmailFolder.SPAM in allFolders
                    )
                }
                val allEmails = listResponse.threads.filter { it.threadId !in pendingThreadIds }.flatMap { providerThread ->
                    providerThread.messages.map { msg ->
                        Email(
                            id = msg.id,
                            threadId = msg.threadId,
                            subject = msg.subject,
                            from = msg.from,
                            fromEmail = msg.fromEmail,
                            to = msg.to,
                            cc = msg.cc,
                            bcc = msg.bcc,
                            snippet = msg.snippet,
                            body = msg.body,
                            date = msg.date,
                            isRead = existingEmailReadStatuses[msg.id] == true || msg.isRead,
                            isStarred = msg.isStarred,
                            labels = msg.folders.map { it.name },
                            attachments = msg.attachments
                        ).toEntity(targetAccountId)
                    }
                }
                database.withTransaction {
                    threadDao.insertThreads(entities)
                    emailDao.insertEmails(allEmails)
                }
            }
            Result.success(listResponse.nextPageToken)
        } catch (e: RetrofitClient.AuthFailedException) {
            Log.w("EmailRepo", "Auth failed during refreshInbox for ${accountId ?: "active"}: ${e.message}")
            Result.failure(Exception("Session expired. Please sign in again."))
        } catch (e: Exception) {
            Log.e("EmailRepo", "refreshInbox failed", e)
            Result.failure(e)
        }
    }
    suspend fun refreshThread(threadId: String): Result<Unit> {
        return try {
            val provider = getActiveProvider() ?: return Result.failure(Exception("No active provider"))
            val activeAccountId = getActiveAccountId()
            val threadResponse = provider.getThread(threadId)
            val emails = threadResponse.messages.map { msg ->
                val labels = msg.folders.map { it.name }
                Email(
                    id = msg.id,
                    threadId = msg.threadId,
                    subject = msg.subject,
                    from = msg.from,
                    fromEmail = msg.fromEmail,
                    to = msg.to,
                    cc = msg.cc,
                    bcc = msg.bcc,
                    snippet = msg.snippet,
                    body = msg.body,
                    date = msg.date,
                    isRead = msg.isRead,
                    isStarred = msg.isStarred,
                    labels = labels,
                    attachments = msg.attachments
                )
            }
            emailDao.insertEmails(emails.map { it.toEntity(activeAccountId) })
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
        val newStarred = !currentStarred
        val activeAccountId = getActiveAccountId()
        insertPendingAction(PendingActionType.TOGGLE_STAR, activeAccountId, threadId, payload = newStarred.toString())
        threadDao.updateThreadStarred(threadId, activeAccountId, newStarred)
        emailDao.updateThreadStarred(threadId, activeAccountId, newStarred)
    }
    suspend fun markEmailsAsRead(emailIds: List<String>) {
        if (emailIds.isEmpty()) return
        val activeAccountId = getActiveAccountId()
        emailDao.markEmailsAsRead(emailIds, activeAccountId)
        insertPendingAction(PendingActionType.MARK_READ, activeAccountId, "", emailIdsJson = emailIds.joinToString(","))
    }
    suspend fun markThreadAsRead(threadId: String) {
        val activeAccountId = getActiveAccountId()
        insertPendingAction(PendingActionType.MARK_READ, activeAccountId, threadId)
        threadDao.updateThreadReadStatus(threadId, activeAccountId, true)
        emailDao.updateThreadEmailsReadStatus(threadId, activeAccountId, true)
    }
    suspend fun markThreadsAsRead(threadIds: List<String>): Result<Unit> {
        if (threadIds.isEmpty()) return Result.success(Unit)
        return try {
            val activeAccountId = getActiveAccountId()
            val unreadEmailIds = emailDao.getUnreadEmailIdsForThreads(threadIds, activeAccountId)
            threadDao.markThreadsAsRead(threadIds, activeAccountId)
            emailDao.markThreadEmailsAsRead(threadIds, activeAccountId)
            val provider = getActiveProvider() ?: return Result.failure(Exception("No active provider"))
            withContext(Dispatchers.IO) {
                if (unreadEmailIds.isNotEmpty()) {
                    provider.batchMarkRead(unreadEmailIds)
                } else {
                    threadIds.forEach { provider.markRead(it, true) }
                }
            }
            Result.success(Unit)
        } catch (e: RetrofitClient.AuthFailedException) {
            Log.w("EmailRepo", "Auth failed during markThreadsAsRead: ${e.message}")
            Result.failure(Exception("Session expired. Please sign in again."))
        } catch (e: Exception) {
            Log.e("EmailRepo", "markThreadsAsRead failed", e)
            Result.failure(e)
        }
    }
    suspend fun markThreadAsUnread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        insertPendingAction(PendingActionType.MARK_UNREAD, activeAccountId, threadId)
        threadDao.updateThreadReadStatus(threadId, activeAccountId, false)
        emailDao.updateThreadEmailsReadStatus(threadId, activeAccountId, false)
    }
    suspend fun archiveThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: getActiveAccountId()
        insertPendingAction(PendingActionType.ARCHIVE, activeAccountId, threadId)
        threadDao.archiveThread(threadId, activeAccountId)
        emailDao.archiveThreadEmails(threadId, activeAccountId)
    }
    suspend fun unarchiveThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: getActiveAccountId()
        insertPendingAction(PendingActionType.UNARCHIVE, activeAccountId, threadId)
        threadDao.unarchiveThread(threadId, activeAccountId)
        emailDao.unarchiveThreadEmails(threadId, activeAccountId)
    }
    suspend fun emptyTrash() {
        val activeAccountId = getActiveAccountId()
        val provider = getActiveProvider()
        if (provider != null) {
            val trashIds = threadDao.getTrashThreadIds(activeAccountId)
            trashIds.forEach { threadId ->
                try { provider.permanentlyDeleteThread(threadId) } catch (e: Exception) { Log.e("EmailRepo", "permanent delete failed for $threadId", e) }
            }
        }
        threadDao.emptyTrash(activeAccountId)
        emailDao.emptyTrash(activeAccountId)
    }
    suspend fun emptySpam() {
        val activeAccountId = getActiveAccountId()
        threadDao.emptySpam(activeAccountId)
        emailDao.emptySpam(activeAccountId)
    }
    suspend fun deleteThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        insertPendingAction(PendingActionType.DELETE, activeAccountId, threadId)
        threadDao.moveToTrash(threadId, activeAccountId)
        emailDao.moveThreadEmailsToTrash(threadId, activeAccountId)
    }
    suspend fun restoreThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        insertPendingAction(PendingActionType.RESTORE, activeAccountId, threadId)
        threadDao.restoreFromTrash(threadId, activeAccountId)
        emailDao.restoreThreadEmailsFromTrash(threadId, activeAccountId)
    }
    suspend fun reportNotSpam(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.reportNotSpam(threadId, activeAccountId)
        emailDao.reportThreadEmailsNotSpam(threadId, activeAccountId)
    }
    suspend fun clearLocalData() {
        withContext(Dispatchers.IO) { database.clearAllTables() }
    }
    suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? {
        val provider = getActiveProvider() ?: return null
        return provider.getAttachmentBytes(messageId, attachmentId)
    }
    suspend fun sendEmail(
        from: String,
        to: String,
        subject: String,
        body: String,
        cc: String = "",
        bcc: String = "",
        threadId: String? = null,
        inReplyToMessageId: String? = null,
        references: String? = null,
        attachments: List<EmailAttachment> = emptyList(),
        explicitAccountId: String? = null
    ): Result<String?> {
        return try {
            val targetAccountId = explicitAccountId ?: getActiveAccountId()
            val provider = (if (explicitAccountId != null) getProviderForAccount(explicitAccountId) else getActiveProvider()) ?: return Result.failure(Exception("No active provider"))
            val sentThreadId = provider.sendEmail(
                from = from,
                to = to,
                subject = subject,
                body = body,
                cc = cc,
                bcc = bcc,
                threadId = threadId,
                attachments = attachments
            )
            val actualThreadId = sentThreadId ?: threadId ?: UUID.randomUUID().toString()
            val msgId = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val domainThread = EmailThread(
                threadId = actualThreadId,
                subject = cleanSubject(subject),
                from = from,
                fromEmail = from,
                snippet = body.take(100),
                date = now,
                messageCount = 1,
                isRead = true,
                isStarred = false,
                latestMessageId = msgId,
                participants = listOf(from, to)
            )
            val domainEmail = Email(
                id = msgId,
                threadId = actualThreadId,
                subject = subject,
                from = from,
                fromEmail = from,
                to = to,
                cc = cc,
                bcc = bcc,
                snippet = body.take(100),
                body = body,
                date = now,
                isRead = true,
                isStarred = false,
                labels = listOf(EmailFolder.SENT.name),
                attachments = attachments.map { com.shrivatsav.monomail.data.model.EmailAttachmentInfo(id = it.name, messageId = msgId, name = it.name, mimeType = it.mimeType, size = it.size.toInt()) }
            )
            database.withTransaction {
                threadDao.insertThreads(listOf(domainThread.toEntity(targetAccountId, inInbox = false, inSent = true, inArchived = false, inTrash = false, inSpam = false)))
                emailDao.insertEmails(listOf(domainEmail.toEntity(targetAccountId)))
            }
            Result.success(actualThreadId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun scheduleSend(
        accountId: String,
        fromEmail: String,
        to: String,
        subject: String,
        body: String,
        scheduledAt: Long,
        cc: String = "",
        bcc: String = "",
        attachments: List<EmailAttachment> = emptyList()
    ) {
        val id = UUID.randomUUID().toString()
        val entity = ScheduledMessageEntity(
            id = id,
            accountId = accountId,
            fromEmail = fromEmail,
            to = to,
            cc = cc,
            bcc = bcc,
            subject = subject,
            body = body,
            attachmentsJson = gson.toJson(
                attachments.map { a ->
                    mapOf(
                        "localPath" to a.uri.toString(),
                        "name" to a.name,
                        "size" to a.size,
                        "mimeType" to a.mimeType
                    )
                }
            ),
            scheduledAt = scheduledAt
        )
        scheduledMessageDao.insertScheduledMessage(entity)
        val delay = scheduledAt - System.currentTimeMillis()
        val workRequest = OneTimeWorkRequestBuilder<ScheduledSendWorker>()
            .setInitialDelay(maxOf(delay, 0), TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putString(ScheduledSendWorker.KEY_SCHEDULED_MESSAGE_ID, id).build())
            .addTag("scheduled_send_$id")
            .build()
        WorkManager.getInstance(context).enqueue(workRequest)
    }
    suspend fun cancelScheduledMessage(id: String) {
        val msg = scheduledMessageDao.getScheduledMessageById(id)
        if (msg != null) {
            cleanupScheduledAttachmentFiles(msg.attachmentsJson)
            scheduledMessageDao.deleteScheduledMessage(id)
        }
        WorkManager.getInstance(context).cancelAllWorkByTag("scheduled_send_$id")
    }
    private fun cleanupScheduledAttachmentFiles(attachmentsJson: String) {
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val attachments: List<Map<String, Any>> = gson.fromJson(attachmentsJson, type)
            attachments.forEach { a ->
                val path = a["localPath"] as? String
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            }
        } catch (_: Exception) { }
    }
    suspend fun copyAttachmentsToCache(
        messageId: String,
        attachments: List<EmailAttachment>
    ): List<EmailAttachment> {
        if (attachments.isEmpty()) return emptyList()
        val dir = File(context.cacheDir, "scheduled_attachments/$messageId")
        dir.mkdirs()
        return attachments.mapNotNull { a ->
            try {
                val fileName = "${System.currentTimeMillis()}_${a.name}"
                val dest = File(dir, fileName)
                context.contentResolver.openInputStream(a.uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                a.copy(uri = Uri.fromFile(dest))
            } catch (e: Exception) {
                Log.e("EmailRepo", "Failed to cache attachment ${a.name}", e)
                null
            }
        }
    }
    fun getPendingScheduledMessagesFlow(accountId: String) = scheduledMessageDao.getPendingScheduledMessages(accountId)
    fun getPendingScheduledCountFlow(accountId: String) = scheduledMessageDao.getPendingCount(accountId)
    suspend fun getScheduledMessageById(id: String) = scheduledMessageDao.getScheduledMessageById(id)
    suspend fun snoozeThread(threadId: String, untilTimestamp: Long, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: getActiveAccountId()
        threadDao.snoozeThread(threadId, activeAccountId, untilTimestamp)
        emailDao.snoozeThreadEmails(threadId, activeAccountId, untilTimestamp)
    }
    suspend fun unsnoozeThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: getActiveAccountId()
        threadDao.unsnoozeThread(threadId, activeAccountId)
        emailDao.unsnoozeThreadEmails(threadId, activeAccountId)
    }
}
