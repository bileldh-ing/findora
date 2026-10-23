package com.findora.app.core.domain.match

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType

/** How the user lives; each tag maps to listing signals in [LifestyleSignals]. [id] is the stored value. */
enum class LifestyleTag(
    val id: String,
) {
    Quiet("quiet"),
    Family("family"),
    PetOwner("pet_owner"),
    RemoteWorker("remote_worker"),
    Student("student"),
    Nightlife("nightlife"),
    Nature("nature"),
    ;

    companion object {
        fun fromIdOrNull(id: String): LifestyleTag? = entries.firstOrNull { it.id == id }
    }
}

/** Hard constraints: a listing without one of these scores 0 and is left out of "Best match". */
enum class MustHave(
    val id: String,
) {
    Parking("parking"),
    Furnished("furnished"),
    Balcony("balcony"),
    Verified("verified"),
    ;

    companion object {
        fun fromIdOrNull(id: String): MustHave? = entries.firstOrNull { it.id == id }
    }
}

/** Monthly rent the user is comfortable with, in [currency]. [min] is optional (soft floor). */
@Immutable
data class BudgetRange(
    val min: Long?,
    val max: Long,
    val currency: Currency,
) {
    init {
        require(max > 0) { "max must be positive" }
        if (min != null) require(min in 0..max) { "min must be between 0 and max" }
    }
}

/** A place the user wants to live near: a chosen neighbourhood or the work/school location. */
@Immutable
data class LocationAnchor(
    val name: String,
    val point: GeoPoint,
    val kind: Kind,
) {
    enum class Kind { Neighbourhood, Commute }
}

/**
 * What the user told the preference quiz. Every part is optional: an unanswered step does not
 * constrain or penalise a listing (that factor scores full marks), so skipping the quiz is harmless.
 */
@Immutable
data class PreferenceProfile(
    val budget: BudgetRange? = null,
    val minBedrooms: Int? = null,
    val minBathrooms: Int? = null,
    val propertyTypes: Set<PropertyType> = emptySet(),
    val anchors: List<LocationAnchor> = emptyList(),
    val lifestyle: Set<LifestyleTag> = emptySet(),
    val mustHaves: Set<MustHave> = emptySet(),
) {
    init {
        minBedrooms?.let { require(it >= 0) { "minBedrooms must not be negative" } }
        minBathrooms?.let { require(it >= 0) { "minBathrooms must not be negative" } }
    }

    val commute: LocationAnchor? get() = anchors.firstOrNull { it.kind == LocationAnchor.Kind.Commute }

    val neighbourhoods: List<LocationAnchor> get() = anchors.filter { it.kind == LocationAnchor.Kind.Neighbourhood }
}
