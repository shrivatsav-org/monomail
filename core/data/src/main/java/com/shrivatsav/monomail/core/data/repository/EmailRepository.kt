package com.shrivatsav.monomail.core.data.repository
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import java.io.File
import androidx.room.withTransaction
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.shrivatsav.monomail.core.data.worker.ScheduledSendWorker
import com.shrivatsav.monomail.core.data.worker.BodyBackfillService
import java.util.UUID
import java.util.concurrent.TimeUnit
import com.google.gson.Gson
import com.shrivatsav.monomail.core.data.auth.AccountManager
import com.shrivatsav.monomail.core.data.auth.UserProfile
import com.shrivatsav.monomail.core.database.local.*
import com.shrivatsav.monomail.core.data.repository.SearchField
import com.shrivatsav.monomail.data.model.Email
import com.shrivatsav.monomail.data.model.EmailAttachment
import com.shrivatsav.monomail.data.model.EmailThread
import com.shrivatsav.monomail.core.network.provider.EmailFolder
import com.shrivatsav.monomail.core.network.provider.EmailProvider
import com.shrivatsav.monomail.core.network.provider.ProviderMessage
import com.shrivatsav.monomail.core.network.provider.ProviderThread
import com.shrivatsav.monomail.core.network.provider.ProviderThreadListResult
import com.shrivatsav.monomail.core.network.provider.ResourceNotFoundException
import com.shrivatsav.monomail.core.network.provider.SendAsAlias
import com.shrivatsav.monomail.core.network.provider.SendEmailOptions
import com.shrivatsav.monomail.core.network.provider.SendEmailResult
import com.shrivatsav.monomail.core.network.remote.RetrofitClient
import com.shrivatsav.monomail.model.InboxTab
import com.shrivatsav.monomail.util.cleanSubject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.Job

data class EmailContact(
    val name: String,
    val email: String
)

