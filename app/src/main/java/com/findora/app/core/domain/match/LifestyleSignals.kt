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
        val tags = property.tags.map { it.lowercase() }
        val amenities = property.amenities.toSet()

        fun hasTag(vararg words: String) = tags.any { text -> words.any { it in text } }
        return when (tag) {
            LifestyleTag.Quiet -> hasTag("quiet", "gated") || Amenity.Garden in amenities
            LifestyleTag.Family ->
                hasTag("family", "school") || Amenity.PlayArea in amenities || property.bedrooms >= FAMILY_BEDROOMS
            LifestyleTag.PetOwner -> property.petFriendly
            LifestyleTag.RemoteWorker ->
                Amenity.HomeOffice in amenities || (Amenity.Wifi in amenities && Amenity.PowerBackup in amenities)
            LifestyleTag.Student -> hasTag("budget", "metro", "cafe") || property.type == PropertyType.Room
            LifestyleTag.Nightlife -> hasTag("city living", "cafe", "mall", "market")
            LifestyleTag.Nature ->
                hasTag("park", "waterfront", "sea view") ||
                    amenities.any { it == Amenity.Garden || it == Amenity.SeaView || it == Amenity.BackwaterView }
        }
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
