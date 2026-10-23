package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class DreamMatchDeckTest {
    private val now: Instant = Instant.ofEpochSecond(1_790_000_000)
    private val kerala = seedProperties.filter { it.regionId == "kerala" }
    private val profile =
        PreferenceProfile(
            budget = BudgetRange(null, 25_000, Currency.INR),
            minBedrooms = 2,
            mustHaves = setOf(MustHave.Parking),
        )

    @Test
    fun withoutAProfileTheDeckFollowsTheRecommendedOrder() {
        val deck = DreamMatchDeck.order(kerala, excludedIds = emptySet(), context = null)
        assertEquals(kerala.size, deck.size)
        assertTrue(deck.all { it.matchPercent == null })
        deck.zipWithNext().forEach { (a, b) ->
            val pa = a.property
            val pb = b.property
            val ordered =
                compareByDescending<com.findora.app.core.domain.model.Property> { it.verified }
                    .thenByDescending { it.rating }
                    .thenByDescending { it.reviewsCount }
                    .thenBy { it.id }
                    .compare(pa, pb) <= 0
            assertTrue(ordered)
        }
    }

    @Test
    fun withAProfileTheDeckIsBestMatchFirstAndLeavesOutExcludedHomes() {
        val context = MatchContext(profile, BehaviorAffinity.None, now)
        val excluded = setOf(kerala.first { it.parking }.id)
        val deck = DreamMatchDeck.order(kerala, excluded, context)
        val expected =
            BestMatchRanking.rank(
                kerala,
                com.findora.app.core.domain.model
                    .PropertyFilter(),
                context,
            )
        assertEquals(expected.map { it.id } - excluded, deck.map { it.property.id })
        assertTrue(deck.all { it.property.parking })
        assertFalse(deck.any { it.property.id in excluded })
        deck.forEach { assertEquals(context.score(it.property).score, it.matchPercent) }
        assertNull(DreamMatchDeck.order(emptyList(), emptySet(), context).firstOrNull())
    }

    @Test
    fun skippingInTheDeckLowersTheAffinityOfThatHomeAndSimilarOnes() {
        val skipped = kerala.first()
        val similar = kerala.first { it.id != skipped.id && it.type == skipped.type }
        val byId = kerala.associateBy { it.id }::get
        val events = listOf(InteractionEvent("u", skipped.id, InteractionType.DeckSkipped, now))
        val affinity = BehaviorAffinity.from(events, byId, now)
        assertTrue(affinity.hasSignal)
        assertTrue(affinity.signalFor(skipped) < 0.0)
        assertTrue(affinity.signalFor(similar) < 0.0)
        // A skip weighs less than an explicit dismissal and a save more than nothing.
        val dismissed =
            BehaviorAffinity.from(listOf(InteractionEvent("u", skipped.id, InteractionType.Dismissed, now)), byId, now)
        assertTrue(dismissed.signalFor(skipped) < affinity.signalFor(skipped))
        val saved =
            BehaviorAffinity.from(listOf(InteractionEvent("u", skipped.id, InteractionType.DeckSaved, now)), byId, now)
        assertTrue(saved.signalFor(skipped) > 0.0)
        // And the learned-behaviour factor of the match score drops with the skip.
        val neutral = MatchScoreCalculator.score(skipped, profile, BehaviorAffinity.None, now)
        val afterSkip = MatchScoreCalculator.score(skipped, profile, affinity, now)
        assertTrue(afterSkip.score <= neutral.score)
        val behaviour = { result: MatchResult ->
            result.reasons.filter { it.factor == MatchFactor.LearnedBehavior }.sumOf { it.points }
        }
        assertTrue(behaviour(afterSkip) < behaviour(neutral))
    }
}
