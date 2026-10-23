package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.SavedSearchEntity
import com.findora.app.core.data.local.entity.SavedSearchSeenEntity
import kotlinx.coroutines.flow.Flow

/** Saved searches and the listing ids each one already reported. Writes are scoped to the owning user. */
@Suppress("TooManyFunctions")
@Dao
abstract class SavedSearchDao {
    @Insert
    abstract suspend fun insert(search: SavedSearchEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertSeen(rows: List<SavedSearchSeenEntity>)

    /** Creates the search with its baseline of already-matching listings in one transaction. */
    @Transaction
    open suspend fun insertWithSeen(
        search: SavedSearchEntity,
        seenIds: Collection<String>,
    ): Long {
        val id = insert(search)
        if (seenIds.isNotEmpty()) insertSeen(seenIds.map { SavedSearchSeenEntity(id, it) })
        return id
    }

    @Query("SELECT * FROM saved_searches WHERE user_id = :userId ORDER BY created_at DESC, id DESC")
    abstract fun observeForUser(userId: String): Flow<List<SavedSearchEntity>>

    @Query("SELECT * FROM saved_searches WHERE id = :id AND user_id = :userId")
    abstract suspend fun find(
        id: Long,
        userId: String,
    ): SavedSearchEntity?

    @Query("SELECT * FROM saved_searches WHERE alerts_enabled = 1")
    abstract suspend fun withAlertsEnabled(): List<SavedSearchEntity>

    @Query("SELECT * FROM saved_searches WHERE alerts_enabled = 1 AND user_id = :userId ORDER BY id")
    abstract suspend fun withAlertsEnabledFor(userId: String): List<SavedSearchEntity>

    @Query("UPDATE saved_searches SET alerts_enabled = :enabled WHERE id = :id")
    abstract suspend fun setAlertsEnabled(
        id: Long,
        enabled: Boolean,
    ): Int

    @Query("UPDATE saved_searches SET alerts_enabled = :enabled WHERE id = :id AND user_id = :userId")
    abstract suspend fun setAlertsEnabledFor(
        id: Long,
        userId: String,
        enabled: Boolean,
    ): Int

    @Query("UPDATE saved_searches SET name = :name WHERE id = :id AND user_id = :userId")
    abstract suspend fun rename(
        id: Long,
        userId: String,
        name: String,
    ): Int

    @Query("UPDATE saved_searches SET alert_frequency = :frequency WHERE id = :id AND user_id = :userId")
    abstract suspend fun setFrequency(
        id: Long,
        userId: String,
        frequency: String,
    ): Int

    @Query("UPDATE saved_searches SET last_notified_at = :atMillis WHERE id = :id")
    abstract suspend fun markNotified(
        id: Long,
        atMillis: Long,
    ): Int

    @Query("UPDATE saved_searches SET last_checked_at = :atMillis WHERE id = :id")
    protected abstract suspend fun markChecked(
        id: Long,
        atMillis: Long,
    ): Int

    @Query("SELECT property_id FROM saved_search_seen WHERE search_id = :id")
    abstract suspend fun seenIds(id: Long): List<String>

    /** Remembers [propertyIds] as seen by search [id] and stamps the check (and the alert), atomically. */
    @Transaction
    open suspend fun recordCheck(
        id: Long,
        propertyIds: Collection<String>,
        atMillis: Long,
        notified: Boolean = false,
    ) {
        if (propertyIds.isNotEmpty()) insertSeen(propertyIds.map { SavedSearchSeenEntity(id, it) })
        markChecked(id, atMillis)
        if (notified) markNotified(id, atMillis)
    }

    @Query("DELETE FROM saved_search_seen WHERE search_id = :id")
    protected abstract suspend fun deleteSeen(id: Long)

    @Query("DELETE FROM saved_searches WHERE id = :id AND user_id = :userId")
    abstract suspend fun delete(
        id: Long,
        userId: String,
    ): Int

    /** Deletes the search (only when [userId] owns it) and its seen ids. */
    @Transaction
    open suspend fun deleteWithSeen(
        id: Long,
        userId: String,
    ): Boolean {
        val deleted = delete(id, userId) > 0
        if (deleted) deleteSeen(id)
        return deleted
    }
}
