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

    /** Deletes the newest row matching the event; returns the number of rows removed (0 or 1). */
    @Query(
        "DELETE FROM interaction_events WHERE id = (SELECT id FROM interaction_events WHERE user_id = :userId " +
            "AND property_id = :propertyId AND type = :type AND occurred_at = :occurredAtMillis " +
            "ORDER BY id DESC LIMIT 1)",
    )
    suspend fun deleteLatest(
        userId: String,
        propertyId: String,
        type: String,
        occurredAtMillis: Long,
    ): Int

    @Query(
        "SELECT property_id FROM interaction_events WHERE user_id = :userId AND type IN (:types) " +
            "GROUP BY property_id ORDER BY MAX(occurred_at) DESC, MAX(id) DESC",
    )
    fun observePropertyIds(
        userId: String,
        types: List<String>,
    ): Flow<List<String>>

    @Query("SELECT COUNT(*) FROM interaction_events WHERE user_id = :userId AND type = :type")
    suspend fun countByType(
        userId: String,
        type: String,
    ): Int

    @Query("DELETE FROM interaction_events WHERE occurred_at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long): Int
}
