package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.RefreshResult
import kotlinx.coroutines.flow.Flow

/** Listings, served from the local cache (single source of truth) and refreshed from the remote source. */
interface PropertyRepository {
    /** Cached listings of [regionId] matching [filter], newest first. Emits again whenever the cache changes. */
    fun observeProperties(
        regionId: String,
        filter: PropertyFilter = PropertyFilter(),
    ): Flow<List<Property>>

    fun observeProperty(id: String): Flow<Property?>

    /** Refreshes [regionId] from the remote source unless the cache is still fresh (or [force] is set). */
    suspend fun refresh(
        regionId: String,
        force: Boolean = false,
    ): RefreshResult
}
