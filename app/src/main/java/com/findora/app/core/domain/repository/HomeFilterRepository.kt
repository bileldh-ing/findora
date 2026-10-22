package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.PropertyFilter
import kotlinx.coroutines.flow.StateFlow

/**
 * The filter the Home feed shows, shared by Home, Search, the filter sheet and Locations so each screen
 * edits one source of truth instead of passing results back through navigation.
 */
interface HomeFilterRepository {
    val filter: StateFlow<PropertyFilter>

    fun update(transform: (PropertyFilter) -> PropertyFilter)

    fun reset()
}
