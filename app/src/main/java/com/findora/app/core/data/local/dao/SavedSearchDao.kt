package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.findora.app.core.data.local.entity.SavedSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedSearchDao {
    @Insert
    suspend fun insert(search: SavedSearchEntity): Long

    @Query("SELECT * FROM saved_searches WHERE user_id = :userId ORDER BY created_at DESC")
    fun observeForUser(userId: String): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_searches WHERE alerts_enabled = 1")
    suspend fun withAlertsEnabled(): List<SavedSearchEntity>

    @Query("UPDATE saved_searches SET alerts_enabled = :enabled WHERE id = :id")
    suspend fun setAlertsEnabled(
        id: Long,
        enabled: Boolean,
    ): Int

    @Query("UPDATE saved_searches SET last_notified_at = :atMillis WHERE id = :id")
    suspend fun markNotified(
        id: Long,
        atMillis: Long,
    ): Int

    @Query("DELETE FROM saved_searches WHERE id = :id AND user_id = :userId")
    suspend fun delete(
        id: Long,
        userId: String,
    ): Int
}
