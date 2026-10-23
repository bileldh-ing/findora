package com.findora.app.core.domain.owner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class OwnerStatsCalculatorTest {
    private val zone = ZoneId.of("Africa/Tunis")
    private val today = LocalDate.of(2026, 10, 23)

    private fun at(
        date: String,
        time: String = "12:00",
    ): Instant =
        LocalDate
            .parse(date)
            .atTime(java.time.LocalTime.parse(time))
            .atZone(zone)
            .toInstant()

    private fun signal(
        property: String,
        user: String,
        date: String,
        time: String = "12:00",
    ) = ListingSignal(property, user, at(date, time))

    @Test
    fun countsOtherUsersViewsSavesAndEnquiriesPerListing() {
        val signals =
            OwnerSignals(
                views =
                    listOf(
                        signal("own-1", "u2", "2026-10-23"),
                        signal("own-1", "u2", "2026-10-22"),
                        signal("own-1", "owner", "2026-10-23"),
                        signal("own-2", "u3", "2026-10-20"),
                        signal("market-1", "u2", "2026-10-23"),
                    ),
                saves =
                    listOf(
                        signal("own-1", "u2", "2026-10-23"),
                        signal("own-1", "u2", "2026-10-23"),
                        signal("own-1", "owner", "2026-10-23"),
                    ),
                chats = listOf(signal("own-2", "u3", "2026-10-21")),
                viewingRequests = listOf(signal("own-2", "u3", "2026-10-21"), signal("own-2", "u4", "2026-10-22")),
            )
        val stats = OwnerStatsCalculator.calculate(listOf("own-1", "own-2"), "owner", signals, today, zone)
        assertEquals(ListingStats(views = 2, saves = 1, enquiries = 0), stats.perListing["own-1"])
        assertEquals(ListingStats(views = 1, saves = 0, enquiries = 3), stats.perListing["own-2"])
        assertEquals(ListingStats(views = 3, saves = 1, enquiries = 3), stats.totals)
    }

    @Test
    fun theChartHasFourteenDaysEndingTodayInTheUsersZone() {
        val signals =
            OwnerSignals(
                views =
                    listOf(
                        signal("own-1", "u2", "2026-10-23", "00:30"),
                        signal("own-1", "u3", "2026-10-23", "23:30"),
                        signal("own-1", "u2", "2026-10-10"),
                        // Older than the window: counted in totals, not in the chart.
                        signal("own-1", "u2", "2026-10-09"),
                    ),
            )
        val stats = OwnerStatsCalculator.calculate(listOf("own-1"), "owner", signals, today, zone)
        assertEquals(OwnerStatsCalculator.CHART_DAYS, stats.dailyViews.size)
        assertEquals(LocalDate.of(2026, 10, 10), stats.dailyViews.first().date)
        assertEquals(today, stats.dailyViews.last().date)
        assertEquals(2, stats.dailyViews.last().count)
        assertEquals(1, stats.dailyViews.first().count)
        assertEquals(3, stats.dailyViews.sumOf { it.count })
        assertEquals(4, stats.totals.views)
    }

    @Test
    fun noListingsMeansZeroesAndAnEmptyChart() {
        val stats = OwnerStatsCalculator.calculate(emptyList(), "owner", OwnerSignals(), today, zone)
        assertEquals(ListingStats(), stats.totals)
        assertEquals(0, stats.dailyViews.sumOf { it.count })
        assertEquals(emptyMap<String, ListingStats>(), stats.perListing)
    }
}
