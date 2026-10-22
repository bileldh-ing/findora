package com.findora.app.core.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
)

/** Axis-aligned bounding box; regions never cross the antimeridian. */
@Immutable
data class GeoBounds(
    val south: Double,
    val west: Double,
    val north: Double,
    val east: Double,
) {
    init {
        require(south <= north) { "south must not exceed north" }
        require(west <= east) { "west must not exceed east" }
    }

    operator fun contains(point: GeoPoint): Boolean = point.latitude in south..north && point.longitude in west..east
}
