package com.findora.app.core.ui.match

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.MatchFactor
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.Reason
import com.findora.app.core.domain.match.ReasonLabel
import com.findora.app.core.domain.model.PropertyType

@StringRes
fun LifestyleTag.labelRes(): Int =
    when (this) {
        LifestyleTag.Quiet -> R.string.lifestyle_quiet
        LifestyleTag.Family -> R.string.lifestyle_family
        LifestyleTag.PetOwner -> R.string.lifestyle_pet_owner
        LifestyleTag.RemoteWorker -> R.string.lifestyle_remote_worker
        LifestyleTag.Student -> R.string.lifestyle_student
        LifestyleTag.Nightlife -> R.string.lifestyle_nightlife
        LifestyleTag.Nature -> R.string.lifestyle_nature
    }

@StringRes
fun MustHave.labelRes(): Int =
    when (this) {
        MustHave.Parking -> R.string.must_have_parking
        MustHave.Furnished -> R.string.must_have_furnished
        MustHave.Balcony -> R.string.must_have_balcony
        MustHave.Verified -> R.string.must_have_verified
    }

@StringRes
fun PropertyType.labelRes(): Int =
    when (this) {
        PropertyType.House -> R.string.category_houses
        PropertyType.Apartment -> R.string.category_apartment
        PropertyType.Room -> R.string.category_rooms
    }

@StringRes
fun MatchFactor.labelRes(): Int =
    when (this) {
        MatchFactor.BudgetFit -> R.string.match_factor_budget
        MatchFactor.LocationOrCommute -> R.string.match_factor_location
        MatchFactor.SizeBedsBaths -> R.string.match_factor_size
        MatchFactor.AmenitiesAndMustHaves -> R.string.match_factor_amenities
        MatchFactor.StyleAndLifestyle -> R.string.match_factor_lifestyle
        MatchFactor.TrustAndFreshness -> R.string.match_factor_trust
        MatchFactor.LearnedBehavior -> R.string.match_factor_behavior
    }

/** The localised sentence for a [Reason], with its number (km, percent) filled in where the text needs one. */
@Composable
fun Reason.text(): String {
    val res = label.labelRes()
    return if (value != null && label.takesValue) stringResource(res, value) else stringResource(res)
}

private val ReasonLabel.takesValue: Boolean
    get() =
        this == ReasonLabel.OverBudget ||
            this == ReasonLabel.NearPreferredArea ||
            this == ReasonLabel.FarFromPreferredArea ||
            this == ReasonLabel.ShortCommute ||
            this == ReasonLabel.LongCommute

@Suppress("CyclomaticComplexMethod") // A flat lookup table, one branch per label.
@StringRes
fun ReasonLabel.labelRes(): Int =
    when (this) {
        ReasonLabel.WithinBudget -> R.string.reason_within_budget
        ReasonLabel.BelowBudgetRange -> R.string.reason_below_budget_range
        ReasonLabel.OverBudget -> R.string.reason_over_budget
        ReasonLabel.BudgetOtherCurrency -> R.string.reason_budget_other_currency
        ReasonLabel.NoBudget -> R.string.reason_no_budget
        ReasonLabel.InPreferredArea -> R.string.reason_in_preferred_area
        ReasonLabel.NearPreferredArea -> R.string.reason_near_preferred_area
        ReasonLabel.FarFromPreferredArea -> R.string.reason_far_from_preferred_area
        ReasonLabel.ShortCommute -> R.string.reason_short_commute
        ReasonLabel.LongCommute -> R.string.reason_long_commute
        ReasonLabel.LocationOtherRegion -> R.string.reason_location_other_region
        ReasonLabel.NoLocation -> R.string.reason_no_location
        ReasonLabel.SizeFits -> R.string.reason_size_fits
        ReasonLabel.FewerBedrooms -> R.string.reason_fewer_bedrooms
        ReasonLabel.FewerBathrooms -> R.string.reason_fewer_bathrooms
        ReasonLabel.OtherPropertyType -> R.string.reason_other_property_type
        ReasonLabel.NoSizePreference -> R.string.reason_no_size_preference
        ReasonLabel.HasMustHaves -> R.string.reason_has_must_haves
        ReasonLabel.MissingMustHave -> R.string.reason_missing_must_have
        ReasonLabel.HasWantedAmenities -> R.string.reason_has_wanted_amenities
        ReasonLabel.MissingWantedAmenities -> R.string.reason_missing_wanted_amenities
        ReasonLabel.RichAmenities -> R.string.reason_rich_amenities
        ReasonLabel.FewAmenities -> R.string.reason_few_amenities
        ReasonLabel.LifestyleFit -> R.string.reason_lifestyle_fit
        ReasonLabel.PartialLifestyleFit -> R.string.reason_partial_lifestyle_fit
        ReasonLabel.LifestyleMismatch -> R.string.reason_lifestyle_mismatch
        ReasonLabel.NoLifestyle -> R.string.reason_no_lifestyle
        ReasonLabel.TrustedListing -> R.string.reason_trusted_listing
        ReasonLabel.LessTrustedListing -> R.string.reason_less_trusted_listing
        ReasonLabel.LikeHomesYouEngagedWith -> R.string.reason_like_homes_you_engaged_with
        ReasonLabel.UnlikeHomesYouEngagedWith -> R.string.reason_unlike_homes_you_engaged_with
        ReasonLabel.NotEnoughActivity -> R.string.reason_not_enough_activity
    }
