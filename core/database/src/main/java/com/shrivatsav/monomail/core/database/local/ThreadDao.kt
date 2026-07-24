package com.shrivatsav.monomail.core.database.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC LIMIT 500")
    fun getInboxThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inInbox = 1 ORDER BY date DESC LIMIT 500")
    fun getAllInboxThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestInboxThread(accountId: String): ThreadEntity?
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inSent = 1 ORDER BY date DESC LIMIT 500")
    fun getSentThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inArchived = 1 ORDER BY date DESC LIMIT 500")
    fun getArchivedThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND isStarred = 1 ORDER BY date DESC LIMIT 500")
    fun getStarredThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inTrash = 1 ORDER BY date DESC LIMIT 500")
    fun getTrashThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inDrafts = 1 AND inTrash = 0 ORDER BY date DESC LIMIT 500")
    fun getDraftThreads(accountId: String): Flow<List<ThreadEntity>>

    @Query("SELECT threadId FROM threads WHERE accountId = :accountId AND inTrash = 1")
    suspend fun getTrashThreadIds(accountId: String): List<String>

    @Query("SELECT threadId FROM threads WHERE accountId = :accountId AND inSpam = 1")
    suspend fun getSpamThreadIds(accountId: String): List<String>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)
    @Query("UPDATE threads SET isStarred = :isStarred WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun updateThreadStarred(threadId: String, accountId: String, isStarred: Boolean)
    @Query("UPDATE threads SET inInbox = 0, inArchived = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun archiveThread(threadId: String, accountId: String)
    @Query("UPDATE threads SET inInbox = 1, inArchived = 0 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun unarchiveThread(threadId: String, accountId: String)
    @Query("UPDATE threads SET isRead = :isRead WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun updateThreadReadStatus(threadId: String, accountId: String, isRead: Boolean)
    @Query("UPDATE threads SET isRead = 1 WHERE accountId = :accountId AND threadId IN (:threadIds)")
    suspend fun markThreadsAsRead(threadIds: List<String>, accountId: String)
    @Query("DELETE FROM threads WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun deleteThread(threadId: String, accountId: String)
    @Query("UPDATE threads SET inInbox = 0, inSent = 0, inArchived = 0, inSpam = 0, inDrafts = 0, inTrash = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun moveToTrash(threadId: String, accountId: String)
    @Query("UPDATE threads SET inTrash = 0, inInbox = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun restoreFromTrash(threadId: String, accountId: String)
    @Query("UPDATE threads SET inSpam = 0, inInbox = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun reportNotSpam(threadId: String, accountId: String)
    @Query("DELETE FROM threads WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inSpam = 1 ORDER BY date DESC LIMIT 500")
    fun getSpamThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND isSnoozed = 1 ORDER BY date DESC LIMIT 500")
    fun getSnoozedThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("UPDATE threads SET inInbox = 0, isSnoozed = 1, snoozedUntil = :untilTimestamp WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun snoozeThread(threadId: String, accountId: String, untilTimestamp: Long)
    @Query("UPDATE threads SET inInbox = 1, isSnoozed = 0, snoozedUntil = 0 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun unsnoozeThread(threadId: String, accountId: String)
    @Query("SELECT threadId, isSnoozed, snoozedUntil FROM threads WHERE threadId IN (:threadIds) AND accountId = :accountId")
    suspend fun getSnoozeStateForThreads(threadIds: List<String>, accountId: String): List<ThreadSnoozeProjection>
    @Query("DELETE FROM threads WHERE accountId = :accountId AND inTrash = 1")
    suspend fun emptyTrash(accountId: String)
    @Query("DELETE FROM threads WHERE accountId = :accountId AND inSpam = 1")
    suspend fun emptySpam(accountId: String)

    @Query("SELECT threadId, snippet FROM threads WHERE accountId = :accountId AND snippet != ''")
    suspend fun getSnippetsForAccount(accountId: String): List<ThreadSnippetProjection>

    @Query("SELECT threadId, isRead FROM threads WHERE accountId = :accountId")
    suspend fun getReadStatuses(accountId: String): List<ThreadReadStatusProjection>

    @Query("SELECT accountId FROM threads WHERE threadId = :threadId LIMIT 1")
    suspend fun getAccountIdForThread(threadId: String): String?

    @Query("SELECT * FROM threads WHERE inArchived = 1 ORDER BY date DESC LIMIT 500")
    fun getAllArchivedThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE isStarred = 1 ORDER BY date DESC LIMIT 500")
    fun getAllStarredThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inTrash = 1 ORDER BY date DESC LIMIT 500")
    fun getAllTrashThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inSent = 1 ORDER BY date DESC LIMIT 500")
    fun getAllSentThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inSpam = 1 ORDER BY date DESC LIMIT 500")
    fun getAllSpamThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE isSnoozed = 1 ORDER BY date DESC LIMIT 500")
    fun getAllSnoozedThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inDrafts = 1 AND inTrash = 0 ORDER BY date DESC LIMIT 500")
    fun getAllDraftThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE threadId IN (:threadIds) AND accountId = :accountId ORDER BY date DESC")
    suspend fun getThreadsByIds(threadIds: List<String>, accountId: String): List<ThreadEntity>
}

data class ThreadReadStatusProjection(
    val threadId: String,
    val isRead: Boolean
)

data class ThreadSnippetProjection(
    val threadId: String,
    val snippet: String
)

data class ThreadSnoozeProjection(
    val threadId: String,
    val isSnoozed: Boolean,
    val snoozedUntil: Long
)
