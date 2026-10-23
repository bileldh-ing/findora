package com.findora.app.core.domain.owner

import androidx.compose.runtime.Immutable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** One thing another user did on an owner's listing: a view, a save, a chat thread or a viewing request. */
data class ListingSignal(
    val propertyId: String,
    val userId: String,
    val at: Instant,
)

/** Raw signals on an owner's listings, as read from interaction events, favourites, chats and viewings. */
data class OwnerSignals(
    val views: List<ListingSignal> = emptyList(),
    val saves: List<ListingSignal> = emptyList(),
    val chats: List<ListingSignal> = emptyList(),
    val viewingRequests: List<ListingSignal> = emptyList(),
)

@Immutable
data class ListingStats(
    val views: Int = 0,
    val saves: Int = 0,
    /** Chat threads opened about the listing plus viewing requests. */
    val enquiries: Int = 0,
)

/** Views on one calendar day (in the user's zone). */
@Immutable
data class DailyCount(
    val date: LocalDate,
    val count: Int,
)

@Immutable
data class OwnerDashboardStats(
    val perListing: Map<String, ListingStats>,
    val totals: ListingStats,
    /** Exactly [OwnerStatsCalculator.CHART_DAYS] days, oldest first, ending today. */
    val dailyViews: List<DailyCount>,
)

/**
 * Derives the owner dashboard's numbers from [OwnerSignals]. Pure and deterministic: the owner's own activity never
 * counts, a save is one per user and listing (current favourites), a view is every recorded view, and enquiries are
 * chat threads plus viewing requests. Signals on listings outside [listingIds] are ignored.
 */
object OwnerStatsCalculator {
    const val CHART_DAYS = 14

    fun calculate(
        listingIds: Collection<String>,
        ownerId: String,
        signals: OwnerSignals,
        today: LocalDate,
        zone: ZoneId,
    ): OwnerDashboardStats {
        val ids = listingIds.toSet()

        fun List<ListingSignal>.relevant() = filter { it.propertyId in ids && it.userId != ownerId }
        val views = signals.views.relevant()
        val saves = signals.saves.relevant().distinctBy { it.propertyId to it.userId }
        val chats = signals.chats.relevant().distinctBy { it.propertyId to it.userId }
        val requests = signals.viewingRequests.relevant()
        val perListing =
            ids.associateWith { id ->
                ListingStats(
                    views = views.count { it.propertyId == id },
                    saves = saves.count { it.propertyId == id },
                    enquiries = chats.count { it.propertyId == id } + requests.count { it.propertyId == id },
                )
            }
        val totals =
            ListingStats(
                views = perListing.values.sumOf { it.views },
                saves = perListing.values.sumOf { it.saves },
                enquiries = perListing.values.sumOf { it.enquiries },
            )
        val first = today.minusDays((CHART_DAYS - 1).toLong())
        val byDay = views.groupingBy { it.at.atZone(zone).toLocalDate() }.eachCount()
        val daily =
            (0 until CHART_DAYS).map { offset -> first.plusDays(offset.toLong()) }.map {
                DailyCount(
                    it,
                    byDay[it] ?: 0,
                )
            }
        return OwnerDashboardStats(perListing = perListing, totals = totals, dailyViews = daily)
    }
}
