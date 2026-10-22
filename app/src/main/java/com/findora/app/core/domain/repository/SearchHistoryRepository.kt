package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.RecentSearch
import kotlinx.coroutines.flow.Flow

interface SearchHistoryRepository {
    fun observeRecent(
        userId: String,
        limit: Int = DEFAULT_LIMIT,
    ): Flow<List<RecentSearch>>

    /** Records [query] (trimmed, case-insensitive de-duplication) and keeps only the newest entries. */
    suspend fun record(
        userId: String,
        query: String,
    )

    /** Removes one entry (swipe to delete); matching is case-insensitive like [record]. */
    suspend fun delete(
        userId: String,
        query: String,
    )

    suspend fun clear(userId: String)

    companion object {
        const val DEFAULT_LIMIT = 10
        const val MAX_STORED = 25
    }
}
