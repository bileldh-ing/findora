package com.findora.app.core.domain.map

import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan

/** One marker on the map: a single item, or several nearby items drawn as a count bubble. */
data class MapCluster<T>(
    val items: List<T>,
    val center: GeoPoint,
    val bounds: GeoBounds,
    /** Stable across recompositions while the members stay the same (the smallest member id). */
    val key: String,
) {
    val isSingle: Boolean get() = items.size == 1
    val size: Int get() = items.size
}

/**
 * Grid clustering in Web Mercator pixel space, the projection every slippy map (osmdroid included) draws in.
 * At a given zoom the world is `256 * 2^zoom` pixels wide; items whose projected pixels fall in the same
 * [cellSizePx] square become one cluster centred on their mean position. From [maxClusterZoom] up nothing is
 * merged, so every listing can be tapped. Pure Kotlin and deterministic (same input, same output order).
 */
object GridClusterer {
    const val DEFAULT_CELL_SIZE_PX = 96
    const val DEFAULT_MAX_CLUSTER_ZOOM = 16.0
    private const val TILE_SIZE = 256.0
    private const val MAX_LATITUDE = 85.05112878

    fun <T> cluster(
        items: List<T>,
        zoom: Double,
        id: (T) -> String,
        position: (T) -> GeoPoint,
        cellSizePx: Int = DEFAULT_CELL_SIZE_PX,
        maxClusterZoom: Double = DEFAULT_MAX_CLUSTER_ZOOM,
    ): List<MapCluster<T>> {
        require(cellSizePx > 0) { "cellSizePx must be positive" }
        if (items.isEmpty()) return emptyList()
        if (zoom >= maxClusterZoom) return items.map { single(it, id, position) }.sortedBy { it.key }
        val worldPx = TILE_SIZE * 2.0.pow(zoom)
        return items
            .groupBy { item ->
                val point = position(item)
                Cell(
                    x = floor(projectX(point.longitude, worldPx) / cellSizePx).toLong(),
                    y = floor(projectY(point.latitude, worldPx) / cellSizePx).toLong(),
                )
            }.entries
            .sortedWith(compareBy({ it.key.y }, { it.key.x }))
            .map { (_, members) -> merge(members, id, position) }
    }

    /** Horizontal Web Mercator pixel of [longitude] in a world [worldPx] wide. */
    fun projectX(
        longitude: Double,
        worldPx: Double,
    ): Double = (longitude + HALF_TURN) / FULL_TURN * worldPx

    /** Vertical Web Mercator pixel of [latitude] (clamped to the projection's limits), 0 at the north edge. */
    fun projectY(
        latitude: Double,
        worldPx: Double,
    ): Double {
        val radians = latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE) * PI / HALF_TURN
        return (1 - ln(tan(radians) + 1 / cos(radians)) / PI) / 2 * worldPx
    }

    private fun <T> single(
        item: T,
        id: (T) -> String,
        position: (T) -> GeoPoint,
    ): MapCluster<T> {
        val point = position(item)
        return MapCluster(
            listOf(item),
            point,
            GeoBounds(point.latitude, point.longitude, point.latitude, point.longitude),
            id(item),
        )
    }

    private fun <T> merge(
        members: List<T>,
        id: (T) -> String,
        position: (T) -> GeoPoint,
    ): MapCluster<T> {
        if (members.size == 1) return single(members.first(), id, position)
        val points = members.map(position)
        return MapCluster(
            items = members.sortedBy(id),
            center = GeoPoint(points.map { it.latitude }.average(), points.map { it.longitude }.average()),
            bounds =
                GeoBounds(
                    south = points.minOf { it.latitude },
                    west = points.minOf { it.longitude },
                    north = points.maxOf { it.latitude },
                    east = points.maxOf { it.longitude },
                ),
            key = members.minOf(id),
        )
    }

    private data class Cell(
        val x: Long,
        val y: Long,
    )

    private const val HALF_TURN = 180.0
    private const val FULL_TURN = 360.0
}
