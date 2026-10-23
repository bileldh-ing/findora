package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.random.Random

/**
 * Property-based style checks: each invariant is asserted over thousands of generated listings and profiles
 * (fixed seeds, so a failure always reproduces), plus a few worked examples.
 */
class MatchScoreCalculatorTest {
    private val now = Instant.parse("2026-10-01T10:00:00Z")
    private val kochi = seedProperties.filter { it.regionId == "kerala" }
    private val anchors =
        listOf(
            LocationAnchor("Kochi", GeoPoint(9.9816, 76.2999), LocationAnchor.Kind.Neighbourhood),
            LocationAnchor("Kakkanad", GeoPoint(10.0159, 76.3419), LocationAnchor.Kind.Neighbourhood),
            LocationAnchor("Edappally", GeoPoint(10.0261, 76.3125), LocationAnchor.Kind.Commute),
        )

    private fun Random.listing(): Property {
        val base = kochi[nextInt(kochi.size)]
        return base.copy(
            id = "gen-${nextInt(1_000_000)}",
            type = PropertyType.entries.random(this),
            rent = Money(nextLong(3_000, 120_000), Currency.INR),
            bedrooms = nextInt(0, 6),
            bathrooms = nextInt(1, 5),
            furnished = nextBoolean(),
            petFriendly = nextBoolean(),
            parking = nextBoolean(),
            verified = nextBoolean(),
            rating = 3.8 + nextDouble() * 1.2,
            amenities = Amenity.entries.filter { nextInt(3) == 0 },
            tags = base.tags.filter { nextBoolean() },
            location = GeoPoint(9.9 + nextDouble() * 0.2, 76.2 + nextDouble() * 0.2),
            createdAt = now.minus(Duration.ofDays(nextLong(0, 400))),
        )
    }

    private fun Random.profile(): PreferenceProfile {
        val max = nextLong(5_000, 100_000)
        return PreferenceProfile(
            budget =
                if (nextInt(4) ==
                    0
                ) {
                    null
                } else {
                    BudgetRange(if (nextBoolean()) nextLong(0, max) else null, max, Currency.INR)
                },
            minBedrooms = if (nextBoolean()) nextInt(0, 5) else null,
            minBathrooms = if (nextBoolean()) nextInt(0, 4) else null,
            propertyTypes = PropertyType.entries.filter { nextInt(3) == 0 }.toSet(),
            anchors = anchors.filter { nextBoolean() },
            lifestyle = LifestyleTag.entries.filter { nextInt(3) == 0 }.toSet(),
            mustHaves = MustHave.entries.filter { nextInt(5) == 0 }.toSet(),
        )
    }

    private fun Random.events(listings: List<Property>): List<InteractionEvent> =
        List(nextInt(0, 12)) {
            InteractionEvent(
                userId = "user-1",
                propertyId = listings[nextInt(listings.size)].id,
                type = InteractionType.entries.random(this),
                occurredAt = now.minus(Duration.ofHours(nextLong(0, 24 * 60))),
                dwellMillis = nextLong(0, 60_000),
            )
        }

    @Test
    fun weightsAddUpToOneHundred() {
        assertEquals(100, MatchFactor.entries.sumOf { it.weight })
    }

    @Test
    fun scoreIsAlwaysWithinZeroAndOneHundredAndExplainedByEveryFactor() {
        val random = Random(1)
        repeat(3_000) {
            val listing = random.listing()
            val pool = List(5) { random.listing() } + listing
            val affinity = BehaviorAffinity.from(random.events(pool), pool.associateBy { it.id }::get, now)
            val result = MatchScoreCalculator.score(listing, random.profile(), affinity, now)
            assertTrue(result.score in 0..100)
            assertEquals(MatchFactor.entries, result.reasons.map { it.factor })
            result.reasons.forEach { reason ->
                assertTrue("${reason.factor} ${reason.points}", reason.points in 0.0..reason.maxPoints.toDouble())
                assertEquals(reason.positive, reason.points >= reason.maxPoints / 2.0)
            }
            if (!result.excluded) {
                assertTrue(abs(result.reasons.sumOf { it.points } - result.score) <= 0.5 + 1e-9)
            }
        }
    }

