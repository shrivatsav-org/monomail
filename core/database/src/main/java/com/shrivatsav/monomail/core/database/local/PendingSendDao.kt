package com.shrivatsav.monomail.core.database.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PendingSendDao {
    @Query("SELECT * FROM pending_sends WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PendingSendEntity?

    @Query("SELECT * FROM pending_sends WHERE accountId = :accountId ORDER BY createdAt ASC")
    suspend fun getAllForAccount(accountId: String): List<PendingSendEntity>

    @Query("SELECT * FROM pending_sends ORDER BY createdAt ASC")
    suspend fun getAll(): List<PendingSendEntity>

    @Query("SELECT * FROM pending_sends WHERE accountId = :accountId ORDER BY createdAt ASC")
    fun getAllForAccountFlow(accountId: String): Flow<List<PendingSendEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: PendingSendEntity)

    @Query("DELETE FROM pending_sends WHERE id = :id")
    suspend fun deleteById(id: String)
}
