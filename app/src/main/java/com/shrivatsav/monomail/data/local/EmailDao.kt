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
}
