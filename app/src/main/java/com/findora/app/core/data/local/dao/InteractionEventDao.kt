package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.findora.app.core.data.local.entity.InteractionEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface InteractionEventDao {
    @Insert
    suspend fun insert(event: InteractionEventEntity): Long

    @Query("SELECT * FROM interaction_events WHERE user_id = :userId ORDER BY occurred_at DESC, id DESC LIMIT :limit")
    fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEventEntity>>

    @Query("SELECT COUNT(*) FROM interaction_events WHERE user_id = :userId AND type = :type")
    suspend fun countByType(
        userId: String,
        type: String,
    ): Int

    @Query("DELETE FROM interaction_events WHERE occurred_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