    @Test
    fun aCheaperRentWithinBudgetNeverLowersBudgetFitOrTheScore() {
        val random = Random(2)
        repeat(3_000) {
            val max = random.nextLong(5_000, 100_000)
            val min = if (random.nextBoolean()) random.nextLong(0, max) else null
            val budget = BudgetRange(min, max, Currency.INR)
            val floor = min ?: 0
            val pricier = random.nextLong(floor, max + 1)
            val cheaper = random.nextLong(floor, pricier + 1)
            val cheaperPoints = MatchScoreCalculator.budgetPoints(cheaper, budget)
            assertTrue(cheaperPoints >= MatchScoreCalculator.budgetPoints(pricier, budget))

            val listing = random.listing().copy(rent = Money(pricier, Currency.INR))
            val profile = random.profile().copy(budget = budget)
            val expensive = MatchScoreCalculator.score(listing, profile, now = now)
            val cheap =
                MatchScoreCalculator.score(
                    listing.copy(rent = Money(cheaper, Currency.INR)),
                    profile,
                    now = now,
                )
            assertTrue(cheap.reasons[0].points >= expensive.reasons[0].points)
            assertTrue(cheap.score >= expensive.score)
        }
    }

    @Test
    fun goingOverBudgetCostsPointsAndIsExplained() {
        val budget = BudgetRange(null, 20_000, Currency.INR)
        assertTrue(
            MatchScoreCalculator.budgetPoints(21_000, budget) < MatchScoreCalculator.budgetPoints(20_000, budget),
        )
        assertEquals(0.0, MatchScoreCalculator.budgetPoints(26_000, budget), 0.0)
        val listing = kochi.first().copy(rent = Money(25_000, Currency.INR))
        val reason = MatchScoreCalculator.score(listing, PreferenceProfile(budget = budget), now = now).reasons[0]
        assertEquals(ReasonLabel.OverBudget, reason.label)
        assertEquals(25, reason.value)
        assertFalse(reason.positive)
    }

    @Test
    fun aMissingMustHaveAlwaysScoresZeroAndIsExcludedFromBestMatch() {
        val random = Random(3)
        repeat(2_000) {
            val mustHave = MustHave.entries.random(random)
            val profile = random.profile().copy(mustHaves = setOf(mustHave))
            val listing = random.listing().without(mustHave)
            val result = MatchScoreCalculator.score(listing, profile, now = now)
            assertEquals(0, result.score)
            assertTrue(result.excluded)
            assertEquals(setOf(mustHave), result.failedMustHaves)
            assertEquals(ReasonLabel.MissingMustHave, result.reasons[3].label)
            val context = MatchContext(profile, BehaviorAffinity.None, now)
            assertTrue(BestMatchRanking.rank(listOf(listing), PropertyFilter(), context).isEmpty())
        }
    }

    private fun Property.without(mustHave: MustHave): Property =
        when (mustHave) {
            MustHave.Parking -> copy(parking = false)
            MustHave.Furnished -> copy(furnished = false)
            MustHave.Balcony -> copy(amenities = amenities - Amenity.Balcony)
            MustHave.Verified -> copy(verified = false)
        }

    @Test
    fun decayMakesOldSignalsCountLess() {
        val random = Random(4)
        repeat(1_000) {
            val pool = List(6) { random.listing() }
            val events = random.events(pool).ifEmpty { return@repeat }
            val older =
                events.map {
                    it.copy(
                        occurredAt = it.occurredAt.minus(Duration.ofDays(random.nextLong(1, 90))),
                    )
                }
            val byId = pool.associateBy { it.id }::get
            val recent = BehaviorAffinity.from(events, byId, now)
            val aged = BehaviorAffinity.from(older, byId, now)
            pool.forEach { candidate ->
                val recentSignal = recent.signalFor(candidate)
                val agedSignal = aged.signalFor(candidate)
                // Every event's weight shrinks, so the summed signal of a single-sign history can only shrink.
                if (events.all { weightSign(it) >= 0 }) assertTrue(agedSignal <= recentSignal + 1e-9)
                if (events.all { weightSign(it) <= 0 }) assertTrue(agedSignal >= recentSignal - 1e-9)
            }
        }
        assertEquals(0.5, BehaviorAffinity.decay(BehaviorAffinity.HALF_LIFE), 1e-9)
        assertEquals(1.0, BehaviorAffinity.decay(Duration.ofDays(-3)), 0.0)
    }

    private fun weightSign(event: InteractionEvent): Int =
        when (event.type) {
            InteractionType.Unfavorited, InteractionType.Dismissed -> -1
            else -> 1
        }

