package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

/** A market Findora ships sample data for, with its currency, locale and map framing. */
@Immutable
data class RegionPack(
    val id: String,
    val name: String,
    val countryCode: String,
    val currency: Currency,
    val localeTag: String,
    val isDefault: Boolean,
    val center: GeoPoint,
    val defaultZoom: Double,
    val bounds: GeoBounds,
    val localities: List<Locality>,
) {
    companion object {
        const val DEFAULT_REGION_ID = "kerala"
    }
}

@Immutable
data class Locality(
    val name: String,
    val location: GeoPoint,
)
