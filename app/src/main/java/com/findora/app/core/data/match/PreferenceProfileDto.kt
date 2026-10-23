package com.findora.app.core.data.match

import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.LocationAnchor
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import kotlinx.serialization.Serializable

/**
 * Stored shape of a [PreferenceProfile]. Enums are kept by their stable ids; ids this version does not know
 * (written by a newer app) are dropped rather than failing the whole profile.
 */
@Serializable
internal data class PreferenceProfileDto(
    val version: Int = CURRENT_VERSION,
    val budgetMin: Long? = null,
    val budgetMax: Long? = null,
    val currency: String? = null,
    val minBedrooms: Int? = null,
    val minBathrooms: Int? = null,
    val types: List<String> = emptyList(),
    val anchors: List<AnchorDto> = emptyList(),
    val lifestyle: List<String> = emptyList(),
    val mustHaves: List<String> = emptyList(),
) {
    @Serializable
    data class AnchorDto(
        val name: String,
        val latitude: Double,
        val longitude: Double,
        val kind: String,
    )

    fun toDomain(): PreferenceProfile =
        PreferenceProfile(
            budget =
                if (budgetMax != null && currency != null) {
                    BudgetRange(budgetMin, budgetMax, Currency.fromCode(currency))
                } else {
                    null
                },
            minBedrooms = minBedrooms,
            minBathrooms = minBathrooms,
            propertyTypes =
                types.mapNotNullTo(LinkedHashSet()) { id -> PropertyType.entries.firstOrNull { it.id == id } },
            anchors =
                anchors.mapNotNull { anchor ->
                    LocationAnchor.Kind.entries.firstOrNull { it.name == anchor.kind }?.let { kind ->
                        LocationAnchor(anchor.name, GeoPoint(anchor.latitude, anchor.longitude), kind)
                    }
                },
            lifestyle = lifestyle.mapNotNullTo(LinkedHashSet(), LifestyleTag::fromIdOrNull),
            mustHaves = mustHaves.mapNotNullTo(LinkedHashSet(), MustHave::fromIdOrNull),
        )

    companion object {
        const val CURRENT_VERSION = 1

        fun from(profile: PreferenceProfile): PreferenceProfileDto =
            PreferenceProfileDto(
                budgetMin = profile.budget?.min,
                budgetMax = profile.budget?.max,
                currency = profile.budget?.currency?.code,
                minBedrooms = profile.minBedrooms,
                minBathrooms = profile.minBathrooms,
                types = profile.propertyTypes.map { it.id },
                anchors =
                    profile.anchors.map {
                        AnchorDto(it.name, it.point.latitude, it.point.longitude, it.kind.name)
                    },
                lifestyle = profile.lifestyle.map { it.id },
                mustHaves = profile.mustHaves.map { it.id },
            )
    }
}
