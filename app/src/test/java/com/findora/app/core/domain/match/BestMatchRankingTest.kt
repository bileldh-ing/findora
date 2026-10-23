package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class BestMatchRankingTest {
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val profile =
        PreferenceProfile(
            budget = BudgetRange(null, 25_000, Currency.INR),
            minBedrooms = 2,
            mustHaves = setOf(MustHave.Parking),
        )
    private val context = MatchContext(profile, BehaviorAffinity.None, now)

    @Test
    fun ordersByScoreDescendingAndDropsHomesMissingAMustHave() {
        val ranked = BestMatchRanking.rank(kerala, PropertyFilter(), context)
        val scores = ranked.map { context.score(it).score }
        assertEquals(scores.sortedDescending(), scores)
        assertTrue(ranked.all { it.parking })
        assertEquals(kerala.count { it.parking }, ranked.size)
    }

    @Test
    fun theFilterStillAppliesAndTiesAreBrokenById() {
        val filter = PropertyFilter(type = PropertyType.Apartment, verifiedOnly = true)
        val ranked = BestMatchRanking.rank(kerala, filter, context)
        assertTrue(ranked.all { it.type == PropertyType.Apartment && it.verified && it.parking })
        ranked.zipWithNext().forEach { (a, b) ->
            val sa = context.score(a).score
            val sb = context.score(b).score
            assertTrue(sa > sb || (sa == sb && a.id < b.id))
        }
        assertEquals(ranked, BestMatchRanking.rank(kerala.reversed(), filter, context))
    }

    @Test
    fun badgeScoresSkipExcludedHomes() {
        val scores = BestMatchRanking.scores(kerala, context)
        assertEquals(kerala.filter { it.parking }.map { it.id }.toSet(), scores.keys)
        assertTrue(scores.values.all { it in 0..100 })
    }

    @Test
    fun inMemoryFilterMatchesEveryRefinement() {
        val filter =
            PropertyFilter(
                query = "kochi",
                minRent = 5_000,
                maxRent = 60_000,
                minBedrooms = 1,
                minBathrooms = 1,
                furnishedOnly = true,
            )
        kerala.forEach { home ->
            val expected =
                (home.title.contains("kochi", true) || home.locality.contains("kochi", true)) &&
                    home.rent.amount in 5_000..60_000 &&
                    home.bedrooms >= 1 &&
                    home.bathrooms >= 1 &&
                    home.furnished
            assertEquals(home.id, expected, filter.accepts(home))
        }
    }
}
