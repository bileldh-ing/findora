package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.distanceKmTo
import java.time.Duration
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.math.tanh

// Budget: inside the range earns 80% of the weight plus up to 20% for headroom under the ceiling.
private const val IN_BUDGET_SHARE = 0.8
private const val BELOW_RANGE_SHARE = 2.0 / 3.0

/** 30% over the ceiling earns nothing. */
private const val MAX_OVERSHOOT = 0.3

// Location: full marks within the "near" radius, nothing beyond the "far" one, linear in between.
private const val NEIGHBOURHOOD_NEAR_KM = 1.5
private const val NEIGHBOURHOOD_FAR_KM = 15.0
private const val COMMUTE_NEAR_KM = 3.0
private const val COMMUTE_FAR_KM = 25.0

/** Anchors this far away belong to another region pack; location is then neutral rather than a penalty. */
private const val OTHER_REGION_KM = 200.0

// Size split (out of 15): bedrooms 6, bathrooms 4, property type 5. Missing two rooms earns nothing.
private const val BEDS_POINTS = 6.0
private const val BATHS_POINTS = 4.0
private const val TYPE_POINTS = 5.0
private const val ROOMS_SHORT_FOR_ZERO = 2.0

// Amenities (out of 15): meeting every must-have is worth 6, the rest comes from amenities.
private const val MUST_HAVE_POINTS = 6.0
private const val RICH_AMENITY_COUNT = 6.0

// Trust (out of 5): verified 2, rating 2 (3.5 to 5.0 stars), freshness 1 (halves every 60 days).
private const val VERIFIED_POINTS = 2.0
private const val RATING_POINTS = 2.0
private const val RATING_FLOOR = 3.5
private const val RATING_SPAN = 1.5
private const val FRESHNESS_POINTS = 1.0
private val FreshnessHalfLife: Duration = Duration.ofDays(60)

/** Behaviour signal that moves the learned factor about three quarters of the way to an extreme. */
private const val SIGNAL_SCALE = 6.0
private const val HALF = 0.5

private const val PERCENT = 100

/** One function per [MatchFactor]; each returns that factor's [Reason] with points clamped to its weight. */
internal object FactorScoring {
    fun budgetPoints(
        rent: Long,
        budget: BudgetRange,
    ): Double {
        val weight = MatchFactor.BudgetFit.weight.toDouble()
        val inRangeFloor = weight * IN_BUDGET_SHARE
        return when {
            rent > budget.max -> {
                val overshoot = (rent - budget.max).toDouble() / budget.max
                (inRangeFloor * (1 - overshoot / MAX_OVERSHOOT)).coerceAtLeast(0.0)
            }
            budget.min != null && rent < budget.min -> weight * BELOW_RANGE_SHARE
            else -> inRangeFloor + (weight - inRangeFloor) * (budget.max - rent).toDouble() / budget.max
        }
    }

    fun budget(
        property: Property,
        profile: PreferenceProfile,
    ): Reason {
        val factor = MatchFactor.BudgetFit
        val budget = profile.budget
        return when {
            budget == null -> full(factor, ReasonLabel.NoBudget)
            budget.currency != property.rent.currency ->
                Reason(factor, ReasonLabel.BudgetOtherCurrency, positive = true, points = factor.weight * HALF)
            else -> budgetReason(property.rent.amount, budget, budgetPoints(property.rent.amount, budget))
        }
    }

    fun location(
        property: Property,
        profile: PreferenceProfile,
    ): Reason {
        val factor = MatchFactor.LocationOrCommute
        val distances = profile.anchors.map { it to it.point.distanceKmTo(property.location) }
        return when {
            distances.isEmpty() -> full(factor, ReasonLabel.NoLocation)
            distances.all { (_, km) -> km > OTHER_REGION_KM } ->
                Reason(factor, ReasonLabel.LocationOtherRegion, positive = true, points = factor.weight * HALF)
            else -> nearestAnchorReason(property, distances)
        }
    }

