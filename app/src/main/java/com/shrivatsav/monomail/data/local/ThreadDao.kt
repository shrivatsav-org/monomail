package com.shrivatsav.monomail.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ThreadDao {
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC")
    fun getInboxThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE inInbox = 1 ORDER BY date DESC")
    fun getAllInboxThreads(): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC LIMIT 1")
    suspend fun getLatestInboxThread(accountId: String): ThreadEntity?
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inSent = 1 ORDER BY date DESC")
    fun getSentThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inArchived = 1 ORDER BY date DESC")
    fun getArchivedThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND isStarred = 1 ORDER BY date DESC")
    fun getStarredThreads(accountId: String): Flow<List<ThreadEntity>>
    @Query("SELECT * FROM threads WHERE accountId = :accountId AND inTrash = 1 ORDER BY date DESC")
    fun getTrashThreads(accountId: String): Flow<List<ThreadEntity>>
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
    @Query("DELETE FROM threads WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun deleteThread(threadId: String, accountId: String)
    @Query("UPDATE threads SET inInbox = 0, inSent = 0, inArchived = 0, inTrash = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun moveToTrash(threadId: String, accountId: String)
    @Query("UPDATE threads SET inTrash = 0, inInbox = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun restoreFromTrash(threadId: String, accountId: String)
    @Query("DELETE FROM threads WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    @Query("DELETE FROM threads WHERE accountId = :accountId AND inTrash = 1")
    suspend fun emptyTrash(accountId: String)
}
