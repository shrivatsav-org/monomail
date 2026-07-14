package com.shrivatsav.monomail.core.database.local
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
@Dao
interface ScheduledMessageDao {
    @Query("SELECT * FROM scheduled_messages WHERE accountId = :accountId AND isSent = 0 ORDER BY scheduledAt ASC")
    fun getPendingScheduledMessages(accountId: String): Flow<List<ScheduledMessageEntity>>
    @Query("SELECT * FROM scheduled_messages WHERE accountId = :accountId AND isSent = 0 ORDER BY scheduledAt ASC")
    suspend fun getPendingScheduledMessagesList(accountId: String): List<ScheduledMessageEntity>
    @Query("SELECT * FROM scheduled_messages WHERE id = :id LIMIT 1")
    suspend fun getScheduledMessageById(id: String): ScheduledMessageEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScheduledMessage(message: ScheduledMessageEntity)
    @Query("UPDATE scheduled_messages SET isSent = 1 WHERE id = :id")
    suspend fun markAsSent(id: String)
    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun deleteScheduledMessage(id: String)
    @Query("DELETE FROM scheduled_messages WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)
    @Query("SELECT COUNT(*) FROM scheduled_messages WHERE accountId = :accountId AND isSent = 0")
    fun getPendingCount(accountId: String): Flow<Int>
}
