package com.findora.app.core.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Cached listing. Lists (images, amenities, history) are stored as JSON columns via [Converters]. [ownerId] is set on
 * listings published by a user of this device (never replaced or removed by a market refresh), null otherwise.
 */
@Entity(
    tableName = "properties",
    indices = [
        Index(value = ["region_id", "created_at"]),
        Index(value = ["region_id", "rent_amount"]),
        Index(value = ["type"]),
        Index(value = ["owner_id"]),
    ],
)
data class PropertyEntity(
    @PrimaryKey val id: String,
    val title: String,
    val type: String,
    @ColumnInfo(name = "rent_amount") val rentAmount: Long,
    val currency: String,
    val bedrooms: Int,
    val bathrooms: Int,
    @ColumnInfo(name = "area_sqm") val areaSqm: Int,
    val floor: Int,
    val furnished: Boolean,
    @ColumnInfo(name = "pet_friendly") val petFriendly: Boolean,
    val parking: Boolean,
    val amenities: List<String>,
    val tags: List<String>,
    val rating: Double,
    @ColumnInfo(name = "reviews_count") val reviewsCount: Int,
    val verified: Boolean,
    val latitude: Double,
    val longitude: Double,
    val locality: String,
    @ColumnInfo(name = "region_id") val regionId: String,
    val description: String,
    @Embedded(prefix = "agent_") val agent: AgentColumns,
    val images: List<ImageColumns>,
    @ColumnInfo(name = "video_url") val videoUrl: String?,
    @ColumnInfo(name = "panorama_url") val panoramaUrl: String?,
    @ColumnInfo(name = "created_at") val createdAtMillis: Long,
    @ColumnInfo(name = "price_history") val priceHistory: List<PricePointColumns>,
    @ColumnInfo(name = "sample_data") val sampleData: Boolean,
    @ColumnInfo(name = "fetched_at") val fetchedAtMillis: Long,
    @ColumnInfo(name = "owner_id") val ownerId: String? = null,
    @ColumnInfo(name = "deposit_amount") val depositAmount: Long? = null,
)

data class AgentColumns(
    val id: String,
    val name: String,
    val phone: String,
    val initials: String,
    @ColumnInfo(name = "avatar_argb") val avatarArgb: Long,
    @ColumnInfo(name = "response_minutes") val responseTimeMinutes: Int,
)

@Serializable
data class ImageColumns(
    val url: String,
    val width: Int,
    val height: Int,
    val dominantColorArgb: Long,
    val alt: String,
    val photographer: String,
    val photographerUrl: String,
    val sourceUrl: String,
    val licence: String,
    val licenceUrl: String,
    val offlineAsset: String? = null,
    val fallbackAsset: String,
)

@Serializable
data class PricePointColumns(
    val month: String,
    val amount: Long,
)
