package com.findora.app.core.domain.alert

import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.SavedSearch
import java.time.Duration
import java.time.Instant

/** What one check of a saved search found: the listings that newly match, newest first. */
data class NewMatches(
    val searchId: Long,
    val newListings: List<Property>,
    /** Every id that matches now (new or not), to be remembered as seen. */
    val matchingIds: Set<String>,
)

/** A favourite whose rent went down since it was last checked. */
data class PriceDrop(
    val property: Property,
    val previous: Money,
) {
    val current: Money get() = property.rent
}

/**
 * Pure alert rules, shared by the worker and the tests. A listing alerts once per saved search: once it is in the
 * seen set it never alerts again, even if it drops out of the results and comes back.
 */
object AlertDiff {
    /** A little slack so a periodic check that runs a few minutes early still counts as "a day later". */
    private val DAILY_SLACK: Duration = Duration.ofHours(1)
    val DAILY_INTERVAL: Duration = Duration.ofDays(1)

    /** Listings of [listings] matching [filter] whose ids are not in [seen]. */
    fun newMatches(
        searchId: Long,
        filter: PropertyFilter,
        listings: List<Property>,
        seen: Set<String>,
    ): NewMatches {
        val matching = listings.filter(filter::accepts)
        val fresh =
            matching
                .filterNot { it.id in seen }
                .sortedWith(compareByDescending<Property> { it.createdAt }.thenBy { it.id })
        return NewMatches(searchId, fresh, matching.mapTo(LinkedHashSet()) { it.id })
    }

    /**
     * Whether [search] is due. Instant searches are checked on every run (each periodic run and each refresh);
     * daily ones once a day. [onRefresh] runs (triggered by a listings refresh) only check instant searches.
     */
    fun isDue(
        search: SavedSearch,
        now: Instant,
        onRefresh: Boolean,
    ): Boolean {
        if (!search.alertsEnabled) return false
        return when (search.frequency) {
            AlertFrequency.Instant -> true
            AlertFrequency.Daily ->
                !onRefresh &&
                    search.lastCheckedAt.let { last ->
                        last == null || !now.isBefore(last.plus(DAILY_INTERVAL).minus(DAILY_SLACK))
                    }
        }
    }

    /**
     * Favourites cheaper than their baseline. A listing without a baseline (just saved) or priced in another
     * currency than before is not a drop; the returned map is the new baseline for every favourite still listed.
     */
    fun priceDrops(
        favorites: List<Property>,
        baselines: Map<String, Long>,
    ): Pair<List<PriceDrop>, Map<String, Long>> {
        val drops =
            favorites.mapNotNull { property ->
                val before = baselines[property.id] ?: return@mapNotNull null
                if (property.rent.amount < before) PriceDrop(property, property.rent.copy(amount = before)) else null
            }
        return drops to favorites.associate { it.id to it.rent.amount }
    }
}
