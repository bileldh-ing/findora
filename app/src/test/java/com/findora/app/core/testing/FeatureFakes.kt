package com.findora.app.core.testing

import androidx.paging.PagingData
import com.findora.app.core.data.local.toDomain
import com.findora.app.core.domain.model.DataError
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SearchHistoryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant

/** The bundled seed listings as domain models (64 homes in four regions). */
val seedProperties: List<Property> by lazy { seedListings.indices.map { seedEntity(it).toDomain() } }

/** In-memory listings: applies the type and text parts of a filter, like the SQL feed does. */
class FakePropertyRepository(
    private val listings: List<Property> = seedProperties,
) : PropertyRepository {
    val requestedFilters = mutableListOf<Pair<String, PropertyFilter>>()
    val refreshCalls = mutableListOf<Pair<String, Boolean>>()
    var refreshResult: RefreshResult = RefreshResult.Fetched(listings.size)

    fun matching(
        regionId: String,
        filter: PropertyFilter,
    ): List<Property> =
        listings.filter { property ->
            property.regionId == regionId &&
                (filter.type == null || property.type == filter.type) &&
                (
                    filter.query.isBlank() ||
                        property.title.contains(filter.query.trim(), ignoreCase = true) ||
                        property.locality.contains(filter.query.trim(), ignoreCase = true)
                ) &&
                (!filter.verifiedOnly || property.verified)
        }

    override fun observeProperties(
        regionId: String,
        filter: PropertyFilter,
    ): Flow<List<Property>> = flowOf(matching(regionId, filter))

    override fun pagedProperties(
        regionId: String,
        filter: PropertyFilter,
    ): Flow<PagingData<Property>> {
        requestedFilters += regionId to filter
        return flowOf(PagingData.from(matching(regionId, filter)))
    }

    override fun observeProperty(id: String): Flow<Property?> = flowOf(listings.firstOrNull { it.id == id })

    override fun observeBounds(regionId: String): Flow<ListingBounds?> {
        val inRegion = listings.filter { it.regionId == regionId }
        return flowOf(
            if (inRegion.isEmpty()) {
                null
            } else {
                ListingBounds(
                    minRent = inRegion.minOf { it.rent.amount },
                    maxRent = inRegion.maxOf { it.rent.amount },
                    minAreaSqm = inRegion.minOf { it.areaSqm },
                    maxAreaSqm = inRegion.maxOf { it.areaSqm },
                    count = inRegion.size,
                )
            },
        )
    }

    override suspend fun suggestions(
        regionId: String,
        query: String,
        limit: Int,
    ): List<SearchSuggestion> {
        val text = query.trim()
        val inRegion = listings.filter { it.regionId == regionId }
        val localities =
            inRegion.map { it.locality }.distinct().filter { it.startsWith(text, ignoreCase = true) }.map {
                SearchSuggestion(it, SearchSuggestion.Kind.Locality)
            }
        val titles =
            inRegion.map { it.title }.filter { it.contains(text, ignoreCase = true) }.map {
                SearchSuggestion(it, SearchSuggestion.Kind.Property)
            }
        return (localities + titles).take(limit)
    }

    override suspend fun refresh(
        regionId: String,
        force: Boolean,
    ): RefreshResult {
        refreshCalls += regionId to force
        return refreshResult
    }

    companion object {
        fun failed(cached: Int): RefreshResult = RefreshResult.Failed(DataError.Network, cached)
    }
}

class FakeFavoritesRepository : FavoritesRepository {
    private val byUser = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    override fun observeFavoriteIds(userId: String): Flow<Set<String>> = byUser.map { it[userId].orEmpty() }

    override fun observeFavorites(userId: String): Flow<List<Property>> =
        byUser.map { map -> map[userId].orEmpty().mapNotNull { id -> seedProperties.firstOrNull { it.id == id } } }

    override suspend fun toggle(
        userId: String,
        propertyId: String,
    ): Boolean {
        var nowFavorite = false
        byUser.update { map ->
            val current = map[userId].orEmpty()
            nowFavorite = propertyId !in current
            map + (userId to if (nowFavorite) current + propertyId else current - propertyId)
        }
        return nowFavorite
    }
}

class FakeSearchHistoryRepository : SearchHistoryRepository {
    private val byUser = MutableStateFlow<Map<String, List<RecentSearch>>>(emptyMap())
    private var tick = 0L

    override fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<RecentSearch>> = byUser.map { it[userId].orEmpty().take(limit) }

    override suspend fun record(
        userId: String,
        query: String,
    ) {
        val text = query.trim()
        if (text.isEmpty()) return
        byUser.update { map ->
            val rest = map[userId].orEmpty().filterNot { it.query.equals(text, ignoreCase = true) }
            map + (userId to listOf(RecentSearch(text, Instant.ofEpochSecond(++tick))) + rest)
        }
    }

    override suspend fun delete(
        userId: String,
        query: String,
    ) = byUser.update { map ->
        map + (userId to map[userId].orEmpty().filterNot { it.query.equals(query.trim(), ignoreCase = true) })
    }

    override suspend fun clear(userId: String) = byUser.update { it - userId }
}
