package com.findora.app.core.data.remote

import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.seedListings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class FakeMarketTest {
    private val clock = MutableClock(Instant.parse("2026-10-22T08:00:00Z"))
    private val market = FakeMarket(clock)
    private val regions = seedListings.map { it.region }.distinct()

    @Test
    fun beforeTheFirstDropOnlyTheSeedIsListed() {
        assertEquals(seedListings.map { it.id }.toSet(), market.apply(seedListings).map { it.id }.toSet())
        clock.current = Instant.parse("2026-10-01T12:00:00Z")
        assertEquals(seedListings.size, market.apply(seedListings).size)
    }

    @Test
    fun newUnitsAppearAtNineAndOneUtcInEveryRegion() {
        clock.current = Instant.parse("2026-10-22T09:00:00Z")
        val morning = market.newListings(seedListings, clock.now())
        assertEquals(regions.size, morning.size)
        clock.current = Instant.parse("2026-10-22T13:00:00Z")
        assertEquals(regions.size * FakeMarket.DROPS_PER_DAY, market.newListings(seedListings, clock.now()).size)
        assertTrue(morning.all { Instant.parse(it.createdAt) <= clock.now() })
    }

    @Test
    fun theMarketIsDeterministicAndIdsAreUnique() {
        clock.current = Instant.parse("2026-10-27T15:00:00Z")
        val first = market.apply(seedListings)
        val second = FakeMarket(MutableClock(clock.now())).apply(seedListings)
        assertEquals(first, second)
        assertEquals(first.size, first.map { it.id }.toSet().size)
    }

    @Test
    fun unitsCopyTheirBuildingAndPriceWithinTenPercent() {
        clock.current = Instant.parse("2026-10-25T14:00:00Z")
        val byId = seedListings.associateBy { it.id }
        market.newListings(seedListings, clock.now()).forEach { unit ->
            val template = byId.getValue(unit.id.substringBefore("-n"))
            assertEquals(template.region, unit.region)
            assertEquals(template.images, unit.images)
            assertEquals(template.agent, unit.agent)
            assertTrue(unit.title.startsWith(template.title))
            val ratio = unit.rentPerMonth.toDouble() / template.rentPerMonth
            assertTrue("${unit.id} ratio $ratio", ratio in 0.88..1.12)
            assertEquals(template.priceHistory.size, unit.priceHistory.size)
        }
    }

    @Test
    fun unitsAreLetAfterAWeek() {
        clock.current = Instant.parse("2026-10-22T14:00:00Z")
        val firstDay = market.newListings(seedListings, clock.now()).map { it.id }.toSet()
        clock.advance(Duration.ofDays(FakeMarket.LISTING_DAYS))
        val later = market.newListings(seedListings, clock.now()).map { it.id }.toSet()
        assertTrue(firstDay.none { it in later })
        assertEquals(regions.size * FakeMarket.DROPS_PER_DAY * FakeMarket.LISTING_DAYS.toInt(), later.size)
    }

    @Test
    fun weeklyOffersLowerSomeRentsForThatWeekOnly() {
        clock.current = Instant.parse("2026-10-23T10:00:00Z")
        val seedIds = seedListings.map { it.id }.toSet()
        val week0 = market.apply(seedListings).filter { it.id in seedIds }.associateBy { it.id }
        val reduced = seedListings.filter { week0.getValue(it.id).rentPerMonth < it.rentPerMonth }
        assertTrue("some offers", reduced.isNotEmpty())
        assertTrue("not everything", reduced.size < seedListings.size / 2)
        reduced.forEach { original ->
            val ratio = week0.getValue(original.id).rentPerMonth.toDouble() / original.rentPerMonth
            assertTrue(ratio in 0.9..0.999)
        }
        clock.advance(Duration.ofDays(7))
        val week1 = market.apply(seedListings).filter { it.id in seedIds }
        val reducedNextWeek = seedListings.filter { s -> week1.first { it.id == s.id }.rentPerMonth < s.rentPerMonth }
        assertNotEquals(reduced.map { it.id }, reducedNextWeek.map { it.id })
    }

    @Test
    fun rentsAreRoundedLikeAskingPrices() {
        assertEquals(10_500L, FakeMarket.roundRent(10_473.0))
        assertEquals(1_190L, FakeMarket.roundRent(1_187.0))
        assertEquals(FakeMarket.stableHash("fd-kl-0001"), FakeMarket.stableHash("fd-kl-0001"))
    }
}