class EmailRepository(
    private val providerFactory: (UserProfile) -> EmailProvider,
    private val database: AppDatabase,
    private val context: Context,
    private val accountManager: AccountManager,
    private val pendingActionDao: PendingActionDao,
    private val pendingSendDao: PendingSendDao
) {
    private val threadDao = database.threadDao()
    private val emailDao = database.emailDao()
    private val scheduledMessageDao = database.scheduledMessageDao()
    private val gson = Gson()
    private val _syncProgress = MutableStateFlow<DeepSyncProgress?>(null)
    val syncProgress: StateFlow<DeepSyncProgress?> = _syncProgress.asStateFlow()
    /** Progress snapshot for the deep sync foreground notification:
     *  overall fraction [0..1] plus the folder (tab) currently being synced. */
    data class DeepSyncProgress(val fraction: Float, val folder: String)

    companion object {
        private const val NO_ACTIVE_PROVIDER = "No active provider"
    }
    private fun getBodyFetchLimitMs(): Long? {
        // Skip body/snippet/attachment extraction for messages older than 30 days.
        // Full bodies are fetched on-demand via getThread when the user opens a message.
        return System.currentTimeMillis() - (30L * 24L * 60L * 60L * 1000L)
    }


    suspend fun getActiveProvider(): EmailProvider? {
        val activeAccount = accountManager.getActiveAccount() ?: return null
        return providerFactory(activeAccount)
    }
    fun getDatabase(): AppDatabase = database
    suspend fun searchThreads(
        query: String,
        searchField: SearchField = SearchField.ALL,
        dateFrom: Long? = null,
        dateTo: Long? = null,
        hasAttachments: Boolean = false,
        accountId: String? = null
    ): List<EmailThread> {
        val activeAccountId = accountId ?: getActiveAccountId()
        val ftsQuery = buildFtsQuery(query, searchField)
        if (ftsQuery.isBlank()) return emptyList()
        val threadIds = emailDao.searchThreadIds(ftsQuery, dateFrom, dateTo, hasAttachments)
        if (threadIds.isEmpty()) return emptyList()
        return threadDao.getThreadsByIds(threadIds, activeAccountId).map { it.toDomainModel() }
    }

    private fun buildFtsQuery(query: String, field: SearchField): String {
        val sanitized = query.replace(Regex("[\"*()^~:]"), " ").trim()
        if (sanitized.isBlank()) return ""
        val tokens = sanitized.split(Regex("\\s+")).filter { it.isNotBlank() }
        val ftsPart = tokens.joinToString(" AND ") { "$it*" }
        return when (field) {
            SearchField.ALL -> ftsPart
            SearchField.SUBJECT -> "subject:($ftsPart)"
            SearchField.BODY -> "body:($ftsPart)"
            SearchField.FROM -> "fromEmail:($ftsPart) OR fromName:($ftsPart)"
            SearchField.TO -> "toEmail:($ftsPart)"
        }
    }
    suspend fun getActiveAccountId(): String {
        return accountManager.getActiveAccount()?.id ?: "gmail_unknown"
    }
    private suspend fun resolveAccountId(threadId: String): String {
        return threadDao.getAccountIdForThread(threadId) ?: getActiveAccountId()
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
    fun getAllSentThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllSentThreads().map { list -> list.map { it.toDomainModel() } }
    fun getAllArchivedThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllArchivedThreads().map { list -> list.map { it.toDomainModel() } }
    fun getDraftThreadsFlow(accountId: String): Flow<List<EmailThread>> =
        threadDao.getDraftThreads(accountId).map { list -> list.map { it.toDomainModel() } }
    fun getAllDraftThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllDraftThreads().map { list -> list.map { it.toDomainModel() } }
    fun getAllStarredThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllStarredThreads().map { list -> list.map { it.toDomainModel() } }
    fun getAllTrashThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllTrashThreads().map { list -> list.map { it.toDomainModel() } }
    fun getAllSnoozedThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllSnoozedThreads().map { list -> list.map { it.toDomainModel() } }
    fun getAllSpamThreadsFlow(): Flow<List<EmailThread>> =
        threadDao.getAllSpamThreads().map { list -> list.map { it.toDomainModel() } }
    fun getInboxEmailsFlow(tab: InboxTab, accountId: String): Flow<List<Email>> {
        return when (tab) {
            InboxTab.UNIFIED -> emailDao.getAllInboxEmails()
            else -> emailDao.getInboxEmails(accountId)
        }.map { list -> list.map { it.toDomainModel() } }
    }
    fun getAllInboxEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllInboxEmails().map { list -> list.map { it.toDomainModel() } }
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
    fun getAllSentEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllSentEmails().map { list -> list.map { it.toDomainModel() } }
    fun getAllArchivedEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllArchivedEmails().map { list -> list.map { it.toDomainModel() } }
    fun getAllStarredEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllStarredEmails().map { list -> list.map { it.toDomainModel() } }
    fun getAllTrashEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllTrashEmails().map { list -> list.map { it.toDomainModel() } }
    fun getAllSpamEmailsFlow(): Flow<List<Email>> =
        emailDao.getAllSpamEmails().map { list -> list.map { it.toDomainModel() } }
    suspend fun getEmailById(id: String, accountId: String? = null): Email? {
        val activeAccountId = accountId ?: getActiveAccountId()
        return emailDao.getEmailById(id, activeAccountId)?.toDomainModel()
    }
    suspend fun getEmailEntityById(id: String, accountId: String): com.shrivatsav.monomail.core.database.local.EmailEntity? {
        return emailDao.getEmailById(id, accountId)
    }
    fun getThreadEmailsFlow(threadId: String): Flow<List<Email>> = flow {
        val accountId = resolveAccountId(threadId)
        emitAll(emailDao.getEmailsForThread(threadId, accountId).map { list -> list.map { it.toDomainModel() } })
    }

    suspend fun suggestContacts(query: String): List<EmailContact> {
        return emailDao.searchContacts(query).map { EmailContact(it.name, it.email) }
    }

    private fun resolveFolder(tab: InboxTab): EmailFolder = when (tab) {
        InboxTab.INBOX -> EmailFolder.INBOX
        InboxTab.SENT -> EmailFolder.SENT
        InboxTab.ARCHIVED -> EmailFolder.ARCHIVE
        InboxTab.STARRED -> EmailFolder.STARRED
        InboxTab.TRASH -> EmailFolder.TRASH
        InboxTab.SPAM -> EmailFolder.SPAM
        InboxTab.UNIFIED -> EmailFolder.INBOX
        InboxTab.SNOOZED -> EmailFolder.INBOX
        InboxTab.DRAFTS -> EmailFolder.DRAFT
    }

    private fun buildThreadEntity(
        providerThread: com.shrivatsav.monomail.core.network.provider.ProviderThread,
        targetAccountId: String,
        existingSnippets: Map<String, ThreadSnippetProjection>,
        existingThreadReadStatuses: Map<String, Boolean>,
        existingEmailIdSet: Set<String>,
        existingThreadFlags: Map<String, ThreadFolderFlagsProjection>
    ): ThreadEntity {
        val messages = providerThread.messages
        val latest = messages.maxByOrNull { it.date }
        val first = messages.minByOrNull { it.date }
        val allFolders = messages.flatMap { it.folders }.toSet()
        val existing = existingThreadFlags[providerThread.threadId]
        // Gmail's All Mail contains INBOX + SENT messages too. Union with the
        // flags the thread already has so a folder pass never wipes membership
        // discovered by an earlier pass (INBOX → sent → All Mail order).
        val inInbox = EmailFolder.INBOX in allFolders || existing?.inInbox == true
        val inSent = EmailFolder.SENT in allFolders || existing?.inSent == true
        val inTrash = EmailFolder.TRASH in allFolders || existing?.inTrash == true
        val inSpam = EmailFolder.SPAM in allFolders || existing?.inSpam == true
        val inDrafts = EmailFolder.DRAFT in allFolders || existing?.inDrafts == true
        // Archived mirrors the email-level heuristic: in All Mail but not in any
        // primary folder. INBOX/SENT/TRASH/SPAM/DRAFT threads never count as archived.
        val inArchived = (EmailFolder.ARCHIVE in allFolders || existing?.inArchived == true) &&
            !inInbox && !inSent && !inTrash && !inSpam && !inDrafts
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
            subject = (first?.subject?.ifBlank { null } ?: "(no subject)").cleanSubject(),
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
        return domainThread.toEntity(
            accountId = targetAccountId,
            inInbox = inInbox,
            inSent = inSent,
            inArchived = inArchived,
            inTrash = inTrash,
            inSpam = inSpam,
            inDrafts = inDrafts
        )
    }

    private fun buildEmailEntities(
        listResponse: com.shrivatsav.monomail.core.network.provider.ProviderThreadListResult,
        targetAccountId: String,
        existingEmailReadStatuses: Map<String, Boolean>,
        existingAttachments: Map<String, String>,
        existingBodies: Map<String, EmailBodyProjection>,
        pendingThreadIds: Set<String>,
        existingLabels: Map<String, List<String>>
    ): List<EmailEntity> {
        return listResponse.threads.filter { it.threadId !in pendingThreadIds }.flatMap { providerThread ->
            providerThread.messages.map { msg ->
                // Gmail's All Mail contains INBOX + SENT messages too. Union with the
                // labels already stored so a folder pass never wipes membership
                // discovered by an earlier pass (INBOX → sent → All Mail order).
                val mergedLabels = (msg.folders.map { it.name } + existingLabels[msg.id].orEmpty()).distinct()
                var entity = Email(
                    id = msg.id, threadId = msg.threadId, subject = msg.subject,
                    from = msg.from, fromEmail = msg.fromEmail, to = msg.to, cc = msg.cc,
                    bcc = msg.bcc, snippet = msg.snippet, body = msg.body,
                    bodyIsHtml = msg.bodyIsHtml, date = msg.date,
                    isRead = existingEmailReadStatuses[msg.id] == true || msg.isRead,
                    isStarred = msg.isStarred, labels = mergedLabels,
                    attachments = msg.attachments
                ).toEntity(targetAccountId)
                val existingJson = existingAttachments[entity.id]
                if (entity.attachmentsJson in listOf("[]", "") && existingJson != null && existingJson != "[]" && existingJson.isNotEmpty()) {
                    entity = entity.copy(attachmentsJson = existingJson)
                }
                val existingBody = existingBodies[entity.id]
                if (entity.body.isEmpty() && existingBody != null && existingBody.body.isNotEmpty()) {
                    entity = entity.copy(body = existingBody.body, bodyIsHtml = existingBody.bodyIsHtml)
                }
                // Preserve existing non-empty snippet when provider returns empty
                if (entity.snippet.isEmpty() && existingBody != null && existingBody.snippet.isNotEmpty()) {
                    entity = entity.copy(snippet = existingBody.snippet)
                }
                entity
            }
        }
    }

    /** Serializes concurrent refreshes (ViewModel pull-to-refresh + periodic worker) so they queue instead of colliding on the shared IMAP connection. */
    private val refreshMutex = Mutex()
    /** Single-flight guard for body backfill sweeps (service + inline fallback). */
    private val backfillMutex = Mutex()
    /** Job of the sweep currently holding [backfillMutex]; lets callers join
     *  an in-flight sweep instead of racing it. */
    @Volatile private var activeBackfillJob: Job? = null
    /** Cooldown: ignore re-triggers for 5 min after a sweep finishes to prevent
     *  the 70→94→76 restart loop caused by new headers appearing between sweeps. */
    @Volatile private var lastBackfillFinished = 0L
    private val backfillCooldownMs = 5L * 60 * 1000

    /** Concurrent IMAP body downloads per sweep; matches the connection pool
     *  size so each downloader gets its own connection (Gmail allows 15). */
    private val IMAP_BACKFILL_CONCURRENCY = 5

    suspend fun refreshInbox(tab: InboxTab, pageToken: String? = null, query: String? = null, accountId: String? = null): Result<String?> = refreshMutex.withLock {
        return try {
            val resolvedProvider = if (accountId != null) getProviderForAccount(accountId) else getActiveProvider()
            if (resolvedProvider == null) return Result.failure(Exception(NO_ACTIVE_PROVIDER))
            val provider = resolvedProvider
            val resolvedAccountId = accountId ?: getActiveAccountId()
            if (tab == InboxTab.SNOOZED) return Result.success(null)
            val folder = resolveFolder(tab)
            // Per-tab incremental cutoff: each folder only re-fetches emails newer
            // than its own newest row. The old global cutoff made Sent/Archive/Spam
            // tabs return empty — those emails are older than the newest INBOX mail.
            // Null when the tab is empty → full folder fetch on first open.
            val sinceDate = when (tab) {
                InboxTab.SENT -> emailDao.getLatestSentEmailDate(resolvedAccountId)
                InboxTab.ARCHIVED -> emailDao.getLatestArchivedEmailDate(resolvedAccountId)
                InboxTab.TRASH -> emailDao.getLatestTrashEmailDate(resolvedAccountId)
                InboxTab.SPAM -> emailDao.getLatestSpamEmailDate(resolvedAccountId)
                InboxTab.DRAFTS -> emailDao.getLatestDraftEmailDate(resolvedAccountId)
                else -> emailDao.getLatestInboxEmailDate(resolvedAccountId)
            }
            val listResponse = provider.listThreads(
                folder = folder,
                bodyFetchLimitMs = getBodyFetchLimitMs(),
                pageToken = pageToken,
                query = query,
                maxResults = 20,
                sinceDate = sinceDate,
                onProgress = null
            )
            if (listResponse.threads.isEmpty()) return Result.success(listResponse.nextPageToken)
            val existingSnippets = if (provider.providerName == "imap") {
                threadDao.getSnippetsForAccount(resolvedAccountId).associateBy { it.threadId }
            } else emptyMap()
            val pendingThreadIds = pendingActionDao.getPendingForAccount(resolvedAccountId).map { it.threadId }.toSet()
            val existingThreadReadStatuses = threadDao.getReadStatuses(resolvedAccountId).associate { it.threadId to it.isRead }
            val existingEmailReadStatuses = emailDao.getEmailReadStatuses(resolvedAccountId).associate { it.id to it.isRead }

            val existingThreadFlags = threadDao.getThreadFolderFlags(resolvedAccountId).associateBy { it.threadId }
            val existingLabels = emailDao.getLabelsForAccount(resolvedAccountId).associate { it.id to it.labels }

            val entities = listResponse.threads.filter { it.threadId !in pendingThreadIds }.map { pt ->
                buildThreadEntity(pt, resolvedAccountId, existingSnippets, existingThreadReadStatuses, existingEmailReadStatuses.keys, existingThreadFlags)
            }
            val allEmails = buildEmailEntities(listResponse, resolvedAccountId, existingEmailReadStatuses,
                emailDao.getAttachmentJsonForAccount(resolvedAccountId).associate { it.id to it.attachmentsJson },
                emailDao.getEmailBodyForAccount(resolvedAccountId).associate { it.id to it },
                pendingThreadIds,
                existingLabels
            )
            val existingSnoozed = threadDao.getSnoozeStateForThreads(entities.map { it.threadId }, resolvedAccountId)
                .filter { it.isSnoozed }.associateBy { it.threadId }

            database.withTransaction {
                threadDao.insertThreads(entities)
                existingSnoozed.forEach { (threadId, state) -> threadDao.snoozeThread(threadId, resolvedAccountId, state.snoozedUntil) }
                emailDao.insertEmails(allEmails)
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
    /**
     * Full deep sync for initial account setup. Iterates all pages since [days] ago
     * and publishes monotonic progress via [syncProgress].
     */
    suspend fun startBackgroundDeepSync(days: Int, accountId: String) {
        val provider = getProviderForAccount(accountId) ?: return
        val sinceDate = System.currentTimeMillis() - (days.toLong() * 24L * 60L * 60L * 1000L)
        _syncProgress.value = DeepSyncProgress(0f, EmailFolder.INBOX.displayName)
        val bodyFetchLimit = getBodyFetchLimitMs()
        try {
            // Sync every folder the provider exposes, INBOX first (most relevant)
            // then Sent/Archive/Spam/Trash/Drafts. Gmail's All Mail contains INBOX
            // + SENT messages too, so folder passes must UNION membership (done in
            // buildThreadEntity/buildEmailEntities) instead of replacing it.
            deepSyncFolderPages(provider, EmailFolder.INBOX, sinceDate, accountId, bodyFetchLimit)
            deepSyncFolderPages(provider, EmailFolder.SENT, sinceDate, accountId, bodyFetchLimit)
            deepSyncFolderPages(provider, EmailFolder.ARCHIVE, sinceDate, accountId, bodyFetchLimit)
            deepSyncFolderPages(provider, EmailFolder.SPAM, sinceDate, accountId, bodyFetchLimit)
            deepSyncFolderPages(provider, EmailFolder.TRASH, sinceDate, accountId, bodyFetchLimit)
            deepSyncFolderPages(provider, EmailFolder.DRAFT, sinceDate, accountId, bodyFetchLimit)
            _syncProgress.value = DeepSyncProgress(1f, EmailFolder.DRAFT.displayName)
        } catch (e: kotlinx.coroutines.CancellationException) {
            // User cancelled (notification action) — propagate without setting
            // the fake 100% state; the finally still clears syncProgress.
            throw e
        } catch (e: Exception) {
            android.util.Log.w("EmailRepo", "Background deep sync failed: ${e.message}")
            _syncProgress.value = DeepSyncProgress(1f, EmailFolder.DRAFT.displayName)
        } finally {
            // delay() throws in a cancelled coroutine — swallow it so the
            // progress cleanup below always runs (otherwise the UI would be
            // stuck on "X is syncing" after a cancel).
            try {
                kotlinx.coroutines.delay(1000)
            } catch (_: kotlinx.coroutines.CancellationException) {}
            _syncProgress.value = null
        }
    }

    /**
     * Pages through one folder during deep sync, inserting header-only threads
     * and emails, publishing monotonic progress via [syncProgress].
     */
    private suspend fun deepSyncFolderPages(
        provider: com.shrivatsav.monomail.core.network.provider.EmailProvider,
        folder: EmailFolder,
        sinceDate: Long,
        accountId: String,
        bodyFetchLimit: Long?
    ) {
        var pageToken: String? = null
        var pageIndex = 0
        do {
            // Emit before the SEARCH so the notification switches to the new
            // folder immediately — the search phase produces no message-level
            // progress and previously showed the previous folder's stale text.
            _syncProgress.value = DeepSyncProgress(
                maxOf(_syncProgress.value?.fraction ?: 0f, pageIndex.toFloat() * 0.10f),
                folder.displayName
            )
            val listResponse = provider.listThreads(
                folder = folder,
                maxResults = 50,
                pageToken = pageToken,
                bodyFetchLimitMs = bodyFetchLimit,
                sinceDate = sinceDate,
                onProgress = { pageProgress ->
                    val p = pageIndex.toFloat()
                    val interpolated = p * 0.10f + pageProgress * 0.10f
                    _syncProgress.value = DeepSyncProgress(
                        maxOf(_syncProgress.value?.fraction ?: 0f, interpolated.coerceIn(p * 0.10f, (p + 1f) * 0.10f)),
                        folder.displayName
                    )
                }
            )
            if (listResponse.threads.isEmpty()) break
            val existingSnippets = if (provider.providerName == "imap") {
                threadDao.getSnippetsForAccount(accountId).associateBy { it.threadId }
            } else emptyMap()
            val pendingThreadIds = pendingActionDao.getPendingForAccount(accountId).map { it.threadId }.toSet()
            val existingThreadReadStatuses = threadDao.getReadStatuses(accountId).associate { it.threadId to it.isRead }
            val existingEmailReadStatuses = emailDao.getEmailReadStatuses(accountId).associate { it.id to it.isRead }
            val existingThreadFlags = threadDao.getThreadFolderFlags(accountId).associateBy { it.threadId }
            val existingLabels = emailDao.getLabelsForAccount(accountId).associate { it.id to it.labels }
            val entities = listResponse.threads.filter { it.threadId !in pendingThreadIds }.map { pt ->
                buildThreadEntity(pt, accountId, existingSnippets, existingThreadReadStatuses, existingEmailReadStatuses.keys, existingThreadFlags)
            }
            val allEmails = buildEmailEntities(listResponse, accountId, existingEmailReadStatuses,
                emailDao.getAttachmentJsonForAccount(accountId).associate { it.id to it.attachmentsJson },
                emailDao.getEmailBodyForAccount(accountId).associate { it.id to it },
                pendingThreadIds,
                existingLabels
            )
            database.withTransaction {
                threadDao.insertThreads(entities)
                emailDao.insertEmails(allEmails)
            }
            pageToken = listResponse.nextPageToken
            pageIndex++
            val timeBased = pageIndex.toFloat() * 0.10f
            _syncProgress.value = DeepSyncProgress(
                maxOf(_syncProgress.value?.fraction ?: 0f, timeBased),
                folder.displayName
            )
        } while (pageToken != null)
    }

    /**
     * Downloads body content for emails that were fetched during the initial
     * header/subject sync but have no body yet. Processes threads from newest
     * to oldest and publishes progress via [bodyBackfillProgress].
     *
     * Designed to run after [startBackgroundDeepSync] finishes, so the inbox
     * is already populated with subjects and can be used immediately.
     */
    private val _bodyBackfillProgress = MutableStateFlow<BodyBackfillState?>(null)
    val bodyBackfillProgress: StateFlow<BodyBackfillState?> = _bodyBackfillProgress.asStateFlow()
    private val _bodyBackfillError = MutableStateFlow<String?>(null)
    val bodyBackfillError: StateFlow<String?> = _bodyBackfillError.asStateFlow()

    suspend fun triggerBodyBackfill(accountId: String) {
        // Cooldown: skip if a sweep finished recently — prevents the restart loop
        // where new headers (Sent folder, thread members) inflate the count between sweeps.
        val elapsed = System.currentTimeMillis() - lastBackfillFinished
        if (elapsed < backfillCooldownMs && lastBackfillFinished > 0) {
            android.util.Log.d("EmailRepo", "Body backfill: cooldown active (${elapsed / 1000}s / ${backfillCooldownMs / 1000}s), skipping")
            return
        }
        val account = accountManager.getAccounts().find { it.id == accountId }
        if (account == null) {
            android.util.Log.w("EmailRepo", "Body backfill: no account found for $accountId")
            return
        }
        if (account.provider != "imap") {
            android.util.Log.d("EmailRepo", "Body backfill skipped: account provider is ${account.provider}, only IMAP downloads bodies")
            return
        }
        try {
            com.shrivatsav.monomail.core.data.worker.BodyBackfillService.start(context, accountId)
        } catch (e: Exception) {
            // Android 12+ can deny FGS starts from a background context (e.g. the
            // periodic worker); run the sweep inline instead so it still happens.
            android.util.Log.w("EmailRepo", "Body backfill FGS start denied, running inline", e)
            startBodyBackfill(accountId)
        }
    }

    suspend fun startBodyBackfill(accountId: String, maxEmails: Int = 500) {
        // Single-flight: concurrent sweeps each compute their own "missing"
        // total and fight over the same notification, making the banner flip
        // between ranges (e.g. 70 vs 447).
        if (!backfillMutex.tryLock()) {
            // A sweep is already running elsewhere (periodic worker, inline
            // fallback, or a previous trigger). Join it instead of giving up
            // so callers can rely on "returned" meaning "the backfill is
            // done" — DeepSyncService must not announce completion while
            // email content is still downloading.
            android.util.Log.d("EmailRepo", "Body backfill: sweep already running, joining it")
            activeBackfillJob?.join()
            return
        }
        try {
            activeBackfillJob = kotlin.coroutines.coroutineContext[Job]
            runBodyBackfillSweep(accountId, maxEmails)
        } finally {
            activeBackfillJob = null
            backfillMutex.unlock()
        }
    }

    private suspend fun runBodyBackfillSweep(accountId: String, maxEmails: Int) {
        val notifier = BodyBackfillNotificationHelper(context)
        var sweepCancelled = false
        var total = 0
        try {
            val provider = getProviderForAccount(accountId)
            if (provider == null) {
                android.util.Log.w("EmailRepo", "Body backfill: no provider for account $accountId")
                _bodyBackfillError.value = "Could not connect to account. Please check your account settings."
                return
            }
            if (provider.providerName != "imap") {
                android.util.Log.d("EmailRepo", "Body backfill skipped: provider is ${provider.providerName}, only IMAP downloads bodies")
                return
            }
            val missing = emailDao.getEmailsMissingBody(accountId, maxEmails)
            if (missing.isEmpty()) {
                android.util.Log.d("EmailRepo", "Body backfill: no missing bodies for $accountId")
                return
            }

            // Group by threadId, only fetch each thread once
            val seenThreads = mutableSetOf<String>()
            val threadGrouped = missing.filter { seenThreads.add(it.threadId) }

            total = missing.size
            var completed = 0
            _bodyBackfillError.value = null
            _bodyBackfillProgress.value = BodyBackfillState(total, 0, accountId)
            notifier.showProgress(_bodyBackfillProgress.value!!)

            // Process threads folder by folder (Inbox -> Sent -> Archived ->
            // Spam -> Trash -> Drafts, matching deep-sync order), newest first
            // within each folder. Starred messages live in Inbox, so they sort
            // under it; threads with no known folder go last.
            val sortedThreadIds = threadGrouped.sortedWith(
                compareBy<EmailBodySlimProjection>({ folderRank(it.labels) })
                    .thenByDescending { it.date }
            )
            // Download several threads at once — the IMAP connection pool
            // serves up to 5 concurrent connections (Gmail allows 15 per
            // account), turning serial round trips into a ~5x faster sweep.
            // Threads still dispatch in folder order (Inbox -> Sent -> ...),
            // so folders finish mostly in sequence.
            val slots = Semaphore(IMAP_BACKFILL_CONCURRENCY)
            val progressMutex = Mutex()
            coroutineScope {
                for (threadMeta in sortedThreadIds) {
                    launch {
                        slots.withPermit {
                            val threadId = threadMeta.threadId
                            val threadEmails = missing.filter { it.threadId == threadId }
                            // Search only the folders this thread's emails live in (labels are
                            // EmailFolder enum names) instead of the default 3 — ~3x fewer
                            // IMAP searches on folder-specific sweeps (e.g. Sent).
                            val folderHints = threadEmails.flatMap { it.labels }.distinct()
                            try {
                                val threadResponse = provider.getThread(threadId, folderHints)
                                val emailById = threadResponse.messages.associateBy { it.id }

                                // Update each missing email in this thread
                                for (emailMeta in threadEmails) {
                                    val providerMsg = emailById[emailMeta.id]
                                    val body = providerMsg?.body?.takeIf { it.isNotBlank() } ?: emailMeta.body ?: ""
                                    val bodyIsHtml = providerMsg?.bodyIsHtml ?: emailMeta.bodyIsHtml
                                    val snippet = providerMsg?.snippet?.takeIf { it.isNotBlank() } ?: emailMeta.snippet

                                    if (providerMsg?.body != null) {
                                        emailDao.updateEmailBody(emailMeta.id, accountId, body, bodyIsHtml, snippet)
                                    }
                                }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                // User cancelled the sweep (banner modal) — stop
                                // immediately without recording a download error.
                                throw e
                            }
                            catch (e: java.net.UnknownHostException) {
                                Log.w("EmailRepo", "Body backfill network error for thread $threadId: ${e.message}")
                                if (_bodyBackfillError.value == null) {
                                    _bodyBackfillError.value = "No internet connection. Email content will download when you're back online."
                                }
                            } catch (e: java.net.ConnectException) {
                                Log.w("EmailRepo", "Body backfill connection error for thread $threadId: ${e.message}")
                                if (_bodyBackfillError.value == null) {
                                    _bodyBackfillError.value = "Could not connect to server. Check your internet connection."
                                }
                            } catch (e: jakarta.mail.MessagingException) {
                                Log.w("EmailRepo", "Body backfill mail error for thread $threadId: ${e.message}")
                                if (_bodyBackfillError.value == null) {
                                    _bodyBackfillError.value = "Mail server error. Email content download will retry later."
                                }
                            } catch (e: Exception) {
                                Log.w("EmailRepo", "Body backfill failed for thread $threadId: ${e.message}")
                                // Continue with next thread
                            }
                            // Progress advances per thread regardless of outcome, so a slow or
                            // failing thread never freezes the banner at 0%.
                            // Labels are EmailFolder enum names; pick the rank-first one so
                            // the notification shows the folder this thread is downloaded
                            // under (a thread can carry several, e.g. INBOX + ARCHIVE after
                            // the All Mail union).
                            val currentFolder = threadEmails
                                .flatMap { it.labels }
                                .minByOrNull { folderRank(listOf(it)) }
                                ?.let { label -> EmailFolder.entries.firstOrNull { it.name == label }?.displayName }
                            // Skip the progress post once the sweep has been
                            // cancelled: getThread() is a blocking IMAP read that
                            // cooperative cancellation cannot interrupt, so a
                            // thread returning right after cancel must not
                            // resurrect the banner/notification.
                            if (kotlin.coroutines.coroutineContext[Job]?.isActive != false) {
                                progressMutex.withLock {
                                    completed += threadEmails.size
                                    _bodyBackfillProgress.value = BodyBackfillState(total, completed, accountId, currentFolder)
                                    notifier.showProgress(_bodyBackfillProgress.value!!)
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            // User cancelled the sweep — no error banner for an intentional stop.
            sweepCancelled = true
            throw e
        }
        catch (e: Exception) {
            Log.w("EmailRepo", "Body backfill initialization failed: ${e.message}")
            if (_bodyBackfillError.value == null) {
                _bodyBackfillError.value = "Could not download email content: ${e.message?.take(80)}"
            }
        } finally {
            try { notifier.dismiss() } catch (_: Exception) {}
            _bodyBackfillProgress.value = null
            lastBackfillFinished = System.currentTimeMillis()
            // Completion toast: only for a sweep that actually finished (not
            // cancelled) without errors and had work to do. Auto-dismisses.
            if (!sweepCancelled && _bodyBackfillError.value == null && total > 0) {
                try { notifier.showDone() } catch (_: Exception) {}
            }
        }
    }

    /** User-initiated stop from the banner modal: cancels the running sweep
     *  and removes its UI and notification immediately. The sweep's finally
     *  still clears progress and releases the single-flight mutex once the
     *  in-flight blocking IMAP reads drain. */
    fun cancelBodyBackfill() {
        activeBackfillJob?.cancel()
        // Optimistic: clear the banner and notification right away. The
        // cancelled sweep may still be draining in-flight blocking IMAP reads
        // (socket I/O can't be interrupted by coroutine cancellation) — that
        // drain continues invisibly and the guard in the sweep stops it from
        // re-posting progress.
        _bodyBackfillProgress.value = null
        try {
            BodyBackfillNotificationHelper(context).dismiss()
        } catch (_: Exception) {}
        // NotificationManager.cancel() is ignored for foreground-service
        // notifications (BodyBackfillService.startForeground posts 0xBB as
        // FGS + promoted ongoing on Android 16+). The only way to remove it
        // instantly is to drop the service's foreground state — stopping the
        // service does that (its notification is removed on destroy). The
        // already-cancelled sweep's finally then runs stopForeground/stopSelf
        // as a no-op when the drain finishes. No-op when the sweep runs in a
        // plain coroutine (worker/refresh path).
        try {
            context.stopService(Intent(context, BodyBackfillService::class.java))
        } catch (_: Exception) {}
    }

    /**
     * User-initiated resume from the sync-status modal. Mirrors
     * [triggerBodyBackfill] but deliberately skips the 5-minute cooldown —
     * an explicit "continue" always starts a fresh sweep.
     */
    suspend fun continueBodyBackfill(accountId: String) {
        val account = accountManager.getAccounts().find { it.id == accountId }
        if (account == null) {
            android.util.Log.w("EmailRepo", "Body backfill: no account found for $accountId")
            return
        }
        if (account.provider != "imap") {
            android.util.Log.d("EmailRepo", "Body backfill skipped: account provider is ${account.provider}, only IMAP downloads bodies")
            return
        }
        try {
            com.shrivatsav.monomail.core.data.worker.BodyBackfillService.start(context, accountId)
        } catch (e: Exception) {
            android.util.Log.w("EmailRepo", "Body backfill FGS start denied, running inline", e)
            startBodyBackfill(accountId)
        }
    }

    /** Live count of emails whose body still needs downloading for [accountId]. */
    fun observeMissingBodyCount(accountId: String): Flow<Int> = emailDao.observeMissingBodyCount(accountId)

    /** Live count of all emails for [accountId] (downloaded bodies = total - missing). */
    fun observeEmailCount(accountId: String): Flow<Int> = emailDao.observeEmailCount(accountId)

    /**
     * Backfill order for a thread's labels: Inbox (incl. Starred) -> Sent ->
     * Archived -> Spam -> Trash -> Drafts, matching the deep-sync pass order.
     * Unknown labels rank last. A thread carrying several labels takes the
     * lowest rank so it downloads with the newest folder it belongs to.
     */
    private fun folderRank(labels: List<String>): Int = labels.minOfOrNull { label ->
        when (EmailFolder.entries.firstOrNull { it.name == label }) {
            EmailFolder.INBOX, EmailFolder.STARRED -> 0
            EmailFolder.SENT -> 1
            EmailFolder.ARCHIVE -> 2
            EmailFolder.SPAM -> 3
            EmailFolder.TRASH -> 4
            EmailFolder.DRAFT -> 5
            null -> Int.MAX_VALUE
        }
    } ?: Int.MAX_VALUE
    suspend fun refreshThread(threadId: String): Result<Unit> {
        val accountId = resolveAccountId(threadId)
        return try {
            val provider = getProviderForAccount(accountId)
                ?: return Result.failure(Exception(NO_ACTIVE_PROVIDER))

            val threadResponse = provider.getThread(threadId)
            // Fetch existing folder labels to preserve them (getThread may find messages
            // in Gmail All Mail / Archive folder even when they belong to INBOX)
            val existingLabels = emailDao.getEmailsByThreadId(threadId, accountId)
                .associateBy { it.id }

            val emails = threadResponse.messages.map { msg ->
                val existing = existingLabels[msg.id]
                val newLabels = msg.folders.map { it.name }.toSet()
                // Merge: keep existing INBOX/SENT labels, only add new folder info from provider
                val mergedLabels = mutableSetOf<String>().apply {
                    addAll(newLabels)
                    if (existing != null) {
                        if (existing.inInbox) add("INBOX")
                        if (existing.inSent) add("SENT")
                        if (existing.inArchived) add("ARCHIVE")
                    }
                }
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
                    bodyIsHtml = msg.bodyIsHtml,
                    date = msg.date,
                    isRead = msg.isRead,
                    isStarred = msg.isStarred,
                    labels = mergedLabels.toList(),
                    attachments = msg.attachments
                )
            }
            database.withTransaction {
                if (emails.isNotEmpty()) {
                    val serverEmailIds = emails.map { it.id }
                    emailDao.deleteOrphanedEmails(threadId, accountId, serverEmailIds)
                    emailDao.insertEmails(emails.map { it.toEntity(accountId) })
                    val deduplicated = emails.fold(mutableListOf<com.shrivatsav.monomail.data.model.Email>()) { acc, email ->
                        val isDuplicate = acc.any { existing ->
                            existing.fromEmail == email.fromEmail &&
                            existing.snippet == email.snippet &&
                            existing.body == email.body &&
                            Math.abs(existing.date - email.date) < 60000
                        }
                        if (!isDuplicate) acc.add(email)
                        acc
                    }
                    threadDao.updateMessageCount(threadId, accountId, deduplicated.size)
                }
            }
            Result.success(Unit)
        } catch (e: ResourceNotFoundException) {
            Log.w("EmailRepo", "Thread $threadId not found on server — removing stale local data")
            threadDao.deleteThread(threadId, accountId)
            emailDao.deleteThreadEmails(threadId, accountId)
            Result.success(Unit)
        } catch (e: java.net.UnknownHostException) {
            Result.failure(Exception("No internet connection. Could not reach the mail server."))
        } catch (e: java.net.ConnectException) {
            Result.failure(Exception("Could not connect to the mail server. Check your internet connection."))
        } catch (e: jakarta.mail.MessagingException) {
            Result.failure(Exception("Mail server error: " + (e.message?.substringBefore(";")?.substringBefore("\n") ?: "Connection failed")))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    suspend fun toggleEmailStar(emailId: String, currentStarred: Boolean, accountId: String, threadId: String) {
        val newStarred = !currentStarred
        insertPendingAction(PendingActionType.MESSAGE_TOGGLE_STAR, accountId, threadId, payload = newStarred.toString(), emailIdsJson = emailId)
        emailDao.updateEmailStarred(emailId, accountId, newStarred)
    }
    suspend fun toggleStar(threadId: String, currentStarred: Boolean) {
        val newStarred = !currentStarred
        val accountId = resolveAccountId(threadId)
        insertPendingAction(PendingActionType.TOGGLE_STAR, accountId, threadId, payload = newStarred.toString())
        threadDao.updateThreadStarred(threadId, accountId, newStarred)
        emailDao.updateThreadStarred(threadId, accountId, newStarred)
    }
    suspend fun markEmailsAsRead(emailIds: List<String>) {
        if (emailIds.isEmpty()) return
        val activeAccountId = getActiveAccountId()
        emailDao.markEmailsAsRead(emailIds, activeAccountId)
        insertPendingAction(PendingActionType.MARK_READ, activeAccountId, "", emailIdsJson = emailIds.joinToString(","))
    }
    suspend fun markThreadAsRead(threadId: String) {
        val accountId = resolveAccountId(threadId)
        insertPendingAction(PendingActionType.MARK_READ, accountId, threadId)
        threadDao.updateThreadReadStatus(threadId, accountId, true)
        emailDao.updateThreadEmailsReadStatus(threadId, accountId, true)
    }
    suspend fun markThreadsAsRead(threadIds: List<String>): Result<Unit> {
        if (threadIds.isEmpty()) return Result.success(Unit)
        return try {
            val activeAccountId = getActiveAccountId()
            val unreadEmailIds = emailDao.getUnreadEmailIdsForThreads(threadIds, activeAccountId)
            threadDao.markThreadsAsRead(threadIds, activeAccountId)
            emailDao.markThreadEmailsAsRead(threadIds, activeAccountId)
            val provider = getActiveProvider() ?: return Result.failure(Exception(NO_ACTIVE_PROVIDER))
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
        val accountId = resolveAccountId(threadId)
        insertPendingAction(PendingActionType.MARK_UNREAD, accountId, threadId)
        threadDao.updateThreadReadStatus(threadId, accountId, false)
        emailDao.updateThreadEmailsReadStatus(threadId, accountId, false)
    }
    suspend fun archiveThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: resolveAccountId(threadId)
        insertPendingAction(PendingActionType.ARCHIVE, activeAccountId, threadId)
        threadDao.archiveThread(threadId, activeAccountId)
        emailDao.archiveThreadEmails(threadId, activeAccountId)
    }
    suspend fun archiveEmail(emailId: String, accountId: String, threadId: String) {
        insertPendingAction(PendingActionType.MESSAGE_ARCHIVE, accountId, threadId, emailIdsJson = emailId)
        emailDao.archiveEmail(emailId, accountId)
    }
    suspend fun unarchiveThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: resolveAccountId(threadId)
        insertPendingAction(PendingActionType.UNARCHIVE, activeAccountId, threadId)
        threadDao.unarchiveThread(threadId, activeAccountId)
        emailDao.unarchiveThreadEmails(threadId, activeAccountId)
    }
    suspend fun emptyTrash(isUnified: Boolean = false) {
        val activeAccountId = getActiveAccountId()
        val accountsToProcess = if (isUnified) {
            accountManager.getAccounts().map { it.id }
        } else {
            listOf(activeAccountId)
        }

        accountsToProcess.forEach { accId ->
            val provider = getProviderForAccount(accId)
            val trashIds = threadDao.getTrashThreadIds(accId)
            
            threadDao.emptyTrash(accId)
            emailDao.emptyTrash(accId)

            if (provider != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    for (threadId in trashIds) {
                        try {
                            provider.permanentlyDeleteThread(threadId)
                        } catch (e: RetrofitClient.AuthFailedException) {
                            android.util.Log.w("EmailRepo", "Auth expired, stopping batch delete", e)
                            break
                        } catch (e: Exception) {
                            android.util.Log.e("EmailRepo", "permanent delete failed for $threadId", e)
                        }
                    }
                }
            }
        }
    }

    suspend fun saveDraftLocally(accountId: String, to: String, cc: String, bcc: String, subject: String, body: String, threadId: String?, draftId: String?): String {
        val actualThreadId = threadId ?: draftId ?: java.util.UUID.randomUUID().toString()
        val actualMsgId = draftId ?: java.util.UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        
        val domainThread = EmailThread(
            threadId = actualThreadId,
            subject = subject.takeIf { it.isNotBlank() } ?: "(No subject)",
            from = "Me",
            fromEmail = accountManager.getAccounts().find { it.id == accountId }?.email ?: "",
            snippet = body.take(100),
            date = now,
            messageCount = 1,
            isRead = true,
            isStarred = false,
            latestMessageId = actualMsgId,
            participants = listOf(to).filter { it.isNotBlank() }
        )
        val domainEmail = com.shrivatsav.monomail.data.model.Email(
            id = actualMsgId,
            threadId = actualThreadId,
            subject = subject,
            from = "Me",
            fromEmail = accountManager.getAccounts().find { it.id == accountId }?.email ?: "",
            to = to,
            cc = cc,
            bcc = bcc,
            snippet = body.take(100),
            body = body,
            bodyIsHtml = false,
            date = now,
            isRead = true,
            isStarred = false,
            labels = listOf(com.shrivatsav.monomail.core.network.provider.EmailFolder.DRAFT.name),
            attachments = emptyList()
        )
        
        database.withTransaction {
            threadDao.insertThreads(listOf(domainThread.toEntity(accountId, inInbox = false, inSent = false, inArchived = false, inTrash = false, inSpam = false, inDrafts = true)))
            emailDao.insertEmails(listOf(domainEmail.toEntity(accountId)))
        }
        return actualMsgId
    }
    suspend fun emptySpam(isUnified: Boolean = false) {
        val activeAccountId = getActiveAccountId()
        val accountsToProcess = if (isUnified) {
            accountManager.getAccounts().map { it.id }
        } else {
            listOf(activeAccountId)
        }

        accountsToProcess.forEach { accId ->
            val provider = getProviderForAccount(accId)
            val spamIds = threadDao.getSpamThreadIds(accId)
            
            threadDao.emptySpam(accId)
            emailDao.emptySpam(accId)

            if (provider != null) {
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    spamIds.forEach { threadId ->
                        try { provider.permanentlyDeleteThread(threadId) } catch (e: Exception) { android.util.Log.e("EmailRepo", "permanent delete failed for $threadId", e) }
                    }
                }
            }
        }
    }
    suspend fun moveSpamToTrash(isUnified: Boolean = false) {
        val activeAccountId = getActiveAccountId()
        val accountsToProcess = if (isUnified) {
            accountManager.getAccounts().map { it.id }
        } else {
            listOf(activeAccountId)
        }
        accountsToProcess.forEach { accId ->
            val spamIds = threadDao.getSpamThreadIds(accId)
            spamIds.forEach { threadId ->
                deleteThread(threadId)
            }
        }
    }

    suspend fun deleteThread(threadId: String) {
        val accountId = resolveAccountId(threadId)
        insertPendingAction(PendingActionType.DELETE, accountId, threadId)
        threadDao.moveToTrash(threadId, accountId)
        emailDao.moveThreadEmailsToTrash(threadId, accountId)
    }
    suspend fun trashEmail(emailId: String, accountId: String, threadId: String) {
        insertPendingAction(PendingActionType.MESSAGE_DELETE, accountId, threadId, emailIdsJson = emailId)
        emailDao.moveEmailToTrash(emailId, accountId)
    }
    suspend fun deleteDraft(draftId: String) {
        emailDao.deleteDraftEmail(draftId)
    }
    suspend fun restoreThread(threadId: String) {
        val accountId = resolveAccountId(threadId)
        insertPendingAction(PendingActionType.RESTORE, accountId, threadId)
        threadDao.restoreFromTrash(threadId, accountId)
        emailDao.restoreThreadEmailsFromTrash(threadId, accountId)
    }
    suspend fun reportNotSpam(threadId: String) {
        val accountId = resolveAccountId(threadId)
        threadDao.reportNotSpam(threadId, accountId)
        emailDao.reportThreadEmailsNotSpam(threadId, accountId)
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
        params: SendEmailParams = SendEmailParams(),
        explicitAccountId: String? = null
    ): Result<SendEmailResult> {
        return try {
            val targetAccountId = explicitAccountId ?: getActiveAccountId()
            val provider = (if (explicitAccountId != null) getProviderForAccount(explicitAccountId) else getActiveProvider()) ?: return Result.failure(Exception(NO_ACTIVE_PROVIDER))
            val result = provider.sendEmail(
                from = from,
                to = to,
                subject = subject,
                body = body,
                options = SendEmailOptions(cc = params.cc, bcc = params.bcc, threadId = params.threadId, inReplyToMessageId = params.inReplyToMessageId, references = params.references, attachments = params.attachments)
            ) ?: return Result.failure(Exception("Send returned null — email was not sent"))
            val actualThreadId = result.threadId ?: UUID.randomUUID().toString()
            val actualMsgId = result.messageId ?: UUID.randomUUID().toString()
            // ponytail: DB insert is best-effort — email already sent to server, don't report false failure
            try {
                val now = System.currentTimeMillis()
                val domainThread = EmailThread(
                    threadId = actualThreadId,
                    subject = subject.cleanSubject(),
                    from = from,
                    fromEmail = from,
                    snippet = body.take(100),
                    date = now,
                    messageCount = 1,
                    isRead = true,
                    isStarred = false,
                    latestMessageId = actualMsgId,
                    participants = listOf(from, to)
                )
                val domainEmail = Email(
                    id = actualMsgId,
                    threadId = actualThreadId,
                    subject = subject,
                    from = from,
                    fromEmail = from,
                    to = to,
                    cc = params.cc,
                    bcc = params.bcc,
                    snippet = body.take(100),
                    body = body,
                    date = now,
                    isRead = true,
                    isStarred = false,
                    labels = listOf(EmailFolder.SENT.name),
                    attachments = params.attachments.map { com.shrivatsav.monomail.data.model.EmailAttachmentInfo(id = it.name, messageId = actualMsgId, name = it.name, mimeType = it.mimeType, size = it.size.toInt()) }
                )
                database.withTransaction {
                    threadDao.insertThreads(listOf(domainThread.toEntity(targetAccountId, inInbox = false, inSent = true, inArchived = false, inTrash = false, inSpam = false)))
                    emailDao.insertEmails(listOf(domainEmail.toEntity(targetAccountId)))
                }
            } catch (e: Exception) {
                Log.w("EmailRepo", "DB insert after send failed (email was sent)", e)
            }
            Result.success(result)
        } catch (e: Exception) {
            Log.e("EmailRepo", "sendEmail failed", e)
            Result.failure(e)
        }
    }

    suspend fun stagePendingSend(
        accountId: String,
        fromEmail: String,
        to: String,
        subject: String,
        body: String,
        params: SendEmailParams = SendEmailParams(),
        fromAlias: String? = null
    ): String {
        val id = UUID.randomUUID().toString()
        val cachedAttachments = if (params.attachments.isNotEmpty()) {
            copyAttachmentsToCache("pending_$id", params.attachments)
        } else emptyList()
        val entity = PendingSendEntity(
            id = id,
            accountId = accountId,
            fromEmail = fromEmail,
            to = to,
            cc = params.cc,
            bcc = params.bcc,
            subject = subject,
            body = body,
            attachmentsJson = gson.toJson(
                cachedAttachments.map { a ->
                    val rawPath = a.uri.path ?: a.uri.toString()
                    mapOf(
                        "localPath" to rawPath,
                        "name" to a.name,
                        "size" to a.size,
                        "mimeType" to a.mimeType
                    )
                }
            ),
            fromAlias = fromAlias,
            threadId = params.threadId,
            messageId = params.inReplyToMessageId,
            messageReferences = params.references
        )
        pendingSendDao.insert(entity)
        return id
    }

    suspend fun completePendingSend(id: String) {
        val entity = pendingSendDao.getById(id) ?: return
        val attachments = parseStoredAttachments(entity.attachmentsJson)
        val result = sendEmail(
            from = entity.fromEmail,
            to = entity.to,
            subject = entity.subject,
            body = entity.body,
            params = SendEmailParams(
                cc = entity.cc,
                bcc = entity.bcc,
                attachments = attachments,
                threadId = entity.threadId,
                inReplyToMessageId = entity.messageId,
                references = entity.messageReferences
            ),
            explicitAccountId = entity.accountId
        )
        pendingSendDao.deleteById(id)
        cleanupPendingAttachmentFiles(entity.attachmentsJson)
        if (result.isFailure) {
            Log.w("EmailRepo", "completePendingSend failed for $id", result.exceptionOrNull())
        }
    }

    suspend fun cancelPendingSend(id: String) {
        val entity = pendingSendDao.getById(id) ?: return
        pendingSendDao.deleteById(id)
        cleanupPendingAttachmentFiles(entity.attachmentsJson)
    }

    suspend fun getAllPendingSends(): List<PendingSendEntity> = pendingSendDao.getAll()

    suspend fun getAllPendingSendsForAccount(accountId: String): List<PendingSendEntity> =
        pendingSendDao.getAllForAccount(accountId)
    suspend fun scheduleSend(
        accountId: String,
        fromEmail: String,
        to: String,
        subject: String,
        body: String,
        scheduledAt: Long,
        params: ScheduleSendParams = ScheduleSendParams()
    ) {
        val id = UUID.randomUUID().toString()
        val entity = ScheduledMessageEntity(
            id = id,
            accountId = accountId,
            fromEmail = fromEmail,
            to = to,
            cc = params.cc,
            bcc = params.bcc,
            subject = subject,
            body = body,
            attachmentsJson = gson.toJson(
                params.attachments.map { a ->
                    mapOf(
                        "localPath" to a.uri.toString(),
                        "name" to a.name,
                        "size" to a.size,
                        "mimeType" to a.mimeType
                    )
                }
            ),
            scheduledAt = scheduledAt,
            fromAlias = params.fromAlias,
            threadId = params.threadId,
            messageId = params.inReplyToMessageId
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
                    if (file.exists()) file.delete().also { if (!it) Log.w("EmailRepository", "Failed to delete scheduled attachment: ${file.path}") }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("EmailRepository", "Failed to cleanup scheduled attachment files", e)
        }
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

    private fun parseStoredAttachments(json: String): List<EmailAttachment> {
        if (json.isBlank() || json == "[]") return emptyList()
        return try {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val list: List<Map<String, Any>> = gson.fromJson(json, type)
            list.mapNotNull { m ->
                val path = m["localPath"] as? String ?: return@mapNotNull null
                val file = File(path)
                if (!file.exists()) return@mapNotNull null
                EmailAttachment(
                    uri = Uri.fromFile(file),
                    name = (m["name"] as? String) ?: file.name,
                    size = ((m["size"] as? Double)?.toLong() ?: file.length()),
                    mimeType = (m["mimeType"] as? String) ?: "application/octet-stream"
                )
            }
        } catch (e: Exception) { emptyList() }
    }
    private fun cleanupPendingAttachmentFiles(attachmentsJson: String) {
        if (attachmentsJson.isBlank() || attachmentsJson == "[]") return
        try {
            val type = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
            val attachments: List<Map<String, Any>> = gson.fromJson(attachmentsJson, type)
            attachments.forEach { a ->
                val path = a["localPath"] as? String
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) file.delete()
                    file.parentFile?.delete()
                }
            }
        } catch (e: Exception) {
            Log.w("EmailRepo", "Failed to cleanup pending attachment files", e)
        }
    }
    // --- Send-as aliases ---
    private val _sendAsAliases = kotlinx.coroutines.flow.MutableStateFlow<List<SendAsAlias>>(emptyList())
    val sendAsAliasesFlow: kotlinx.coroutines.flow.StateFlow<List<SendAsAlias>> = _sendAsAliases.asStateFlow()

    suspend fun refreshSendAsAliases() {
        val activeAccount = accountManager.getActiveAccount() ?: return
        val provider = providerFactory(activeAccount)
        val aliases = try {
            provider.getSendAsAliases()
        } catch (e: Exception) {
            Log.e("EmailRepo", "Failed to refresh send-as aliases", e)
            emptyList()
        }
        _sendAsAliases.value = aliases
    }

    fun getPendingScheduledMessagesFlow(accountId: String) = scheduledMessageDao.getPendingScheduledMessages(accountId)
    fun getPendingScheduledCountFlow(accountId: String) = scheduledMessageDao.getPendingCount(accountId)
    suspend fun getScheduledMessageById(id: String) = scheduledMessageDao.getScheduledMessageById(id)
    suspend fun snoozeThread(threadId: String, untilTimestamp: Long, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: resolveAccountId(threadId)
        insertPendingAction(PendingActionType.SNOOZE, activeAccountId, threadId, payload = untilTimestamp.toString())
        threadDao.snoozeThread(threadId, activeAccountId, untilTimestamp)
        emailDao.snoozeThreadEmails(threadId, activeAccountId, untilTimestamp)
    }
    suspend fun unsnoozeThread(threadId: String, explicitAccountId: String? = null) {
        val activeAccountId = explicitAccountId ?: resolveAccountId(threadId)
        insertPendingAction(PendingActionType.UNSNOOZE, activeAccountId, threadId)
        threadDao.unsnoozeThread(threadId, activeAccountId)
        emailDao.unsnoozeThreadEmails(threadId, activeAccountId)
    }
}

data class SendEmailParams(
    val cc: String = "",
    val bcc: String = "",
    val threadId: String? = null,
    val inReplyToMessageId: String? = null,
    val references: String? = null,
    val attachments: List<EmailAttachment> = emptyList()
)

data class ScheduleSendParams(
    val cc: String = "",
    val bcc: String = "",
    val attachments: List<EmailAttachment> = emptyList(),
    val fromAlias: String? = null,
    val threadId: String? = null,
    val inReplyToMessageId: String? = null,
    val references: String? = null
)
