package com.shrivatsav.monomail.shared.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.shrivatsav.monomail.shared.auth.AccountManager
import com.shrivatsav.monomail.shared.auth.UserProfile
import com.shrivatsav.monomail.shared.data.local.encodeAttachments
import com.shrivatsav.monomail.shared.data.local.encodeStringList
import com.shrivatsav.monomail.shared.data.local.toDomain
import com.shrivatsav.monomail.shared.data.model.Email
import com.shrivatsav.monomail.shared.data.model.EmailAttachment
import com.shrivatsav.monomail.shared.data.model.EmailThread
import com.shrivatsav.monomail.shared.data.provider.EmailFolder
import com.shrivatsav.monomail.shared.data.provider.EmailProvider
import com.shrivatsav.monomail.shared.data.provider.ProviderMessage
import com.shrivatsav.monomail.shared.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/**
 * Multiplatform email repository. Replaces the Android original:
 *  - Room DAOs            -> SQLDelight AppDatabaseQueries
 *  - WorkManager sync     -> best-effort inline provider calls after optimistic local writes
 *  - Gson                 -> kotlinx.serialization (via EntityMappers)
 *  - Context              -> removed
 */
class EmailRepository(
    private val providerFactory: (UserProfile) -> EmailProvider,
    private val database: AppDatabase,
    private val accountManager: AccountManager
) {
    private val q = database.appDatabaseQueries

    private fun cleanSubject(subject: String): String =
        subject.replaceFirst(Regex("^(Re|Fwd|Fw):\\s*", RegexOption.IGNORE_CASE), "")

    private fun bool(value: Boolean): Long = if (value) 1L else 0L

    fun getActiveProvider(): EmailProvider? =
        accountManager.getActiveAccount()?.let { providerFactory(it) }

    fun getActiveAccountId(): String =
        accountManager.getActiveAccount()?.id ?: "gmail_unknown"

    fun getProviderForAccount(accountId: String): EmailProvider? =
        accountManager.getAccounts().find { it.id == accountId }?.let { providerFactory(it) }

    fun getDatabase(): AppDatabase = database

    fun getInboxThreadsFlow(tab: InboxTab): Flow<List<EmailThread>> = flow {
        val accountId = getActiveAccountId()
        val query = when (tab) {
            InboxTab.INBOX -> q.getInboxThreads(accountId)
            InboxTab.SENT -> q.getSentThreads(accountId)
            InboxTab.ARCHIVED -> q.getArchivedThreads(accountId)
            InboxTab.STARRED -> q.getStarredThreads(accountId)
            InboxTab.TRASH -> q.getTrashThreads(accountId)
            InboxTab.UNIFIED -> q.getAllInboxThreads()
        }
        emitAll(query.asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } })
    }

    fun getAllInboxThreadsFlow(): Flow<List<EmailThread>> =
        q.getAllInboxThreads().asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } }

    fun getInboxEmailsFlow(tab: InboxTab): Flow<List<Email>> = flow {
        val accountId = getActiveAccountId()
        val query = when (tab) {
            InboxTab.INBOX -> q.getInboxEmails(accountId)
            InboxTab.SENT -> q.getSentEmails(accountId)
            InboxTab.ARCHIVED -> q.getArchivedEmails(accountId)
            InboxTab.STARRED -> q.getStarredEmails(accountId)
            InboxTab.TRASH -> q.getTrashEmails(accountId)
            InboxTab.UNIFIED -> q.getAllInboxEmails()
        }
        emitAll(query.asFlow().mapToList(Dispatchers.Default).map { list -> list.map { it.toDomain() } })
    }

    suspend fun getEmailById(id: String): Email? {
        val accountId = getActiveAccountId()
        return q.getEmailById(id, accountId).executeAsOneOrNull()?.toDomain()
    }

    fun getThreadEmailsFlow(threadId: String): Flow<List<Email>> = flow {
        val accountId = getActiveAccountId()
        emitAll(
            q.getEmailsForThread(threadId, accountId).asFlow().mapToList(Dispatchers.Default)
                .map { list -> list.map { it.toDomain() } }
        )
    }

    suspend fun getLatestInboxThread(accountId: String): EmailThread? =
        q.getLatestInboxThread(accountId).executeAsOneOrNull()?.toDomain()

    private fun insertMessage(accountId: String, msg: ProviderMessage) {
        q.insertEmail(
            id = msg.id,
            accountId = accountId,
            threadId = msg.threadId,
            subject = msg.subject,
            fromName = msg.from,
            fromEmail = msg.fromEmail,
            toEmail = msg.to,
            snippet = msg.snippet,
            body = msg.body,
            date = msg.date,
            isRead = bool(msg.isRead),
            isStarred = bool(msg.isStarred),
            labels = encodeStringList(msg.folders.map { it.name }),
            attachmentsJson = encodeAttachments(msg.attachments),
            inInbox = bool(EmailFolder.INBOX in msg.folders),
            inSent = bool(EmailFolder.SENT in msg.folders),
            inArchived = bool(EmailFolder.ARCHIVE in msg.folders),
            inTrash = bool(EmailFolder.TRASH in msg.folders)
        )
    }

    suspend fun refreshInbox(
        tab: InboxTab,
        pageToken: String? = null,
        query: String? = null,
        accountId: String? = null
    ): Result<String?> {
        return try {
            val provider = (if (accountId != null) getProviderForAccount(accountId) else getActiveProvider())
                ?: return Result.failure(IllegalStateException("No active provider"))
            val targetAccountId = accountId ?: getActiveAccountId()
            val folder = when (tab) {
                InboxTab.INBOX -> EmailFolder.INBOX
                InboxTab.SENT -> EmailFolder.SENT
                InboxTab.ARCHIVED -> EmailFolder.ARCHIVE
                InboxTab.STARRED -> EmailFolder.STARRED
                InboxTab.TRASH -> EmailFolder.TRASH
                InboxTab.UNIFIED -> EmailFolder.INBOX
            }
            val listResponse = provider.listThreads(
                folder = folder,
                maxResults = 20,
                pageToken = pageToken,
                query = query
            )
            if (listResponse.threads.isNotEmpty()) {
                q.transaction {
                    listResponse.threads.forEach { providerThread ->
                        val messages = providerThread.messages
                        val latest = messages.maxByOrNull { it.date }
                        val allFolders = messages.flatMap { it.folders }.toSet()
                        val participants = messages.map { it.from }.distinct()
                        q.insertThread(
                            threadId = providerThread.threadId,
                            accountId = targetAccountId,
                            subject = cleanSubject(latest?.subject ?: "(no subject)"),
                            fromName = latest?.from ?: "",
                            fromEmail = latest?.fromEmail ?: "",
                            snippet = latest?.snippet ?: "",
                            date = latest?.date ?: 0L,
                            messageCount = messages.size.toLong(),
                            isRead = bool(messages.all { it.isRead }),
                            isStarred = bool(messages.any { it.isStarred }),
                            latestMessageId = latest?.id ?: "",
                            participants = encodeStringList(participants),
                            inInbox = bool(EmailFolder.INBOX in allFolders),
                            inSent = bool(EmailFolder.SENT in allFolders),
                            inArchived = bool(EmailFolder.ARCHIVE in allFolders),
                            inTrash = bool(EmailFolder.TRASH in allFolders)
                        )
                        messages.forEach { insertMessage(targetAccountId, it) }
                    }
                }
            }
            Result.success(listResponse.nextPageToken)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshThread(threadId: String): Result<Unit> {
        return try {
            val provider = getActiveProvider() ?: return Result.failure(IllegalStateException("No active provider"))
            val accountId = getActiveAccountId()
            val threadResponse = provider.getThread(threadId)
            q.transaction {
                threadResponse.messages.forEach { insertMessage(accountId, it) }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
        val newStarred = !currentStarred
        val accountId = getActiveAccountId()
        q.updateThreadStarred(bool(newStarred), threadId, accountId)
        q.updateEmailThreadStarred(bool(newStarred), threadId, accountId)
        runCatching { getActiveProvider()?.toggleStar(threadId, newStarred) }
    }

    suspend fun markEmailsAsRead(emailIds: List<String>) {
        if (emailIds.isEmpty()) return
        val accountId = getActiveAccountId()
        q.transaction { emailIds.forEach { q.markEmailAsRead(it, accountId) } }
        runCatching { getActiveProvider()?.batchMarkRead(emailIds) }
    }

    suspend fun markThreadAsRead(threadId: String) {
        val accountId = getActiveAccountId()
        q.updateThreadReadStatus(1L, threadId, accountId)
        q.updateThreadEmailsReadStatus(1L, threadId, accountId)
        runCatching { getActiveProvider()?.markRead(threadId, true) }
    }

    suspend fun markThreadAsUnread(threadId: String) {
        val accountId = getActiveAccountId()
        q.updateThreadReadStatus(0L, threadId, accountId)
        q.updateThreadEmailsReadStatus(0L, threadId, accountId)
        runCatching { getActiveProvider()?.markRead(threadId, false) }
    }

    suspend fun archiveThread(threadId: String) {
        val accountId = getActiveAccountId()
        q.archiveThread(threadId, accountId)
        q.archiveThreadEmails(threadId, accountId)
        runCatching { getActiveProvider()?.archiveThread(threadId) }
    }

    suspend fun unarchiveThread(threadId: String) {
        val accountId = getActiveAccountId()
        q.unarchiveThread(threadId, accountId)
        q.unarchiveThreadEmails(threadId, accountId)
        runCatching { getActiveProvider()?.unarchiveThread(threadId) }
    }

    suspend fun deleteThread(threadId: String) {
        val accountId = getActiveAccountId()
        q.moveThreadToTrash(threadId, accountId)
        q.moveThreadEmailsToTrash(threadId, accountId)
        runCatching { getActiveProvider()?.trashThread(threadId) }
    }

    suspend fun restoreThread(threadId: String) {
        val accountId = getActiveAccountId()
        q.restoreThreadFromTrash(threadId, accountId)
        q.restoreThreadEmailsFromTrash(threadId, accountId)
        runCatching { getActiveProvider()?.restoreThread(threadId) }
    }

    suspend fun clearLocalData() {
        val accountId = getActiveAccountId()
        q.transaction {
            q.clearThreadsForAccount(accountId)
            q.clearEmailsForAccount(accountId)
        }
    }

    suspend fun getAttachmentBytes(messageId: String, attachmentId: String): ByteArray? =
        getActiveProvider()?.getAttachmentBytes(messageId, attachmentId)

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
            val provider = getActiveProvider() ?: return Result.failure(IllegalStateException("No active provider"))
            provider.sendEmail(
                from = from,
                to = to,
                subject = subject,
                body = body,
                threadId = threadId,
                attachments = attachments
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
