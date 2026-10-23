package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.alert.SavedFilterJson
import com.findora.app.core.data.local.dao.PriceWatchDao
import com.findora.app.core.data.local.dao.SavedSearchDao
import com.findora.app.core.data.local.entity.PriceWatchEntity
import com.findora.app.core.data.local.entity.SavedSearchEntity
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.domain.repository.PriceWatchRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Suppress("TooManyFunctions")
@Singleton
class RoomSavedSearchRepository
    @Inject
    constructor(
        private val dao: SavedSearchDao,
        private val clock: AppClock,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : SavedSearchRepository {
        override fun observeSavedSearches(userId: String): Flow<List<SavedSearch>> =
            dao
                .observeForUser(userId)
                .map { rows -> rows.map { it.toDomain() } }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override suspend fun savedSearch(
            userId: String,
            id: Long,
        ): SavedSearch? = withContext(ioDispatcher) { dao.find(id, userId)?.toDomain() }

        override suspend fun create(
            userId: String,
            search: NewSavedSearch,
        ): Long {
            val name = cleanName(search.name)
            require(name.isNotEmpty()) { "A saved search needs a name" }
            return withContext(ioDispatcher) {
                dao.insertWithSeen(
                    SavedSearchEntity(
                        userId = userId,
                        name = name,
                        query = search.query.trim(),
                        filterJson = SavedFilterJson.encode(search.filter),
                        alertsEnabled = search.alertsEnabled,
                        createdAtMillis = clock.now().toEpochMilli(),
                        lastNotifiedAtMillis = null,
                        regionId = search.regionId,
                        alertFrequency = search.frequency.id,
                        lastCheckedAtMillis = clock.now().toEpochMilli(),
                    ),
                    search.seenIds,
                )
            }
        }

        override suspend fun rename(
            userId: String,
            id: Long,
            name: String,
        ): Boolean {
            val clean = cleanName(name)
            if (clean.isEmpty()) return false
            return withContext(ioDispatcher) { dao.rename(id, userId, clean) > 0 }
        }

        override suspend fun setAlertsEnabled(
            userId: String,
            id: Long,
            enabled: Boolean,
        ): Boolean = withContext(ioDispatcher) { dao.setAlertsEnabledFor(id, userId, enabled) > 0 }

        override suspend fun setFrequency(
            userId: String,
            id: Long,
            frequency: AlertFrequency,
        ): Boolean = withContext(ioDispatcher) { dao.setFrequency(id, userId, frequency.id) > 0 }

        override suspend fun delete(
            userId: String,
            id: Long,
        ): Boolean = withContext(ioDispatcher) { dao.deleteWithSeen(id, userId) }

        override suspend fun alertingSearches(userId: String): List<SavedSearch> =
            withContext(ioDispatcher) { dao.withAlertsEnabledFor(userId).map { it.toDomain() } }

        override suspend fun seenIds(id: Long): Set<String> = withContext(ioDispatcher) { dao.seenIds(id).toSet() }

        override suspend fun recordCheck(
            id: Long,
            newlySeen: Set<String>,
            checkedAt: Instant,
            notified: Boolean,
        ) {
            withContext(ioDispatcher) {
                dao.recordCheck(id, newlySeen, checkedAt.toEpochMilli(), notified)
            }
        }

        private fun cleanName(name: String): String =
            name
                .trim()
                .replace(WHITESPACE, " ")
                .take(SavedSearch.MAX_NAME_LENGTH)

        private companion object {
            val WHITESPACE = Regex("\\s+")
        }
    }

@Singleton
class RoomPriceWatchRepository
    @Inject
    constructor(
        private val dao: PriceWatchDao,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PriceWatchRepository {
        override suspend fun baselines(userId: String): Map<String, Long> =
            withContext(ioDispatcher) { dao.forUser(userId).associate { it.propertyId to it.rentAmount } }

        override suspend fun replace(
            userId: String,
            rents: Map<String, Long>,
        ) {
            withContext(ioDispatcher) {
                dao.replace(userId, rents.map { (id, rent) -> PriceWatchEntity(userId, id, rent) })
            }
        }
    }

internal fun SavedSearchEntity.toDomain(): SavedSearch =
    SavedSearch(
        id = id,
        userId = userId,
        name = name,
        query = query,
        regionId = regionId,
        filter = SavedFilterJson.decode(filterJson),
        alertsEnabled = alertsEnabled,
        frequency = AlertFrequency.fromId(alertFrequency),
        createdAt = Instant.ofEpochMilli(createdAtMillis),
        lastCheckedAt = lastCheckedAtMillis?.let(Instant::ofEpochMilli),
    )