    @Test
    fun aFavouriteFromLastMonthLiftsTheScoreLessThanOneFromToday() {
        val liked = kochi.first()
        val event = InteractionEvent("user-1", liked.id, InteractionType.Favorited, now)
        val byId = mapOf(liked.id to liked)::get
        val today = BehaviorAffinity.from(listOf(event), byId, now)
        val lastMonth =
            BehaviorAffinity.from(
                listOf(event.copy(occurredAt = now.minus(Duration.ofDays(30)))),
                byId,
                now,
            )
        val profile = PreferenceProfile()
        val fresh = MatchScoreCalculator.score(liked, profile, today, now).reasons.last()
        val stale = MatchScoreCalculator.score(liked, profile, lastMonth, now).reasons.last()
        assertTrue(fresh.points > stale.points)
        assertTrue(stale.points > MatchFactor.LearnedBehavior.weight / 2.0)
        assertEquals(ReasonLabel.LikeHomesYouEngagedWith, fresh.label)
    }

    @Test
    fun scoringIsDeterministic() {
        val random = Random(5)
        repeat(500) {
            val listing = random.listing()
            val profile = random.profile()
            val pool = List(4) { random.listing() } + listing
            val events = random.events(pool)
            val first =
                MatchScoreCalculator.score(
                    listing,
                    profile,
                    BehaviorAffinity.from(
                        events,
                        pool.associateBy {
                            it.id
                        }::get,
                        now,
                    ),
                    now,
                )
            val second =
                MatchScoreCalculator.score(
                    listing,
                    profile,
                    BehaviorAffinity.from(
                        events,
                        pool.associateBy {
                            it.id
                        }::get,
                        now,
                    ),
                    now,
                )
            assertEquals(first, second)
        }
    }

    @Test
    fun aHomeThatFitsEveryAnswerScoresHighAndExplainsWhy() {
        val home =
            kochi.first().copy(
                type = PropertyType.Apartment,
                rent = Money(18_000, Currency.INR),
                bedrooms = 2,
                bathrooms = 2,
                locality = "Kakkanad",
                location = GeoPoint(10.0159, 76.3419),
                parking = true,
                verified = true,
                rating = 4.9,
                amenities = listOf(Amenity.Wifi, Amenity.HomeOffice, Amenity.PowerBackup, Amenity.Balcony),
                createdAt = now.minus(Duration.ofDays(3)),
            )
        val profile =
            PreferenceProfile(
                budget = BudgetRange(10_000, 25_000, Currency.INR),
                minBedrooms = 2,
                minBathrooms = 1,
                propertyTypes = setOf(PropertyType.Apartment),
                anchors = listOf(anchors[1]),
                lifestyle = setOf(LifestyleTag.RemoteWorker),
                mustHaves = setOf(MustHave.Parking, MustHave.Verified),
            )
        val result = MatchScoreCalculator.score(home, profile, now = now)
        assertTrue("score ${result.score}", result.score >= 90)
        assertFalse(result.excluded)
        val top = result.topReasons()
        assertEquals(3, top.size)
        assertTrue(top.all { it.positive && !it.label.neutral })
        assertEquals(ReasonLabel.WithinBudget, top.first().label)
        assertEquals(ReasonLabel.InPreferredArea, result.reasons[1].label)
    }

    @Test
    fun anEmptyProfileJudgesOnlyTrustAndBehaviourAndPromotesNoNeutralReason() {
        val result = MatchScoreCalculator.score(kochi.first(), PreferenceProfile(), now = now)
        assertTrue(result.score in 50..100)
        assertTrue(result.topReasons().none { it.label.neutral })
        assertTrue(result.topReasons().size <= 3)
    }

    @Test
    fun budgetsInAnotherCurrencyAndFarAwayAnchorsAreNeutral() {
        val paris = seedProperties.first { it.regionId == "paris" }
        val profile = PreferenceProfile(budget = BudgetRange(null, 20_000, Currency.INR), anchors = anchors)
        val result = MatchScoreCalculator.score(paris, profile, now = now)
        assertEquals(ReasonLabel.BudgetOtherCurrency, result.reasons[0].label)
        assertEquals(ReasonLabel.LocationOtherRegion, result.reasons[1].label)
        assertEquals(15.0, result.reasons[0].points, 1e-9)
        assertEquals(10.0, result.reasons[1].points, 1e-9)
    }

    @Test
    fun commuteDistanceIsReportedInKilometres() {
        val home = kochi.first().copy(location = GeoPoint(9.9816, 76.2999), locality = "Somewhere")
        val profile = PreferenceProfile(anchors = listOf(anchors[2]))
        val reason = MatchScoreCalculator.score(home, profile, now = now).reasons[1]
        assertEquals(ReasonLabel.ShortCommute, reason.label)
        val km = MatchScoreCalculator.distanceKm(anchors[2].point, home.location)
        assertEquals(Math.round(km).toInt(), reason.value)
        assertTrue(km in 4.0..7.0)
    }
}
