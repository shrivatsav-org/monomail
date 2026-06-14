package com.shrivatsav.monomail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadDao {

    @Query("SELECT * FROM threads WHERE inInbox = 1 ORDER BY date DESC")
    fun getInboxThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE inSent = 1 ORDER BY date DESC")
    fun getSentThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE inArchived = 1 ORDER BY date DESC")
    fun getArchivedThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE isStarred = 1 ORDER BY date DESC")
    fun getStarredThreads(): Flow<List<ThreadEntity>>

    @Query("SELECT * FROM threads WHERE inTrash = 1 ORDER BY date DESC")
    fun getTrashThreads(): Flow<List<ThreadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertThreads(threads: List<ThreadEntity>)

    @Query("UPDATE threads SET isStarred = :isStarred WHERE threadId = :threadId")
    suspend fun updateThreadStarred(threadId: String, isStarred: Boolean)

    @Query("UPDATE threads SET inInbox = 0, inArchived = 1 WHERE threadId = :threadId")
    suspend fun archiveThread(threadId: String)

    @Query("UPDATE threads SET inInbox = 1, inArchived = 0 WHERE threadId = :threadId")
    suspend fun unarchiveThread(threadId: String)

    @Query("UPDATE threads SET isRead = :isRead WHERE threadId = :threadId")
    suspend fun updateThreadReadStatus(threadId: String, isRead: Boolean)

    @Query("DELETE FROM threads WHERE threadId = :threadId")
    suspend fun deleteThread(threadId: String)

    @Query("UPDATE threads SET inInbox = 0, inSent = 0, inArchived = 0, inTrash = 1 WHERE threadId = :threadId")
    suspend fun moveToTrash(threadId: String)

    @Query("UPDATE threads SET inTrash = 0, inInbox = 1 WHERE threadId = :threadId")
    suspend fun restoreFromTrash(threadId: String)
    
    @Query("DELETE FROM threads")
    suspend fun clearAll()
}
