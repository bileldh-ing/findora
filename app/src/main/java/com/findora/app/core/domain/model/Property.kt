package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/**
 * A rental listing. Seed listings are fictional sample data ([isSampleData]). A listing published on this device by
 * one of its users carries that user's id in [ownerId] (null for market listings) and may state a [deposit].
 */
@Immutable
data class Property(
    val id: String,
    val title: String,
    val type: PropertyType,
    val rent: Money,
    val bedrooms: Int,
    val bathrooms: Int,
    val areaSqm: Int,
    val floor: Int,
    val furnished: Boolean,
    val petFriendly: Boolean,
    val parking: Boolean,
    val amenities: List<Amenity>,
    val tags: List<String>,
    val rating: Double,
    val reviewsCount: Int,
    val verified: Boolean,
    val location: GeoPoint,
    val locality: String,
    val regionId: String,
    val description: String,
    val agent: Agent,
    val images: List<PropertyImage>,
    val videoUrl: String?,
    val panoramaUrl: String?,
    val createdAt: Instant,
    val priceHistory: List<PricePoint>,
    val isSampleData: Boolean,
    val ownerId: String? = null,
    val deposit: Money? = null,
) {
    val coverImage: PropertyImage get() = images.first()

    /** True when [viewerId] published this listing ("Listed by you"). */
    fun isListedBy(viewerId: String?): Boolean = ownerId != null && ownerId == viewerId
}
