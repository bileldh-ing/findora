package com.findora.app.core.domain.alert

import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertDiffTest {
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val now = Instant.parse("2026-10-24T09:00:00Z")

    private fun search(
        frequency: AlertFrequency = AlertFrequency.Daily,
        lastChecked: Instant? = now.minus(Duration.ofDays(2)),
        alerts: Boolean = true,
    ) = SavedSearch(
        id = 1,
        userId = "u",
        name = "Houses",
        query = "",
        regionId = "kerala",
        filter = PropertyFilter(type = PropertyType.House),
        alertsEnabled = alerts,
        frequency = frequency,
        createdAt = now.minus(Duration.ofDays(3)),
        lastCheckedAt = lastChecked,
    )

    @Test
    fun onlyMatchingListingsThatWereNotSeenAreNewNewestFirst() {
        val houses = kerala.filter { it.type == PropertyType.House }
        val seen = houses.drop(2).map { it.id }.toSet()
        val result = AlertDiff.newMatches(1, PropertyFilter(type = PropertyType.House), kerala, seen)
        assertEquals(houses.take(2).map { it.id }.toSet(), result.newListings.map { it.id }.toSet())
        assertEquals(result.newListings.sortedByDescending { it.createdAt }, result.newListings)
        assertEquals(houses.map { it.id }.toSet(), result.matchingIds)
        assertTrue(result.newListings.all { it.type == PropertyType.House })
    }

    @Test
    fun aSecondCheckWithTheSameListingsFindsNothing() {
        val first = AlertDiff.newMatches(1, PropertyFilter(), kerala, emptySet())
        assertEquals(kerala.size, first.newListings.size)
        val second = AlertDiff.newMatches(1, PropertyFilter(), kerala, first.matchingIds)
        assertTrue(second.newListings.isEmpty())
    }

    @Test
    fun aListingThatLeftAndCameBackDoesNotAlertAgain() {
        val home = kerala.first()
        val seen = setOf(home.id)
        val withoutIt = AlertDiff.newMatches(1, PropertyFilter(), kerala.drop(1), seen)
        val withIt = AlertDiff.newMatches(1, PropertyFilter(), kerala, seen + withoutIt.matchingIds)
        assertTrue(withIt.newListings.isEmpty())
    }

    @Test
    fun dailySearchesAreDueOnceADayAndNeverOnARefresh() {
        assertTrue(AlertDiff.isDue(search(lastChecked = now.minus(Duration.ofHours(24))), now, onRefresh = false))
        // A periodic run a little early still counts.
        assertTrue(AlertDiff.isDue(search(lastChecked = now.minus(Duration.ofMinutes(23 * 60 + 30))), now, false))
        assertFalse(AlertDiff.isDue(search(lastChecked = now.minus(Duration.ofHours(6))), now, onRefresh = false))
        assertTrue(AlertDiff.isDue(search(lastChecked = null), now, onRefresh = false))
        assertFalse(AlertDiff.isDue(search(lastChecked = null), now, onRefresh = true))
    }

    @Test
    fun instantSearchesAreAlwaysDueAndDisabledOnesNever() {
        val instant = search(frequency = AlertFrequency.Instant, lastChecked = now)
        assertTrue(AlertDiff.isDue(instant, now, onRefresh = true))
        assertTrue(AlertDiff.isDue(instant, now, onRefresh = false))
        assertFalse(AlertDiff.isDue(instant.copy(alertsEnabled = false), now, onRefresh = true))
        assertFalse(AlertDiff.isDue(search(alerts = false), now, onRefresh = false))
    }

    @Test
    fun priceDropsCompareWithTheLastSeenRentOnly() {
        val (a, b, c) = kerala.take(3)
        val baselines = mapOf(a.id to a.rent.amount + 500, b.id to b.rent.amount - 500, "gone" to 1_000L)
        val (drops, next) = AlertDiff.priceDrops(listOf(a, b, c), baselines)
        assertEquals(listOf(a.id), drops.map { it.property.id })
        assertEquals(a.rent.amount + 500, drops.single().previous.amount)
        assertEquals(a.rent, drops.single().current)
        // New baselines: every favourite still listed at today's rent (c had none: no drop, now tracked).
        assertEquals(mapOf(a.id to a.rent.amount, b.id to b.rent.amount, c.id to c.rent.amount), next)
        assertTrue(AlertDiff.priceDrops(listOf(a, b, c), next).first.isEmpty())
    }
}
