package com.findora.app.core.domain.repository

import androidx.paging.PagingData
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.SearchSuggestion
import kotlinx.coroutines.flow.Flow

/**
 * Listings, served from the local cache (single source of truth) and refreshed from the remote source.
 * Paging types come from paging-common, a pure Kotlin artifact, so the domain stays free of Android.
 */
interface PropertyRepository {
    /** Cached listings of [regionId] matching [filter], newest first. Emits again whenever the cache changes. */
    fun observeProperties(
        regionId: String,
        filter: PropertyFilter = PropertyFilter(),
    ): Flow<List<Property>>

    /** The Home feed: [filter] (including text, amenities and sort) applied in SQL, paged from Room. */
    fun pagedProperties(
        regionId: String,
        filter: PropertyFilter,
    ): Flow<PagingData<Property>>

    fun observeProperty(id: String): Flow<Property?>

    /** Rent and area ranges of the cached listings in [regionId]; null while the cache is empty. */
    fun observeBounds(regionId: String): Flow<ListingBounds?>

    /** Neighbourhoods first, then listing names, matching [query] in [regionId]. */
    suspend fun suggestions(
        regionId: String,
        query: String,
        limit: Int = DEFAULT_SUGGESTIONS,
    ): List<SearchSuggestion>

    /** Refreshes [regionId] from the remote source unless the cache is still fresh (or [force] is set). */
    suspend fun refresh(
        regionId: String,
        force: Boolean = false,
    ): RefreshResult

    companion object {
        const val DEFAULT_SUGGESTIONS = 8
        const val PAGE_SIZE = 20
    }
}
