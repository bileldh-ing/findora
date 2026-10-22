package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.data.local.toEntity
import com.findora.app.core.data.remote.RemoteDataSource
import com.findora.app.core.data.remote.RemoteException
import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.di.IoDispatcher
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.repository.PropertyRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

/** Cache freshness window for listings. */
data class CachePolicy(
    val maxAge: Duration = Duration.ofMinutes(DEFAULT_MAX_AGE_MINUTES),
    val pageSize: Int = DEFAULT_PAGE_SIZE,
) {
    companion object {
        const val DEFAULT_MAX_AGE_MINUTES = 15L
        const val DEFAULT_PAGE_SIZE = 20
    }
}

/**
 * Room is the single source of truth: readers observe the database, [refresh] writes into it.
 * A failed refresh never clears the cache, so the app keeps working offline.
 */
@Singleton
class OfflineFirstPropertyRepository
    @Inject
    constructor(
        private val remote: RemoteDataSource,
        private val dao: PropertyDao,
        private val clock: AppClock,
        private val policy: CachePolicy,
        @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PropertyRepository {
        override fun observeProperties(
            regionId: String,
            filter: PropertyFilter,
        ): Flow<List<Property>> =
            dao
                .observeByRegion(
                    regionId = regionId,
                    type = filter.type?.id,
                    minRent = filter.minRent,
                    maxRent = filter.maxRent,
                    minBedrooms = filter.minBedrooms,
                    verifiedOnly = filter.verifiedOnly,
                ).distinctUntilChanged()
                .map { rows -> rows.map { it.toDomain() } }
                .flowOn(ioDispatcher)

        override fun observeProperty(id: String): Flow<Property?> =
            dao
                .observeById(id)
                .distinctUntilChanged()
                .map { it?.toDomain() }
                .flowOn(ioDispatcher)

        override suspend fun refresh(
            regionId: String,
            force: Boolean,
        ): RefreshResult =
            withContext(ioDispatcher) {
                val now = clock.now()
                val oldest = dao.oldestFetchInRegion(regionId)
                val fresh = oldest != null && now.toEpochMilli() - oldest < policy.maxAge.toMillis()
                if (!force && fresh) {
                    return@withContext RefreshResult.UpToDate(dao.countInRegion(regionId))
                }
                try {
                    val listings = fetchAllPages(regionId)
                    val fetchedAt = now.toEpochMilli()
                    dao.replaceRegion(regionId, listings.map { it.toEntity(fetchedAt) })
                    RefreshResult.Fetched(listings.size)
                } catch (error: RemoteException) {
                    RefreshResult.Failed(error.error, dao.countInRegion(regionId))
                }
            }

        private suspend fun fetchAllPages(regionId: String): List<ListingDto> {
            val all = mutableListOf<ListingDto>()
            var page: Int? = 0
            while (page != null) {
                val result = remote.fetchListings(regionId, page, policy.pageSize)
                all += result.items
                page = result.nextPage
            }
            return all
        }
    }
