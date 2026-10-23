package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.distanceKmTo
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The Best Match engine: a pure, deterministic score from 0 to 100 for one listing against a
 * [PreferenceProfile], built from seven weighted factors ([MatchFactor]) and explained by one [Reason] per
 * factor. A listing that lacks a must-have scores 0 and is marked excluded. No randomness and no clock of
 * its own: [now] is passed in, so the same inputs always give the same result.
 */
object MatchScoreCalculator {
    fun score(
        property: Property,
        profile: PreferenceProfile,
        affinity: BehaviorAffinity = BehaviorAffinity.None,
        now: Instant,
    ): MatchResult {
        val failed = profile.mustHaves.filterNotTo(LinkedHashSet()) { LifestyleSignals.satisfies(it, property) }
        val reasons =
            listOf(
                FactorScoring.budget(property, profile),
                FactorScoring.location(property, profile),
                FactorScoring.size(property, profile),
                FactorScoring.amenities(property, profile, failed),
                FactorScoring.lifestyle(property, profile),
                FactorScoring.trust(property, now),
                FactorScoring.behavior(property, affinity),
            )
        val total = if (failed.isEmpty()) reasons.sumOf { it.points }.roundToInt() else 0
        return MatchResult(total.coerceIn(0, MatchResult.MAX_SCORE), reasons, failed)
    }

    /** Points the budget factor gives [rent] for [budget]; exposed for the monotonicity tests. */
    fun budgetPoints(
        rent: Long,
        budget: BudgetRange,
    ): Double = FactorScoring.budgetPoints(rent, budget)

    /** Great-circle (haversine) distance in kilometres between two points. */
    fun distanceKm(
        a: GeoPoint,
        b: GeoPoint,
    ): Double = a.distanceKmTo(b)
}
