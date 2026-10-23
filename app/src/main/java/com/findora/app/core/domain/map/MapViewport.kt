package com.findora.app.core.domain.map

import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyFilter
import kotlin.math.abs

/** What the map shows right now: the visible box, its centre and the zoom level. */
data class MapViewport(
    val bounds: GeoBounds,
    val center: GeoPoint,
    val zoom: Double,
) {
    /**
     * True when the user has moved far enough from [baseline] that the visible listings may differ: the centre
     * moved by more than [panFraction] of the baseline's span, or the zoom changed by at least [zoomStep].
     */
    fun movedFrom(
        baseline: MapViewport,
        panFraction: Double = DEFAULT_PAN_FRACTION,
        zoomStep: Double = DEFAULT_ZOOM_STEP,
    ): Boolean {
        val latSpan = (baseline.bounds.north - baseline.bounds.south).coerceAtLeast(MIN_SPAN)
        val lonSpan = (baseline.bounds.east - baseline.bounds.west).coerceAtLeast(MIN_SPAN)
        val panned =
            abs(center.latitude - baseline.center.latitude) / latSpan > panFraction ||
                abs(center.longitude - baseline.center.longitude) / lonSpan > panFraction
        return panned || abs(zoom - baseline.zoom) >= zoomStep
    }

    companion object {
        const val DEFAULT_PAN_FRACTION = 0.15
        const val DEFAULT_ZOOM_STEP = 0.5
        private const val MIN_SPAN = 1e-6
    }
}

/** Builders and filters for map areas; pure Kotlin so the "Search this area" rules are unit-tested. */
object MapAreas {
    private const val MAX_LATITUDE = 85.05112878
    private const val MAX_LONGITUDE = 180.0

    /**
     * A [GeoBounds] from raw map edges. Latitudes are clamped to the projection; when the view wraps the
     * antimeridian (west > east, only possible fully zoomed out) the whole longitude range is used.
     */
    fun boundsOf(
        north: Double,
        east: Double,
        south: Double,
        west: Double,
    ): GeoBounds {
        val top = north.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val bottom = south.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)
        val wraps = west > east
        return GeoBounds(
            south = minOf(top, bottom),
            west = if (wraps) -MAX_LONGITUDE else west.coerceIn(-MAX_LONGITUDE, MAX_LONGITUDE),
            north = maxOf(top, bottom),
            east = if (wraps) MAX_LONGITUDE else east.coerceIn(-MAX_LONGITUDE, MAX_LONGITUDE),
        )
    }

    /**
     * The listings the map shows: those accepted by the shared Home [filter] (the same rules as the SQL feed)
     * and, when an [area] was searched, located inside it. Ordered west to east so swiping the card carousel
     * walks across the map instead of jumping around it.
     */
    fun visibleListings(
        listings: List<Property>,
        filter: PropertyFilter,
        area: GeoBounds?,
    ): List<Property> =
        listings
            .filter { filter.accepts(it) && (area == null || it.location in area) }
            .sortedWith(compareBy({ it.location.longitude }, { it.id }))
}
