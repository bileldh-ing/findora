package com.findora.app.core.domain.repository

import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.SavedSearch
import kotlinx.coroutines.flow.Flow
import java.time.Instant

/** A saved search to create; [seenIds] are the listings that already matched (they never alert). */
data class NewSavedSearch(
    val name: String,
    val query: String,
    val regionId: String,
    val filter: PropertyFilter,
    val seenIds: Set<String>,
    val alertsEnabled: Boolean = true,
    val frequency: AlertFrequency = AlertFrequency.Daily,
)

/** Saved searches per user id, with the listing ids each one has already reported. Every call is user-scoped. */
interface SavedSearchRepository {
    /** Newest first. */
    fun observeSavedSearches(userId: String): Flow<List<SavedSearch>>

    /** The search when it exists and belongs to [userId]; null otherwise (a link can never open another user's). */
    suspend fun savedSearch(
        userId: String,
        id: Long,
    ): SavedSearch?

    suspend fun create(
        userId: String,
        search: NewSavedSearch,
    ): Long

    suspend fun rename(
        userId: String,
        id: Long,
        name: String,
    ): Boolean

    suspend fun setAlertsEnabled(
        userId: String,
        id: Long,
        enabled: Boolean,
    ): Boolean

    suspend fun setFrequency(
        userId: String,
        id: Long,
        frequency: AlertFrequency,
    ): Boolean

    suspend fun delete(
        userId: String,
        id: Long,
    ): Boolean

    /** [userId]'s searches with alerts switched on. */
    suspend fun alertingSearches(userId: String): List<SavedSearch>

    /** Listing ids already reported (or present when the search was saved) for search [id]. */
    suspend fun seenIds(id: Long): Set<String>

    /** Adds [newlySeen] to the search's seen ids and stamps the check (and alert, when [notified]) atomically. */
    suspend fun recordCheck(
        id: Long,
        newlySeen: Set<String>,
        checkedAt: Instant,
        notified: Boolean = false,
    )
}

/** The rent each favourite had when it was last checked, so a price drop is reported once. */
interface PriceWatchRepository {
    /** Listing id to the last rent seen, in the listing's own currency. */
    suspend fun baselines(userId: String): Map<String, Long>

    /** Replaces [userId]'s baselines with [rents] (favourites that were removed are forgotten). */
    suspend fun replace(
        userId: String,
        rents: Map<String, Long>,
    )
}

/** Hears about successful listing refreshes (instant saved-search alerts run then). */
fun interface ListingsRefreshListener {
    fun onRegionRefreshed(regionId: String)

    companion object {
        val None = ListingsRefreshListener { }
    }
}

/** Keeps the periodic saved-search and price-drop check scheduled. Implemented with WorkManager. */
interface SearchAlertScheduler {
    /** Schedules the periodic check if it is not scheduled yet (idempotent). */
    fun ensureScheduled()
}

/** Keeps the "Findora picks" home-screen widget current. Implemented with WorkManager and Glance. */
interface HomeWidgetRefresher {
    /** Schedules the periodic widget refresh while a widget is placed (idempotent). */
    fun ensureScheduled()

    /** Redraws placed widgets soon (favourites or viewings changed). Does nothing when none is placed. */
    fun requestRefresh()
}