    fun size(
        property: Property,
        profile: PreferenceProfile,
    ): Reason {
        val factor = MatchFactor.SizeBedsBaths
        if (profile.minBedrooms == null && profile.minBathrooms == null && profile.propertyTypes.isEmpty()) {
            return full(factor, ReasonLabel.NoSizePreference)
        }
        val beds = BEDS_POINTS * roomShare(property.bedrooms, profile.minBedrooms)
        val baths = BATHS_POINTS * roomShare(property.bathrooms, profile.minBathrooms)
        val typeFits = profile.propertyTypes.isEmpty() || property.type in profile.propertyTypes
        val type = if (typeFits) TYPE_POINTS else 0.0
        val label =
            when {
                !typeFits -> ReasonLabel.OtherPropertyType
                beds < BEDS_POINTS -> ReasonLabel.FewerBedrooms
                baths < BATHS_POINTS -> ReasonLabel.FewerBathrooms
                else -> ReasonLabel.SizeFits
            }
        return reason(factor, label, beds + baths + type)
    }

    fun amenities(
        property: Property,
        profile: PreferenceProfile,
        failed: Set<MustHave>,
    ): Reason {
        val factor = MatchFactor.AmenitiesAndMustHaves
        if (failed.isNotEmpty()) return Reason(factor, ReasonLabel.MissingMustHave, positive = false, points = 0.0)
        val wanted = LifestyleSignals.wantedAmenities(profile.lifestyle)
        val share =
            if (wanted.isEmpty()) {
                (property.amenities.size / RICH_AMENITY_COUNT).coerceAtMost(1.0)
            } else {
                wanted.count { it in property.amenities }.toDouble() / wanted.size
            }
        val hasMustHaves = profile.mustHaves.isNotEmpty()
        val points =
            if (hasMustHaves) {
                MUST_HAVE_POINTS + (factor.weight - MUST_HAVE_POINTS) * share
            } else {
                factor.weight * share
            }
        val label =
            when {
                hasMustHaves && share >= HALF -> ReasonLabel.HasMustHaves
                wanted.isNotEmpty() && share >= HALF -> ReasonLabel.HasWantedAmenities
                wanted.isNotEmpty() -> ReasonLabel.MissingWantedAmenities
                share >= HALF -> ReasonLabel.RichAmenities
                hasMustHaves -> ReasonLabel.HasMustHaves
                else -> ReasonLabel.FewAmenities
            }
        return reason(factor, label, points)
    }

    fun lifestyle(
        property: Property,
        profile: PreferenceProfile,
    ): Reason {
        val factor = MatchFactor.StyleAndLifestyle
        if (profile.lifestyle.isEmpty()) return full(factor, ReasonLabel.NoLifestyle)
        val matched = profile.lifestyle.count { LifestyleSignals.matches(it, property) }
        val share = matched.toDouble() / profile.lifestyle.size
        val label =
            when {
                share >= 1.0 -> ReasonLabel.LifestyleFit
                share >= HALF -> ReasonLabel.PartialLifestyleFit
                else -> ReasonLabel.LifestyleMismatch
            }
        return reason(factor, label, factor.weight * share)
    }

    fun trust(
        property: Property,
        now: Instant,
    ): Reason {
        val factor = MatchFactor.TrustAndFreshness
        val verified = if (property.verified) VERIFIED_POINTS else 0.0
        val rating = RATING_POINTS * ((property.rating - RATING_FLOOR) / RATING_SPAN).coerceIn(0.0, 1.0)
        val age = Duration.between(property.createdAt, now)
        val fresh = FRESHNESS_POINTS * BehaviorAffinity.decay(age, FreshnessHalfLife)
        val points = verified + rating + fresh
        return reason(
            factor,
            if (points >= factor.weight * HALF) ReasonLabel.TrustedListing else ReasonLabel.LessTrustedListing,
            points,
        )
    }

