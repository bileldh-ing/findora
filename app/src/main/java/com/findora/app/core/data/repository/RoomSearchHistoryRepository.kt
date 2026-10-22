package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.RecentSearchDao
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSearchHistoryRepository
    @Inject
    constructor(
        private val dao: RecentSearchDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SearchHistoryRepository {
        override fun observeRecent(
            userId: String,
            limit: Int,
        ): Flow<List<RecentSearch>> =
            dao
                .observeRecent(userId, limit)
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override suspend fun record(
            userId: String,
            query: String,
        ) {
            val trimmed = normalize(query)
            if (trimmed.isEmpty()) return
            withContext(ioDispatcher) {
                dao.record(
                    userId = userId,
                    query = trimmed,
                    normalizedQuery = trimmed.lowercase(Locale.ROOT),
                    searchedAtMillis = clock.now().toEpochMilli(),
                    keep = SearchHistoryRepository.MAX_STORED,
                )
            }
        }

        override suspend fun delete(
            userId: String,
            query: String,
        ) = withContext(ioDispatcher) { dao.delete(userId, normalize(query).lowercase(Locale.ROOT)) }

        override suspend fun clear(userId: String) = withContext(ioDispatcher) { dao.clear(userId) }

        private fun normalize(query: String): String = query.trim().replace(whitespace, " ")

        private companion object {
            val whitespace = Regex("\\s+")
        }
    }
