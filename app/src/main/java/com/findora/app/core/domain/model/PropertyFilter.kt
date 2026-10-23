package com.findora.app.core.domain.model

/**
 * How listings are ordered. "Recommended" (the default) ranks verified, well-rated, well-reviewed homes first.
 * "Best match" orders by the user's match score and leaves out homes missing a must-have; it is offered once a
 * preference profile exists (without one it falls back to the Recommended order).
 */
enum class SortOrder { Recommended, BestMatch, PriceLowToHigh, PriceHighToLow, Newest, Rating }

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
    val sort: SortOrder = SortOrder.Recommended,
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
                sort != SortOrder.Recommended,
            ).count { it } + amenities.size

    /** Drops every sheet refinement but keeps the category and the search text. */
    fun withoutRefinements(): PropertyFilter = PropertyFilter(type = type, query = query)

    /**
     * The same rules as the SQL feed (`PropertyQueryBuilder`), applied in memory: used where listings are ranked
     * in Kotlin (Best match) rather than by the database. Sort is not a filter and is ignored here.
     */
    fun accepts(property: Property): Boolean {
        val text = query.trim()
        return (type == null || property.type == type) &&
            (text.isEmpty() || property.title.contains(text, true) || property.locality.contains(text, true)) &&
            acceptsRanges(property) &&
            acceptsFlags(property) &&
            property.amenities.containsAll(amenities)
    }

    private fun acceptsFlags(property: Property): Boolean =
        (!furnishedOnly || property.furnished) &&
            (!petFriendlyOnly || property.petFriendly) &&
            (!parkingOnly || property.parking) &&
            (!verifiedOnly || property.verified)

    private fun acceptsRanges(property: Property): Boolean {
        val rent = property.rent.amount
        val area = property.areaSqm
        return (minRent == null || rent >= minRent) &&
            (maxRent == null || rent <= maxRent) &&
            (minBedrooms == null || property.bedrooms >= minBedrooms) &&
            (minBathrooms == null || property.bathrooms >= minBathrooms) &&
            (minAreaSqm == null || area >= minAreaSqm) &&
            (maxAreaSqm == null || area <= maxAreaSqm)
    }
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
