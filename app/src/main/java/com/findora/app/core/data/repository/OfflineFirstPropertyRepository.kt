package com.findora.app.core.data.repository

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.local.PropertyQueryBuilder
import com.findora.app.core.data.local.dao.BoundsRow
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.data.local.dao.PropertySearchDao
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.data.local.toEntity
import com.findora.app.core.data.remote.RemoteDataSource
import com.findora.app.core.data.remote.RemoteException
import com.findora.app.core.data.seed.ListingDto
import com.findora.app.core.di.IoDispatcher
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.sqlite.db.SimpleSQLiteQuery
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.SearchSuggestion
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
        private val searchDao: PropertySearchDao,
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

        override fun pagedProperties(
            regionId: String,
            filter: PropertyFilter,
        ): Flow<PagingData<Property>> {
            val query = PropertyQueryBuilder.build(regionId, filter)
            return Pager(
                config = PagingConfig(pageSize = policy.pageSize, enablePlaceholders = false),
                pagingSourceFactory = { searchDao.pagingSource(SimpleSQLiteQuery(query.sql, query.args.toTypedArray())) },
            ).flow.map { page -> page.map { it.toDomain() } }
        }

        override fun observeBounds(regionId: String): Flow<ListingBounds?> =
            searchDao
                .observeBounds(regionId)
                .map { row -> row.toBounds() }
                .distinctUntilChanged()
                .flowOn(ioDispatcher)

        override suspend fun suggestions(
            regionId: String,
            query: String,
            limit: Int,
        ): List<SearchSuggestion> {
            val text = query.trim()
            if (text.isEmpty()) return emptyList()
            return withContext(ioDispatcher) {
                // Prefix matches read naturally for neighbourhoods; names match anywhere ("Haven" finds "Luna Haven").
                val escaped = PropertyQueryBuilder.escapeLike(text)
                val localities = searchDao.matchingLocalities(regionId, "$escaped%", limit)
                val titles = searchDao.matchingTitles(regionId, "%$escaped%", limit)
                (
                    localities.map { SearchSuggestion(it, SearchSuggestion.Kind.Locality) } +
                        titles.map { SearchSuggestion(it, SearchSuggestion.Kind.Property) }
                ).distinctBy { it.text.lowercase() }.take(limit)
            }
        }

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

        private fun BoundsRow.toBounds(): ListingBounds? {
            val rents = if (minRent != null && maxRent != null) minRent to maxRent else null
            val areas = if (minArea != null && maxArea != null) minArea to maxArea else null
            return if (count == 0 || rents == null || areas == null) {
                null
            } else {
                ListingBounds(rents.first, rents.second, areas.first, areas.second, count)
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
