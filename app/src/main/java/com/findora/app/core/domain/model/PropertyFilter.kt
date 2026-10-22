package com.findora.app.core.domain.model

/** How listings are ordered. "Best match" ranks verified, well-rated, well-reviewed homes first until Phase 5. */
enum class SortOrder { BestMatch, PriceLowToHigh, PriceHighToLow, Newest, Rating }

/** The Home category chips. "Rental House" shows every rental; the others narrow to one [PropertyType]. */
enum class HomeCategory(
    val type: PropertyType?,
) {
    RentalHouse(null),
    Apartment(PropertyType.Apartment),
    Houses(PropertyType.House),
    Rooms(PropertyType.Room),
    ;

    companion object {
        fun of(type: PropertyType?): HomeCategory = entries.firstOrNull { it.type == type } ?: RentalHouse
    }
}

/** Structured listing filter; null or false fields do not constrain the result. */
data class PropertyFilter(
    val type: PropertyType? = null,
    val minRent: Long? = null,
    val maxRent: Long? = null,
    val minBedrooms: Int? = null,
    val verifiedOnly: Boolean = false,
    /** Free text matched against the listing title and locality. */
    val query: String = "",
    val minBathrooms: Int? = null,
    val minAreaSqm: Int? = null,
    val maxAreaSqm: Int? = null,
    val furnishedOnly: Boolean = false,
    val petFriendlyOnly: Boolean = false,
    val parkingOnly: Boolean = false,
    val amenities: Set<Amenity> = emptySet(),
    val sort: SortOrder = SortOrder.BestMatch,
) {
    init {
        if (minRent != null && maxRent != null) require(minRent <= maxRent) { "minRent must not exceed maxRent" }
        if (minAreaSqm != null &&
            maxAreaSqm != null
        ) {
            require(minAreaSqm <= maxAreaSqm) { "minArea must not exceed maxArea" }
        }
    }

    /** Refinements set in the filter sheet (badge on the filter icon); category and search text are not counted. */
    val activeCount: Int
        get() =
            listOf(
                minRent != null || maxRent != null,
                minBedrooms != null,
                minBathrooms != null,
                minAreaSqm != null || maxAreaSqm != null,
                furnishedOnly,
                petFriendlyOnly,
                parkingOnly,
                verifiedOnly,
                sort != SortOrder.BestMatch,
            ).count { it } + amenities.size

    /** Drops every sheet refinement but keeps the category and the search text. */
    fun withoutRefinements(): PropertyFilter = PropertyFilter(type = type, query = query)
}

/** Ranges of the cached listings in a region, used to bound the filter sliders. */
data class ListingBounds(
    val minRent: Long,
    val maxRent: Long,
    val minAreaSqm: Int,
    val maxAreaSqm: Int,
    val count: Int,
)

/** A search suggestion: a neighbourhood or a listing name. */
data class SearchSuggestion(
    val text: String,
    val kind: Kind,
) {
    enum class Kind { Locality, Property }
}
