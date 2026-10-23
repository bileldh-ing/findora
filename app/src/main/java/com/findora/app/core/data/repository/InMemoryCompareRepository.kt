package com.findora.app.core.data.repository

import com.findora.app.core.domain.compare.CompareAddResult
import com.findora.app.core.domain.compare.CompareSelection
import com.findora.app.core.domain.repository.CompareRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Compare selections per user id, held in memory for the process: a comparison is a short-lived working set,
 * so it is not persisted (see README, Compare). Each user keeps their own selection.
 */
@Singleton
class InMemoryCompareRepository
    @Inject
    constructor() : CompareRepository {
        private val byUser = MutableStateFlow<Map<String, List<String>>>(emptyMap())

        override fun observe(userId: String): Flow<List<String>> =
            byUser.map { it[userId].orEmpty() }.distinctUntilChanged()

        override suspend fun add(
            userId: String,
            propertyId: String,
        ): CompareAddResult {
            var result = CompareAddResult.Added
            byUser.update { map ->
                val (next, outcome) = CompareSelection.add(map[userId].orEmpty(), propertyId)
                result = outcome
                map + (userId to next)
            }
            return result
        }

        override suspend fun remove(
            userId: String,
            propertyId: String,
        ) {
            byUser.update { map -> map + (userId to map[userId].orEmpty() - propertyId) }
        }

        override suspend fun move(
            userId: String,
            fromIndex: Int,
            toIndex: Int,
        ) {
            byUser.update { map -> map + (userId to CompareSelection.move(map[userId].orEmpty(), fromIndex, toIndex)) }
        }

        override suspend fun clear(userId: String) {
            byUser.update { it - userId }
        }
    }
