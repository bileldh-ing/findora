package com.findora.app.core.domain.alert

import com.findora.app.core.common.AppClock
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.core.domain.repository.FavoritesRepository
import com.findora.app.core.domain.repository.PriceWatchRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.domain.repository.SavedSearchRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/** One saved search that found new listings. */
data class SearchAlert(
    val search: SavedSearch,
    val newListings: List<Property>,
)

/** What one alert check found for a user. */
data class AlertReport(
    val searchAlerts: List<SearchAlert>,
    val priceDrops: List<PriceDrop>,
) {
    val isEmpty: Boolean get() = searchAlerts.isEmpty() && priceDrops.isEmpty()
}

/**
 * Checks [userId]'s due saved searches against the cached listings and their favourites against the rents seen
 * last time, and remembers what it reported, so running it twice never alerts twice. Reads the local cache only:
 * the caller refreshes it first when it wants fresh listings.
 */
class CheckSearchAlerts
    @Inject
    constructor(
        private val savedSearches: SavedSearchRepository,
        private val priceWatch: PriceWatchRepository,
        private val properties: PropertyRepository,
        private val favorites: FavoritesRepository,
        private val clock: AppClock,
    ) {
        /** The regions [userId]'s alerting searches look at ([fallbackRegionId] for searches saved without one). */
        suspend fun regionsToRefresh(
            userId: String,
            fallbackRegionId: String,
        ): Set<String> =
            savedSearches
                .alertingSearches(userId)
                .mapTo(LinkedHashSet()) { it.regionId.ifBlank { fallbackRegionId } }
                .apply { add(fallbackRegionId) }

        suspend operator fun invoke(
            userId: String,
            fallbackRegionId: String,
            onRefresh: Boolean,
        ): AlertReport {
            val now = clock.now()
            val listingsByRegion = HashMap<String, List<Property>>()
            val alerts =
                savedSearches
                    .alertingSearches(userId)
                    .filter { AlertDiff.isDue(it, now, onRefresh) }
                    .mapNotNull { search ->
                        val region = search.regionId.ifBlank { fallbackRegionId }
                        val listings =
                            listingsByRegion.getOrPut(region) { properties.observeProperties(region).first() }
                        val seen = savedSearches.seenIds(search.id)
                        val diff = AlertDiff.newMatches(search.id, search.filter, listings, seen)
                        val fresh = diff.newListings
                        savedSearches.recordCheck(search.id, fresh.mapTo(HashSet()) { it.id }, now, fresh.isNotEmpty())
                        if (fresh.isEmpty()) null else SearchAlert(search, fresh)
                    }
            val homes = favorites.observeFavorites(userId).first()
            val (drops, baselines) = AlertDiff.priceDrops(homes, priceWatch.baselines(userId))
            priceWatch.replace(userId, baselines)
            return AlertReport(alerts, drops)
        }
    }
