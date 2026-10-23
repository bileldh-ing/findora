package com.findora.app.core.data.owner

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingPhoto
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Versioned JSON of a [ListingDraft]. Unknown types, amenities or currencies (from a newer app) are dropped instead of
 * failing, and an unreadable document reads as an empty draft, so a draft can never crash the wizard.
 */
object OwnerDraftJson {
    const val VERSION = 1
    private val json =
        Json {
            ignoreUnknownKeys = true
            // The version (a default) is always written; unset optional fields are left out.
            encodeDefaults = true
            explicitNulls = false
        }

    fun encode(draft: ListingDraft): String = json.encodeToString(DraftDocument.serializer(), draft.toDocument())

    fun decode(text: String): ListingDraft =
        try {
            json.decodeFromString(DraftDocument.serializer(), text).toDraft()
        } catch (ignored: SerializationException) {
            ListingDraft()
        } catch (ignored: IllegalArgumentException) {
            ListingDraft()
        }

    @Serializable
    private data class DraftDocument(
        val version: Int = VERSION,
        val type: String? = null,
        val title: String = "",
        val description: String = "",
        val regionId: String? = null,
        val locality: String = "",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val bedrooms: Int? = null,
        val bathrooms: Int? = null,
        val areaSqm: Int? = null,
        val floor: Int? = null,
        val furnished: Boolean = false,
        val petFriendly: Boolean = false,
        val parking: Boolean = false,
        val amenities: List<String> = emptyList(),
        val photos: List<PhotoDocument> = emptyList(),
        val rent: Long? = null,
        val currency: String? = null,
        val deposit: Long? = null,
    )

    @Serializable
    private data class PhotoDocument(
        val path: String,
        val width: Int,
        val height: Int,
        val color: Long,
    )

    private fun ListingDraft.toDocument() =
        DraftDocument(
            type = type?.id,
            title = title,
            description = description,
            regionId = regionId,
            locality = locality,
            latitude = pin?.latitude,
            longitude = pin?.longitude,
            bedrooms = bedrooms,
            bathrooms = bathrooms,
            areaSqm = areaSqm,
            floor = floor,
            furnished = furnished,
            petFriendly = petFriendly,
            parking = parking,
            amenities = amenities.sortedBy { it.ordinal }.map(Amenity::id),
            photos = photos.map { PhotoDocument(it.path, it.width, it.height, it.dominantColorArgb) },
            rent = rent,
            currency = currency?.code,
            deposit = deposit,
        )

    private fun DraftDocument.toDraft(): ListingDraft {
        val lat = latitude
        val lng = longitude
        return ListingDraft(
            type = PropertyType.entries.firstOrNull { it.id == type },
            title = title,
            description = description,
            regionId = regionId,
            locality = locality,
            pin = if (lat != null && lng != null) GeoPoint(lat, lng) else null,
            bedrooms = bedrooms,
            bathrooms = bathrooms,
            areaSqm = areaSqm,
            floor = floor,
            furnished = furnished,
            petFriendly = petFriendly,
            parking = parking,
            amenities = amenities.mapNotNull(Amenity::fromIdOrNull).toSet(),
            photos = photos.map { ListingPhoto(it.path, it.width, it.height, it.color) },
            rent = rent,
            currency = Currency.entries.firstOrNull { it.code == currency },
            deposit = deposit,
        )
    }
}
