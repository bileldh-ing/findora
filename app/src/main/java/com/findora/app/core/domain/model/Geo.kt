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

private const val EARTH_RADIUS_KM = 6371.0

/** Great-circle (haversine) distance to [other] in kilometres. */
fun GeoPoint.distanceKmTo(other: GeoPoint): Double {
    val lat1 = Math.toRadians(latitude)
    val lat2 = Math.toRadians(other.latitude)
    val dLat = lat2 - lat1
    val dLon = Math.toRadians(other.longitude - longitude)
    val sinLat = kotlin.math.sin(dLat / 2)
    val sinLon = kotlin.math.sin(dLon / 2)
    val h = sinLat * sinLat + kotlin.math.cos(lat1) * kotlin.math.cos(lat2) * sinLon * sinLon
    return 2 * EARTH_RADIUS_KM * kotlin.math.asin(kotlin.math.sqrt(h.coerceIn(0.0, 1.0)))
}
