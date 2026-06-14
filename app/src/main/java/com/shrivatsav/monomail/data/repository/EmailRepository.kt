package com.shrivatsav.monomail.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.shrivatsav.monomail.auth.AccountManager
import com.shrivatsav.monomail.auth.UserProfile
import com.shrivatsav.monomail.data.local.AppDatabase
import com.shrivatsav.monomail.data.local.toEntity
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.data.provider.EmailFolder
import com.shrivatsav.monomail.data.provider.EmailProvider
import com.shrivatsav.monomail.data.worker.SyncWorker
import com.shrivatsav.monomail.ui.screens.inbox.InboxTab
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

class EmailRepository(
    private val providerFactory: (UserProfile) -> EmailProvider,
    private val database: AppDatabase,
    private val context: Context,
    private val accountManager: AccountManager
) {
    private val threadDao = database.threadDao()
    private val emailDao = database.emailDao()
    
    private val workManager = WorkManager.getInstance(context)
    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

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

    private fun enqueueSync(data: Data) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setInputData(data)
            .build()
        workManager.enqueue(request)
    }

    // ── Local Database Flows ─────────────────────────────────────────

    fun getInboxThreadsFlow(tab: InboxTab): Flow<List<EmailThread>> = flow {
        val activeAccountId = accountManager.getActiveAccount()?.id ?: "gmail_unknown"
        val dbFlow = when (tab) {
            InboxTab.INBOX -> threadDao.getInboxThreads(activeAccountId)
            InboxTab.SENT -> threadDao.getSentThreads(activeAccountId)
            InboxTab.ARCHIVED -> threadDao.getArchivedThreads(activeAccountId)
            InboxTab.STARRED -> threadDao.getStarredThreads(activeAccountId)
            InboxTab.TRASH -> threadDao.getTrashThreads(activeAccountId)
            InboxTab.UNIFIED -> threadDao.getAllInboxThreads()
        }.map { list -> list.map { it.toDomainModel() } }
        emitAll(dbFlow)
    }

    fun getAllInboxThreadsFlow(): Flow<List<EmailThread>> {
        return threadDao.getAllInboxThreads().map { list -> list.map { it.toDomainModel() } }
    }

    suspend fun getEmailById(id: String): Email? {
        val activeAccountId = getActiveAccountId()
        return emailDao.getEmailById(id, activeAccountId)?.toDomainModel()
    }

    fun getThreadEmailsFlow(threadId: String): Flow<List<Email>> = flow {
        val activeAccountId = getActiveAccountId()
        emitAll(emailDao.getEmailsForThread(threadId, activeAccountId).map { list -> list.map { it.toDomainModel() } })
    }

    // ── Sync with Network ────────────────────────────────────────────

    suspend fun refreshInbox(tab: InboxTab, pageToken: String? = null, query: String? = null, accountId: String? = null): Result<String?> {
        return try {
            val provider = if (accountId != null) getProviderForAccount(accountId) else getActiveProvider()
            if (provider == null) return Result.failure(Exception("No active provider"))
            
            val targetAccountId = accountId ?: getActiveAccountId()
            
            val folder = when (tab) {
                InboxTab.INBOX -> EmailFolder.INBOX
                InboxTab.SENT -> EmailFolder.SENT
                InboxTab.ARCHIVED -> EmailFolder.ARCHIVE
                InboxTab.STARRED -> EmailFolder.STARRED
                InboxTab.TRASH -> EmailFolder.TRASH
                InboxTab.UNIFIED -> EmailFolder.INBOX // Fallback to Inbox
            }

            val listResponse = provider.listThreads(
                folder = folder,
                maxResults = 20,
                pageToken = pageToken,
                query = query
            )

            if (listResponse.threads.isNotEmpty()) {
                val entities = listResponse.threads.map { providerThread ->
                    val messages = providerThread.messages
                    val latest = messages.maxByOrNull { it.date }
                    
                    val allFolders = messages.flatMap { it.folders }.toSet()
                    
                    val participants = messages.map { it.from }.distinct()
                    val isRead = messages.all { it.isRead }
                    val isStarred = messages.any { it.isStarred }

                    val domainThread = EmailThread(
                        threadId = providerThread.threadId,
                        subject = latest?.subject ?: "(no subject)",
                        from = latest?.from ?: "",
                        fromEmail = latest?.fromEmail ?: "",
                        snippet = latest?.snippet ?: "",
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
                        inTrash = EmailFolder.TRASH in allFolders
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

    // ── Optimistic UI Actions ────────────────────────────────────────

    suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
        val newStarred = !currentStarred
        val activeAccountId = getActiveAccountId()
        threadDao.updateThreadStarred(threadId, activeAccountId, newStarred)
        emailDao.updateThreadStarred(threadId, activeAccountId, newStarred)

        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_TOGGLE_STAR)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .putBoolean(SyncWorker.KEY_IS_STARRED, newStarred)
            .build()
        enqueueSync(data)
    }

    suspend fun markEmailsAsRead(emailIds: List<String>) {
        if (emailIds.isEmpty()) return
        val activeAccountId = getActiveAccountId()
        emailDao.markEmailsAsRead(emailIds, activeAccountId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_EMAILS_READ)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_EMAIL_IDS, Gson().toJson(emailIds))
            .build()
        enqueueSync(data)
    }

    suspend fun markThreadAsRead(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.updateThreadReadStatus(threadId, activeAccountId, true)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_THREAD_READ)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }
    
    suspend fun markThreadAsUnread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.updateThreadReadStatus(threadId, activeAccountId, false)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_MARK_THREAD_UNREAD)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun archiveThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.archiveThread(threadId, activeAccountId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_ARCHIVE)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun unarchiveThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.unarchiveThread(threadId, activeAccountId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_UNARCHIVE)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun deleteThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.moveToTrash(threadId, activeAccountId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_DELETE)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun restoreThread(threadId: String) {
        val activeAccountId = getActiveAccountId()
        threadDao.restoreFromTrash(threadId, activeAccountId)
        
        val data = Data.Builder()
            .putString(SyncWorker.KEY_ACTION, SyncWorker.ACTION_RESTORE)
            .putString(SyncWorker.KEY_ACCOUNT_ID, activeAccountId)
            .putString(SyncWorker.KEY_THREAD_ID, threadId)
            .build()
        enqueueSync(data)
    }

    suspend fun clearLocalData() {
        database.clearAllTables()
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
        threadId: String? = null,
        inReplyToMessageId: String? = null,
        references: String? = null,
        attachments: List<EmailAttachment> = emptyList()
    ): Result<Unit> {
        return try {
            val provider = getActiveProvider() ?: return Result.failure(Exception("No active provider"))
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
