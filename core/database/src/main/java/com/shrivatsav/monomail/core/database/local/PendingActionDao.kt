package com.shrivatsav.monomail.core.database.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingActionDao {
    @Query("SELECT * FROM pending_actions WHERE status = 'PENDING' ORDER BY createdAt ASC LIMIT 1")
    suspend fun getNextPending(): PendingActionEntity?

    @Query("SELECT * FROM pending_actions WHERE accountId = :accountId AND (status = 'PENDING' OR status = 'IN_FLIGHT')")
    suspend fun getPendingForAccount(accountId: String): List<PendingActionEntity>

    @Query("SELECT * FROM pending_actions WHERE (status = 'PENDING' OR status = 'IN_FLIGHT')")
    fun getPendingFlow(): Flow<List<PendingActionEntity>>

    @Query("SELECT * FROM pending_actions WHERE (status = 'PENDING' OR status = 'IN_FLIGHT')")
    suspend fun getAllPending(): List<PendingActionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(action: PendingActionEntity)

    @Query("UPDATE pending_actions SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: String, status: PendingActionStatus)

    @Query("UPDATE pending_actions SET status = 'PENDING', retryCount = retryCount + 1, errorMessage = :error WHERE id = :id")
    suspend fun incrementRetry(id: String, error: String)

    @Query("DELETE FROM pending_actions WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM pending_actions WHERE accountId = :accountId")
    suspend fun clearForAccount(accountId: String)

    @Query("SELECT COUNT(*) FROM pending_actions WHERE status = 'FAILED'")
    fun getFailedCountFlow(): Flow<Int>
}
