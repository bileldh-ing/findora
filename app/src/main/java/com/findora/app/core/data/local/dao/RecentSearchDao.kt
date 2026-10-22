package com.findora.app.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.findora.app.core.data.local.entity.RecentSearchEntity
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecentSearchDao {
    @Query("SELECT * FROM recent_searches WHERE user_id = :userId ORDER BY searched_at DESC, id DESC LIMIT :limit")
    abstract fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<RecentSearchEntity>>

    @Query("SELECT id FROM recent_searches WHERE user_id = :userId AND normalized_query = :normalizedQuery")
    abstract suspend fun findId(
        userId: String,
        normalizedQuery: String,
    ): Long?

    @Insert
    abstract suspend fun insert(search: RecentSearchEntity): Long

    @Query("UPDATE recent_searches SET query = :query, searched_at = :searchedAtMillis WHERE id = :id")
    abstract suspend fun touch(
        id: Long,
        query: String,
        searchedAtMillis: Long,
    )

    @Query(
        """
        DELETE FROM recent_searches WHERE user_id = :userId AND id NOT IN (
            SELECT id FROM recent_searches WHERE user_id = :userId ORDER BY searched_at DESC, id DESC LIMIT :keep
        )
        """,
    )
    abstract suspend fun trim(
        userId: String,
        keep: Int,
    )

    @Query("DELETE FROM recent_searches WHERE user_id = :userId")
    abstract suspend fun clear(userId: String)

    @Transaction
    open suspend fun record(
        userId: String,
        query: String,
        normalizedQuery: String,
        searchedAtMillis: Long,
        keep: Int,
    ) {
        // Explicit find-then-write instead of SQL UPSERT, which needs SQLite 3.24 (API 30+); minSdk is 26.
        val existing = findId(userId, normalizedQuery)
        if (existing == null) {
            insert(
                RecentSearchEntity(
                    userId = userId,
                    query = query,
                    normalizedQuery = normalizedQuery,
                    searchedAtMillis = searchedAtMillis,
                ),
            )
        } else {
            touch(existing, query, searchedAtMillis)
        }
        trim(userId, keep)
    }
}
