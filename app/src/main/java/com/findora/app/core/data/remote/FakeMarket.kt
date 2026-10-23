package com.findora.app.core.data.remote

import com.findora.app.core.common.AppClock
import com.findora.app.core.data.seed.ListingDto
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToLong

/**
 * Makes the fake backend's market move with time, deterministically, so saved-search alerts and price-drop
 * alerts have something real to find without a server:
 *
 * - New listings: every day since [EPOCH] (the seed's generation date) each region publishes [DROPS_PER_DAY]
 *   new homes, at 09:00 and 13:00 UTC. Each is a new unit of an existing seed home (same building, photos, agent
 *   and features; its own id, unit name, publication time and a rent within ±10% of the original, history scaled
 *   alike). They stay listed for [LISTING_DAYS] days, then disappear as let, so the cache stays bounded.
 * - Price drops: in every week since [EPOCH], about one listing in [OFFER_ODDS] has its rent lowered by
 *   [OFFER_PERCENT]% for that week.
 *
 * Everything is a pure function of the listing ids and the clock: the same instant always yields the same market,
 * on every device, and the detail page resolves generated ids like any other listing.
 */
@Singleton
class FakeMarket
    @Inject
    constructor(
        private val clock: AppClock,
    ) {
        /** The region's listings as the market stands now: seed homes (with this week's offers) plus new units. */
        fun apply(seed: List<ListingDto>): List<ListingDto> {
            val now = clock.now()
            return (seed + newListings(seed, now)).map { withWeeklyOffer(it, now) }
        }

        /** The units published within the last [LISTING_DAYS] days, up to [now]. */
        fun newListings(
            seed: List<ListingDto>,
            now: Instant,
        ): List<ListingDto> {
            val today = now.atZone(ZoneOffset.UTC).toLocalDate()
            val firstDay = maxOf(EPOCH, today.minusDays(LISTING_DAYS - 1))
            return seed
                .groupBy(ListingDto::region)
                .flatMap { (_, regional) ->
                    val templates = regional.sortedBy(ListingDto::id)
                    generateSequence(firstDay) { it.plusDays(1) }
                        .takeWhile { !it.isAfter(today) }
                        .flatMap { day -> (0 until DROPS_PER_DAY).asSequence().map { slot -> day to slot } }
                        .mapNotNull { (day, slot) -> newUnit(templates, day, slot).takeIf { it.listedAt <= now } }
                        .map { it.listing }
                        .toList()
                }
        }

        private fun newUnit(
            templates: List<ListingDto>,
            day: LocalDate,
            slot: Int,
        ): PublishedUnit {
            val dayIndex = ChronoUnit.DAYS.between(EPOCH, day)
            val template = templates[((dayIndex * DROPS_PER_DAY + slot) % templates.size).toInt()]
            val listedAt = day.atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(FIRST_HOUR + slot * GAP))
            val factor = 1.0 + (stableHash("${template.id}:$dayIndex:$slot") % SPREAD_STEPS - SPREAD) / PERCENT
            val unitName = "${dayIndex % UNIT_FLOORS + 2}${'A' + slot}"
            val listing =
                template.copy(
                    id = "${template.id}-n$dayIndex$slot",
                    title = "${template.title} · $unitName",
                    rentPerMonth = roundRent(template.rentPerMonth * factor),
                    createdAt = listedAt.toString(),
                    priceHistory = template.priceHistory.map { it.copy(amount = roundRent(it.amount * factor)) },
                )
            return PublishedUnit(listing, listedAt)
        }

        private fun withWeeklyOffer(
            listing: ListingDto,
            now: Instant,
        ): ListingDto {
            // Instants have no week unit: count whole UTC days since the epoch date instead.
            val days = ChronoUnit.DAYS.between(EPOCH, now.atZone(ZoneOffset.UTC).toLocalDate())
            val week = days / DAYS_PER_WEEK
            if (days < 0 || stableHash("${listing.id}:w$week") % OFFER_ODDS != 0L) return listing
            val reduced = roundRent(listing.rentPerMonth * (PERCENT - OFFER_PERCENT) / PERCENT)
            return listing.copy(rentPerMonth = minOf(reduced, listing.rentPerMonth - 1).coerceAtLeast(1))
        }

        private data class PublishedUnit(
            val listing: ListingDto,
            val listedAt: Instant,
        )

        companion object {
            /** The seed's generation date: the market starts moving from here. */
            val EPOCH: LocalDate = LocalDate.of(2026, 10, 22)
            const val DROPS_PER_DAY = 2
            const val LISTING_DAYS = 7L
            const val OFFER_ODDS = 8L
            const val OFFER_PERCENT = 5.0
            private const val FIRST_HOUR = 9L
            private const val GAP = 4L
            private const val SPREAD = 10
            private const val SPREAD_STEPS = 21L
            private const val PERCENT = 100.0
            private const val UNIT_FLOORS = 7
            private const val DAYS_PER_WEEK = 7L
            private const val ROUND_SMALL = 10L
            private const val ROUND_LARGE = 100L
            private const val LARGE_RENT = 5_000L

            /** Rents read like real asking prices: to the nearest 10 (small amounts) or 100. */
            fun roundRent(amount: Double): Long {
                val step = if (amount >= LARGE_RENT) ROUND_LARGE else ROUND_SMALL
                return ((amount / step).roundToLong() * step).coerceAtLeast(step)
            }

            /** FNV-1a: stable across JVMs and runs, unlike String.hashCode's contract. */
            fun stableHash(text: String): Long {
                var hash = FNV_OFFSET
                text.forEach { char ->
                    hash = hash xor char.code.toLong()
                    hash *= FNV_PRIME
                }
                return hash and Long.MAX_VALUE
            }

            private const val FNV_OFFSET = -0x340d631b7bdddcdbL
            private const val FNV_PRIME = 0x100000001b3L
        }
    }
