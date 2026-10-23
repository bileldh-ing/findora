package com.findora.app.core.domain.map

import com.findora.app.core.domain.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridClustererTest {
    private data class Pin(
        val id: String,
        val point: GeoPoint,
    )

    private fun cluster(
        pins: List<Pin>,
        zoom: Double,
        cellSizePx: Int = GridClusterer.DEFAULT_CELL_SIZE_PX,
    ) = GridClusterer.cluster(pins, zoom, Pin::id, Pin::point, cellSizePx)

    // Two homes ~150 m apart in Kochi and one ~20 km away.
    private val near1 = Pin("a", GeoPoint(9.9816, 76.2999))
    private val near2 = Pin("b", GeoPoint(9.9826, 76.3009))
    private val far = Pin("c", GeoPoint(10.1500, 76.4000))

    @Test
    fun emptyInputGivesNoClusters() {
        assertTrue(cluster(emptyList(), zoom = 12.0).isEmpty())
    }

    @Test
    fun zoomedOutMergesNearbyHomesButKeepsDistantOnesApart() {
        val clusters = cluster(listOf(far, near2, near1), zoom = 11.0)
        assertEquals(2, clusters.size)
        val group = clusters.single { !it.isSingle }
        assertEquals(listOf("a", "b"), group.items.map { it.id })
        assertEquals("a", group.key)
        assertEquals(9.9821, group.center.latitude, 1e-9)
        assertEquals(76.3004, group.center.longitude, 1e-9)
        assertEquals(9.9816, group.bounds.south, 1e-9)
        assertEquals(76.3009, group.bounds.east, 1e-9)
        assertEquals("c", clusters.single { it.isSingle }.key)
    }

    @Test
    fun zoomingInSplitsAClusterIntoSingles() {
        val clusters = cluster(listOf(near1, near2, far), zoom = 15.5)
        assertEquals(3, clusters.size)
        assertTrue(clusters.all { it.isSingle })
    }

    @Test
    fun atOrAboveTheMaxClusterZoomNothingIsMergedEvenForIdenticalPoints() {
        val twin = Pin("z", near1.point)
        val clusters = cluster(listOf(twin, near1), zoom = GridClusterer.DEFAULT_MAX_CLUSTER_ZOOM)
        assertEquals(listOf("a", "z"), clusters.map { it.key })
    }

    @Test
    fun theWholeWorldFitsInOneCellAtZoomZeroWithAHugeCell() {
        val paris = Pin("p", GeoPoint(48.8566, 2.3522))
        val dubai = Pin("d", GeoPoint(25.2048, 55.2708))
        val clusters = cluster(listOf(paris, dubai, near1), zoom = 0.0, cellSizePx = 256)
        assertEquals(1, clusters.size)
        assertEquals(3, clusters.single().size)
    }

    @Test
    fun outputIsDeterministicWhateverTheInputOrder() {
        val pins = listOf(near1, near2, far, Pin("d", GeoPoint(9.90, 76.25)))
        val expected = cluster(pins, zoom = 11.0)
        repeat(5) { seed ->
            assertEquals(expected, cluster(pins.shuffled(kotlin.random.Random(seed)), zoom = 11.0))
        }
    }

    @Test
    fun mercatorProjectionMatchesKnownPixels() {
        val world = 256.0
        assertEquals(128.0, GridClusterer.projectX(0.0, world), 1e-9)
        assertEquals(0.0, GridClusterer.projectX(-180.0, world), 1e-9)
        assertEquals(128.0, GridClusterer.projectY(0.0, world), 1e-9)
        // Latitudes beyond the projection's limit are clamped to the top and bottom edges.
        assertEquals(0.0, GridClusterer.projectY(90.0, world), 1e-6)
        assertEquals(world, GridClusterer.projectY(-90.0, world), 1e-6)
    }

    @Test(expected = IllegalArgumentException::class)
    fun cellSizeMustBePositive() {
        cluster(listOf(near1), zoom = 10.0, cellSizePx = 0)
    }
}
