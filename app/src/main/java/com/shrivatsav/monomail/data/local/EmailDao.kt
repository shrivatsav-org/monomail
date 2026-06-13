package com.shrivatsav.monomail.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EmailDao {

    @Query("SELECT * FROM emails WHERE threadId = :threadId ORDER BY date ASC")
    fun getEmailsForThread(threadId: String): Flow<List<EmailEntity>>

    @Query("SELECT * FROM emails WHERE id = :id LIMIT 1")
    suspend fun getEmailById(id: String): EmailEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEmails(emails: List<EmailEntity>)

    @Query("UPDATE emails SET isStarred = :isStarred WHERE threadId = :threadId")
    suspend fun updateThreadStarred(threadId: String, isStarred: Boolean)

    @Query("UPDATE emails SET isRead = 1 WHERE id IN (:emailIds)")
    suspend fun markEmailsAsRead(emailIds: List<String>)

    @Query("DELETE FROM emails WHERE threadId = :threadId")
    suspend fun deleteThreadEmails(threadId: String)
    
    @Query("DELETE FROM emails")
    suspend fun clearAll()
}
