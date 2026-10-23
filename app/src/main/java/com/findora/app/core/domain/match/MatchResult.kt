package com.findora.app.core.domain.match

import androidx.compose.runtime.Immutable

/** The seven scored factors and their weights (out of 100), as set in the product spec. */
enum class MatchFactor(
    val weight: Int,
) {
    BudgetFit(30),
    LocationOrCommute(20),
    SizeBedsBaths(15),
    AmenitiesAndMustHaves(15),
    StyleAndLifestyle(10),
    TrustAndFreshness(5),
    LearnedBehavior(5),
}

/**
 * Stable keys for a reason's text; the UI maps each to a localised string. [neutral] labels explain that
 * the user expressed no preference, so they are listed in the breakdown but never promoted to a chip.
 */
enum class ReasonLabel(
    val neutral: Boolean = false,
) {
    WithinBudget,
    BelowBudgetRange,
    OverBudget,
    BudgetOtherCurrency(neutral = true),
    NoBudget(neutral = true),
    InPreferredArea,
    NearPreferredArea,
    FarFromPreferredArea,
    ShortCommute,
    LongCommute,
    LocationOtherRegion(neutral = true),
    NoLocation(neutral = true),
    SizeFits,
    FewerBedrooms,
    FewerBathrooms,
    OtherPropertyType,
    NoSizePreference(neutral = true),
    HasMustHaves,
    MissingMustHave,
    HasWantedAmenities,
    MissingWantedAmenities,
    RichAmenities,
    FewAmenities,
    LifestyleFit,
    PartialLifestyleFit,
    LifestyleMismatch,
    NoLifestyle(neutral = true),
    TrustedListing,
    LessTrustedListing,
    LikeHomesYouEngagedWith,
    UnlikeHomesYouEngagedWith,
    NotEnoughActivity(neutral = true),
}

/**
 * One factor's contribution. [points] is out of [MatchFactor.weight]; [positive] is its sign (at least half
 * the factor's weight). [value] carries a number the text needs: kilometres or percent over budget.
 */
@Immutable
data class Reason(
    val factor: MatchFactor,
    val label: ReasonLabel,
    val positive: Boolean,
    val points: Double,
    val value: Int? = null,
) {
    val maxPoints: Int get() = factor.weight

    /** Share of the factor earned, 0..1, drawn as the bar in "Why this match?". */
    val fraction: Float get() = (points / factor.weight).toFloat().coerceIn(0f, 1f)

    /** How strongly this reason explains the score: points earned if positive, points lost if negative. */
    val impact: Double get() = if (positive) points else factor.weight - points
}

/**
 * A listing scored against the user's profile: [score] 0..100 and one [reasons] entry per [MatchFactor], in
 * factor order. A failed must-have sets [score] to 0 and [excluded], which keeps it out of "Best match".
 */
@Immutable
data class MatchResult(
    val score: Int,
    val reasons: List<Reason>,
    val failedMustHaves: Set<MustHave> = emptySet(),
) {
    init {
        require(score in 0..MAX_SCORE) { "score must be within 0..100" }
    }

    val excluded: Boolean get() = failedMustHaves.isNotEmpty()

    /** The [count] most telling non-neutral reasons, strongest first (ties keep factor order). */
    fun topReasons(count: Int = TOP_REASONS): List<Reason> =
        reasons
            .filterNot { it.label.neutral }
            .sortedByDescending { it.impact }
            .take(count)

    companion object {
        const val MAX_SCORE = 100
        const val TOP_REASONS = 3
    }
}
