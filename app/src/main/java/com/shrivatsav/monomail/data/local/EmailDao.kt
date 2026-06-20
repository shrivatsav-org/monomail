package com.shrivatsav.monomail.data.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface EmailDao {
    @Query("SELECT * FROM emails WHERE threadId = :threadId AND accountId = :accountId ORDER BY date ASC")
    fun getEmailsForThread(threadId: String, accountId: String): Flow<List<EmailEntity>>
    @Query("SELECT * FROM emails WHERE id = :id AND accountId = :accountId LIMIT 1")
    suspend fun getEmailById(id: String, accountId: String): EmailEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)
    @Query("UPDATE emails SET isStarred = :isStarred WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun updateThreadStarred(threadId: String, accountId: String, isStarred: Boolean)
    @Query("UPDATE emails SET isRead = 1 WHERE id IN (:emailIds) AND accountId = :accountId")
    suspend fun markEmailsAsRead(emailIds: List<String>, accountId: String)
    @Query("DELETE FROM emails WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun deleteThreadEmails(threadId: String, accountId: String)
    @Query("DELETE FROM emails WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    @Query("DELETE FROM emails WHERE accountId = :accountId AND inTrash = 1")
    suspend fun emptyTrash(accountId: String)

    @Query("UPDATE emails SET inInbox = 0, inArchived = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun archiveThreadEmails(threadId: String, accountId: String)

    @Query("UPDATE emails SET inInbox = 1, inArchived = 0 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun unarchiveThreadEmails(threadId: String, accountId: String)

    @Query("UPDATE emails SET isRead = :isRead WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun updateThreadEmailsReadStatus(threadId: String, accountId: String, isRead: Boolean)

    @Query("UPDATE emails SET inInbox = 0, inSent = 0, inArchived = 0, inTrash = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun moveThreadEmailsToTrash(threadId: String, accountId: String)

    @Query("UPDATE emails SET inTrash = 0, inInbox = 1 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun restoreThreadEmailsFromTrash(threadId: String, accountId: String)
    @Query("SELECT * FROM emails WHERE accountId = :accountId AND inInbox = 1 ORDER BY date DESC")
    fun getInboxEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE inInbox = 1 ORDER BY date DESC")
    fun getAllInboxEmails(): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND inSent = 1 ORDER BY date DESC")
    fun getSentEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND inArchived = 1 ORDER BY date DESC")
    fun getArchivedEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND isStarred = 1 ORDER BY date DESC")
    fun getStarredEmails(accountId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE accountId = :accountId AND inTrash = 1 ORDER BY date DESC")
    fun getTrashEmails(accountId: String): Flow<List<EmailEntity>>
    @Query("UPDATE emails SET isSnoozed = 1, snoozedUntil = :untilTimestamp WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun snoozeThreadEmails(threadId: String, accountId: String, untilTimestamp: Long)
    @Query("UPDATE emails SET inInbox = 1, isSnoozed = 0, snoozedUntil = 0 WHERE threadId = :threadId AND accountId = :accountId")
    suspend fun unsnoozeThreadEmails(threadId: String, accountId: String)
}