    fun behavior(
        property: Property,
        affinity: BehaviorAffinity,
    ): Reason {
        val factor = MatchFactor.LearnedBehavior
        if (!affinity.hasSignal) {
            return Reason(factor, ReasonLabel.NotEnoughActivity, positive = true, points = factor.weight * HALF)
        }
        val signal = affinity.signalFor(property)
        val points = factor.weight * (HALF + HALF * tanh(signal / SIGNAL_SCALE))
        val label = if (signal >= 0) ReasonLabel.LikeHomesYouEngagedWith else ReasonLabel.UnlikeHomesYouEngagedWith
        return Reason(factor, label, positive = signal >= 0, points = points)
    }
}

private fun full(
    factor: MatchFactor,
    label: ReasonLabel,
) = Reason(factor, label, positive = true, points = factor.weight.toDouble())

private fun reason(
    factor: MatchFactor,
    label: ReasonLabel,
    points: Double,
    value: Int? = null,
): Reason {
    val clamped = points.coerceIn(0.0, factor.weight.toDouble())
    return Reason(factor, label, positive = clamped >= factor.weight * HALF, points = clamped, value = value)
}

private fun linearShare(
    km: Double,
    near: Double,
    far: Double,
): Double =
    when {
        km <= near -> 1.0
        km >= far -> 0.0
        else -> (far - km) / (far - near)
    }

private fun budgetReason(
    rent: Long,
    budget: BudgetRange,
    points: Double,
): Reason {
    val factor = MatchFactor.BudgetFit
    return when {
        rent > budget.max -> {
            val percentOver = ((rent - budget.max) * PERCENT / budget.max).toInt().coerceAtLeast(1)
            reason(factor, ReasonLabel.OverBudget, points, value = percentOver)
        }
        budget.min != null && rent < budget.min -> reason(factor, ReasonLabel.BelowBudgetRange, points)
        else -> reason(factor, ReasonLabel.WithinBudget, points)
    }
}

/** Scores location by the anchor the listing suits best (ties go to the closer anchor). */
private fun nearestAnchorReason(
    property: Property,
    distances: List<Pair<LocationAnchor, Double>>,
): Reason {
    val factor = MatchFactor.LocationOrCommute
    val (anchor, km) =
        distances.maxWith(
            compareBy<Pair<LocationAnchor, Double>> { (anchor, km) -> anchorShare(anchor, property, km) }
                .thenByDescending { (_, km) -> km },
        )
    val share = anchorShare(anchor, property, km)
    val inArea = anchor.kind == LocationAnchor.Kind.Neighbourhood && anchor.name.equals(property.locality, true)
    val label =
        when {
            anchor.kind == LocationAnchor.Kind.Commute ->
                if (share >= HALF) ReasonLabel.ShortCommute else ReasonLabel.LongCommute
            inArea -> ReasonLabel.InPreferredArea
            share >= HALF -> ReasonLabel.NearPreferredArea
            else -> ReasonLabel.FarFromPreferredArea
        }
    val value = if (inArea) null else km.roundToInt().coerceAtLeast(1)
    return reason(factor, label, factor.weight * share, value)
}

private fun anchorShare(
    anchor: LocationAnchor,
    property: Property,
    km: Double,
): Double =
    when (anchor.kind) {
        LocationAnchor.Kind.Neighbourhood ->
            if (anchor.name.equals(property.locality, ignoreCase = true)) {
                1.0
            } else {
                linearShare(km, NEIGHBOURHOOD_NEAR_KM, NEIGHBOURHOOD_FAR_KM)
            }
        LocationAnchor.Kind.Commute -> linearShare(km, COMMUTE_NEAR_KM, COMMUTE_FAR_KM)
    }

private fun roomShare(
    actual: Int,
    wanted: Int?,
): Double =
    if (wanted == null || actual >= wanted) {
        1.0
    } else {
        (1 - (wanted - actual) / ROOMS_SHORT_FOR_ZERO).coerceAtLeast(0.0)
    }
