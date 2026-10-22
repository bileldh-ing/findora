package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable
import java.time.Instant

/** A rental listing. Seed listings are fictional sample data ([isSampleData]). */
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
) {
    val coverImage: PropertyImage get() = images.first()
}
