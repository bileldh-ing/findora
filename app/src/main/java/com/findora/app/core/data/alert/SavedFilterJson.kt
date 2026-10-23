package com.findora.app.core.data.alert

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SortOrder
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * The stored form of a [PropertyFilter] (saved_searches.filter_json). Enums are kept as their stable ids or names,
 * and anything unknown (written by a newer version) is dropped instead of failing, so a saved search always opens.
 */
@Serializable
internal data class SavedFilterDto(
    val type: String? = null,
    val minRent: Long? = null,
    val maxRent: Long? = null,
    val minBedrooms: Int? = null,
    val verifiedOnly: Boolean = false,
    val query: String = "",
    val minBathrooms: Int? = null,
    val minAreaSqm: Int? = null,
    val maxAreaSqm: Int? = null,
    val furnishedOnly: Boolean = false,
    val petFriendlyOnly: Boolean = false,
    val parkingOnly: Boolean = false,
    val amenities: List<String> = emptyList(),
    val sort: String = SortOrder.Recommended.name,
)

object SavedFilterJson {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }

    fun encode(filter: PropertyFilter): String =
        json.encodeToString(
            SavedFilterDto.serializer(),
            SavedFilterDto(
                type = filter.type?.id,
                minRent = filter.minRent,
                maxRent = filter.maxRent,
                minBedrooms = filter.minBedrooms,
                verifiedOnly = filter.verifiedOnly,
                query = filter.query,
                minBathrooms = filter.minBathrooms,
                minAreaSqm = filter.minAreaSqm,
                maxAreaSqm = filter.maxAreaSqm,
                furnishedOnly = filter.furnishedOnly,
                petFriendlyOnly = filter.petFriendlyOnly,
                parkingOnly = filter.parkingOnly,
                amenities = filter.amenities.map { it.id }.sorted(),
                sort = filter.sort.name,
            ),
        )

    /** The filter in [text]; a malformed or inconsistent value decodes to the default (unfiltered) search. */
    fun decode(text: String): PropertyFilter =
        try {
            json.decodeFromString(SavedFilterDto.serializer(), text).toFilter()
        } catch (ignored: SerializationException) {
            PropertyFilter()
        } catch (ignored: IllegalArgumentException) {
            // Also covers a stored range PropertyFilter rejects (min above max).
            PropertyFilter()
        }

    private fun SavedFilterDto.toFilter(): PropertyFilter =
        PropertyFilter(
            type = type?.let { id -> PropertyType.entries.firstOrNull { it.id == id } },
            minRent = minRent,
            maxRent = maxRent,
            minBedrooms = minBedrooms,
            verifiedOnly = verifiedOnly,
            query = query,
            minBathrooms = minBathrooms,
            minAreaSqm = minAreaSqm,
            maxAreaSqm = maxAreaSqm,
            furnishedOnly = furnishedOnly,
            petFriendlyOnly = petFriendlyOnly,
            parkingOnly = parkingOnly,
            amenities = amenities.mapNotNullTo(LinkedHashSet()) { id -> Amenity.entries.firstOrNull { it.id == id } },
            sort = SortOrder.entries.firstOrNull { it.name == sort } ?: SortOrder.Recommended,
        )
}
