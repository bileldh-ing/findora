package com.findora.app.core.data.seed

import kotlinx.serialization.Serializable

/** Wire format of `assets/seed/listings.json`, also used as the fake remote API payload. */
@Serializable
data class SeedListingsDto(
    val schemaVersion: Int,
    val generatedOn: String,
    val sampleData: Boolean,
    val notice: String,
    val listings: List<ListingDto>,
)

@Serializable
data class ListingDto(
    val id: String,
    val title: String,
    val type: String,
    val rentPerMonth: Long,
    val currency: String,
    val bedrooms: Int,
    val bathrooms: Int,
    val areaSqm: Int,
    val floor: Int,
    val furnished: Boolean,
    val petFriendly: Boolean,
    val parking: Boolean,
    val amenities: List<String>,
    val tags: List<String>,
    val rating: Double,
    val reviewsCount: Int,
    val verified: Boolean,
    val latitude: Double,
    val longitude: Double,
    val locality: String,
    val region: String,
    val description: String,
    val agent: AgentDto,
    val images: List<ImageDto>,
    val videoUrl: String? = null,
    val panoramaUrl: String? = null,
    val createdAt: String,
    val priceHistory: List<PricePointDto>,
    val sampleData: Boolean = true,
)

@Serializable
data class AgentDto(
    val id: String,
    val name: String,
    val phone: String,
    val avatar: AvatarDto,
    val responseTimeMinutes: Int,
)

@Serializable
data class AvatarDto(
    val initials: String,
    val background: String,
)

@Serializable
data class ImageDto(
    val url: String,
    val width: Int,
    val height: Int,
    val dominantColor: String,
    val alt: String,
    val category: String,
    val photographer: String,
    val photographerUrl: String,
    val sourceUrl: String,
    val licence: String,
    val licenceUrl: String,
    val offlineAsset: String? = null,
    val fallbackAsset: String,
)

@Serializable
data class PricePointDto(
    val month: String,
    val amount: Long,
)

/** Wire format of `assets/seed/regions.json`. */
@Serializable
data class SeedRegionsDto(
    val schemaVersion: Int,
    val sampleData: Boolean,
    val defaultRegionId: String,
    val regions: List<RegionDto>,
)

@Serializable
data class RegionDto(
    val id: String,
    val name: String,
    val country: String,
    val currency: String,
    val locale: String,
    val isDefault: Boolean,
    val center: LatLngDto,
    val defaultZoom: Double,
    val bounds: BoundsDto,
    val localities: List<LocalityDto>,
)

@Serializable
data class LatLngDto(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
data class BoundsDto(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
)

@Serializable
data class LocalityDto(
    val name: String,
    val latitude: Double,
    val longitude: Double,
)
