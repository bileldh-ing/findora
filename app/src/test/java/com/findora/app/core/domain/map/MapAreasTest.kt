package com.findora.app.core.domain.map

import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.testing.seedProperties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapAreasTest {
    private val kerala = seedProperties.filter { it.regionId == "kerala" }

    @Test
    fun withoutAnAreaEveryListingAcceptedByTheFilterIsShownWestToEast() {
        val visible = MapAreas.visibleListings(kerala, PropertyFilter(), area = null)
        assertEquals(kerala.map { it.id }.toSet(), visible.map { it.id }.toSet())
        assertEquals(visible.sortedBy { it.location.longitude }.map { it.id }, visible.map { it.id })
    }

    @Test
    fun anAreaKeepsOnlyListingsInsideItsBounds() {
        val sorted = kerala.sortedBy { it.location.latitude }
        val pivot = sorted[sorted.size / 2].location.latitude
        val southHalf = GeoBounds(south = 0.0, west = 70.0, north = pivot, east = 80.0)
        val visible = MapAreas.visibleListings(kerala, PropertyFilter(), southHalf)
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.all { it.location.latitude <= pivot })
        assertEquals(kerala.count { it.location.latitude <= pivot }, visible.size)
    }

    @Test
    fun boundsAreInclusiveOnTheirEdges() {
        val home = kerala.first()
        val point = home.location
        val exact = GeoBounds(point.latitude, point.longitude, point.latitude, point.longitude)
        assertEquals(listOf(home.id), MapAreas.visibleListings(kerala, PropertyFilter(), exact).map { it.id })
    }

    @Test
    fun theSharedFilterAndTheAreaBothApply() {
        val filter = PropertyFilter(type = PropertyType.Apartment, minBedrooms = 2)
        val everywhere = GeoBounds(-80.0, -180.0, 80.0, 180.0)
        val visible = MapAreas.visibleListings(kerala, filter, everywhere)
        assertEquals(kerala.count { filter.accepts(it) }, visible.size)
        assertTrue(visible.all { it.type == PropertyType.Apartment && it.bedrooms >= 2 })
    }

    @Test
    fun anAreaElsewhereShowsNothing() {
        val paris = GeoBounds(48.8, 2.22, 48.91, 2.47)
        assertTrue(MapAreas.visibleListings(kerala, PropertyFilter(), paris).isEmpty())
    }

    @Test
    fun boundsOfClampsLatitudesAndWidensAViewThatWrapsTheAntimeridian() {
        val normal = MapAreas.boundsOf(north = 10.2, east = 76.5, south = 9.8, west = 76.1)
        assertEquals(GeoBounds(9.8, 76.1, 10.2, 76.5), normal)
        val wrapped = MapAreas.boundsOf(north = 89.0, east = -170.0, south = -89.0, west = 170.0)
        assertEquals(-180.0, wrapped.west, 0.0)
        assertEquals(180.0, wrapped.east, 0.0)
        assertTrue(wrapped.north < 85.1 && wrapped.south > -85.1)
    }

    @Test
    fun aViewportMovedFarEnoughOrZoomedCountsAsMoved() {
        val base = viewport(center = GeoPoint(10.0, 76.0), zoom = 12.0)
        assertFalse(viewport(GeoPoint(10.01, 76.01), 12.0).movedFrom(base))
        assertTrue(viewport(GeoPoint(10.05, 76.0), 12.0).movedFrom(base))
        assertTrue(viewport(GeoPoint(10.0, 76.06), 12.0).movedFrom(base))
        assertTrue(viewport(GeoPoint(10.0, 76.0), 12.6).movedFrom(base))
        assertFalse(viewport(GeoPoint(10.0, 76.0), 12.2).movedFrom(base))
    }

    /** A viewport 0.2 degrees across around [center]. */
    private fun viewport(
        center: GeoPoint,
        zoom: Double,
    ) = MapViewport(
        bounds =
            GeoBounds(
                center.latitude - 0.1,
                center.longitude - 0.1,
                center.latitude + 0.1,
                center.longitude + 0.1,
            ),
        center = center,
        zoom = zoom,
    )
}
