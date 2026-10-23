package com.findora.app.core.domain.match

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyType

/** How each [LifestyleTag] and [MustHave] reads from a listing's attributes, tags and amenities. */
object LifestyleSignals {
    private const val FAMILY_BEDROOMS = 3

    /** True when [property] shows at least one sign of suiting [tag]. */
    fun matches(
        tag: LifestyleTag,
        property: Property,
    ): Boolean {
        val listing = ListingSignals(property)
        return when (tag) {
            LifestyleTag.Quiet -> listing.hasTag("quiet", "gated") || listing.has(Amenity.Garden)
            LifestyleTag.Family -> listing.suitsFamily()
            LifestyleTag.PetOwner -> property.petFriendly
            LifestyleTag.RemoteWorker -> listing.suitsRemoteWork()
            LifestyleTag.Student -> listing.hasTag("budget", "metro", "cafe") || property.type == PropertyType.Room
            LifestyleTag.Nightlife -> listing.hasTag("city living", "cafe", "mall", "market")
            LifestyleTag.Nature -> listing.suitsNatureLover()
        }
    }

    /** A listing's lower-cased tags and amenity set, read once per check. */
    private class ListingSignals(
        private val property: Property,
    ) {
        private val tags = property.tags.map { it.lowercase() }
        private val amenities = property.amenities.toSet()

        fun hasTag(vararg words: String): Boolean = tags.any { text -> words.any { it in text } }

        fun has(amenity: Amenity): Boolean = amenity in amenities

        fun suitsFamily(): Boolean =
            hasTag("family", "school") || has(Amenity.PlayArea) || property.bedrooms >= FAMILY_BEDROOMS

        fun suitsRemoteWork(): Boolean = has(Amenity.HomeOffice) || (has(Amenity.Wifi) && has(Amenity.PowerBackup))

        fun suitsNatureLover(): Boolean =
            hasTag("park", "waterfront", "sea view") ||
                has(Amenity.Garden) ||
                has(Amenity.SeaView) ||
                has(Amenity.BackwaterView)
    }

    /** Amenities that make daily life easier for someone with these [tags]. */
    fun wantedAmenities(tags: Set<LifestyleTag>): Set<Amenity> = tags.flatMapTo(LinkedHashSet()) { wanted(it) }

    private fun wanted(tag: LifestyleTag): List<Amenity> =
        when (tag) {
            LifestyleTag.Quiet, LifestyleTag.PetOwner, LifestyleTag.Nature -> listOf(Amenity.Garden)
            LifestyleTag.Family -> listOf(Amenity.PlayArea, Amenity.Security)
            LifestyleTag.RemoteWorker -> listOf(Amenity.Wifi, Amenity.HomeOffice, Amenity.PowerBackup)
            LifestyleTag.Student -> listOf(Amenity.Wifi, Amenity.WashingMachine)
            LifestyleTag.Nightlife -> listOf(Amenity.AirConditioning)
        }

    fun satisfies(
        mustHave: MustHave,
        property: Property,
    ): Boolean =
        when (mustHave) {
            MustHave.Parking -> property.parking
            MustHave.Furnished -> property.furnished
            MustHave.Balcony -> Amenity.Balcony in property.amenities
            MustHave.Verified -> property.verified
        }
}
