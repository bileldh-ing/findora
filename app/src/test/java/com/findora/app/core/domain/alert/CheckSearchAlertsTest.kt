package com.findora.app.core.domain.alert

import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakePriceWatchRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSavedSearchRepository
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.RecordingAlertScheduler
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CheckSearchAlertsTest {
    private val clock = MutableClock(Instant.parse("2026-10-24T09:00:00Z"))
    private val savedSearches = FakeSavedSearchRepository { clock.now() }
    private val prices = FakePriceWatchRepository()
    private val favorites = FakeFavoritesRepository()
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val houses = PropertyFilter(type = PropertyType.House)

    /** A new house listed after the search was saved. */
    private val newHouse: Property =
        kerala.first { it.type == PropertyType.House }.let {
            it.copy(id = "${it.id}-n21", title = "${it.title} · 3B", createdAt = clock.now())
        }

    private fun check(listings: List<Property>) =
        CheckSearchAlerts(savedSearches, prices, FakePropertyRepository(listings), favorites, clock)

    @Test
    fun savingRemembersCurrentMatchesSoOnlyLaterHomesAlertOnce() =
        runTest {
            val save = SaveSearch(savedSearches, FakePropertyRepository(kerala), RecordingAlertScheduler())
            val id = save("u", "Houses", "", "kerala", houses)
            savedSearches.setLastChecked(id, null)
            assertTrue(check(kerala)("u", "kerala", onRefresh = false).searchAlerts.isEmpty())

            savedSearches.setLastChecked(id, null)
            val report = check(kerala + newHouse)("u", "kerala", onRefresh = false)
            assertEquals(
                listOf(newHouse.id),
                report.searchAlerts
                    .single()
                    .newListings
                    .map { it.id },
            )

            // Running again (even when due) never repeats an alert.
            savedSearches.setLastChecked(id, null)
            assertTrue(check(kerala + newHouse)("u", "kerala", onRefresh = false).isEmpty)
        }

    @Test
    fun refreshRunsOnlyCheckInstantSearchesAndDailyOnesWaitADay() =
        runTest {
            val daily = savedSearches.create("u", NewSavedSearch("Daily", "", "kerala", houses, emptySet()))
            val instant =
                savedSearches.create(
                    "u",
                    NewSavedSearch("Instant", "", "kerala", houses, emptySet(), frequency = AlertFrequency.Instant),
                )
            val onRefresh = check(kerala + newHouse)("u", "kerala", onRefresh = true)
            assertEquals(listOf(instant), onRefresh.searchAlerts.map { it.search.id })

            // The daily search was saved "now": not due for a day.
            assertTrue(check(kerala + newHouse)("u", "kerala", onRefresh = false).searchAlerts.isEmpty())
            clock.advance(Duration.ofHours(24))
            val nextDay = check(kerala + newHouse)("u", "kerala", onRefresh = false)
            assertEquals(listOf(daily), nextDay.searchAlerts.map { it.search.id })
        }

    @Test
    fun searchesOfOtherUsersAndDisabledAlertsAreSkipped() =
        runTest {
            savedSearches.create("other", NewSavedSearch("Theirs", "", "kerala", houses, emptySet()))
            val off = savedSearches.create("u", NewSavedSearch("Off", "", "kerala", houses, emptySet()))
            savedSearches.setAlertsEnabled("u", off, false)
            clock.advance(Duration.ofDays(2))
            assertTrue(check(kerala + newHouse)("u", "kerala", onRefresh = false).isEmpty)
        }

    @Test
    fun aFavouriteThatGotCheaperIsReportedOnce() =
        runTest {
            val home = kerala.first()
            favorites.toggle("u", home.id)
            // First check records the baseline.
            assertTrue(check(kerala)("u", "kerala", onRefresh = false).priceDrops.isEmpty())
            val cheaper = home.copy(rent = Money(home.rent.amount - 500, home.rent.currency))
            val listings = listOf(cheaper) + kerala.drop(1)
            val favoritesNow = FakeFavoritesRepository(listings).apply { toggle("u", home.id) }
            val withCheaper =
                CheckSearchAlerts(savedSearches, prices, FakePropertyRepository(listings), favoritesNow, clock)
            val drop = withCheaper("u", "kerala", onRefresh = false).priceDrops.single()
            assertEquals(home.rent.amount, drop.previous.amount)
            assertEquals(cheaper.rent, drop.current)
            assertTrue(withCheaper("u", "kerala", onRefresh = false).priceDrops.isEmpty())
        }

    @Test
    fun regionsToRefreshCoverEverySearchAndTheCurrentRegion() =
        runTest {
            savedSearches.create("u", NewSavedSearch("Tunis", "", "tunis", houses, emptySet()))
            savedSearches.create("u", NewSavedSearch("Old", "", "", houses, emptySet()))
            assertEquals(setOf("tunis", "kerala"), check(kerala).regionsToRefresh("u", "kerala"))
        }
}
