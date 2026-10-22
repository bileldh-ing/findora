package com.findora.app.core.data.local

import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PropertyQueryBuilderTest {
    @Test
    fun defaultFilterOnlyScopesTheRegionAndRanksBestMatchFirst() {
        val query = PropertyQueryBuilder.build("kerala", PropertyFilter())
        assertEquals(
            "SELECT * FROM properties WHERE region_id = ? ORDER BY verified DESC, rating DESC, reviews_count DESC, id ASC",
            query.sql,
        )
        assertEquals(listOf<Any>("kerala"), query.args)
    }

    @Test
    fun everyRefinementBecomesABoundClauseInOrder() {
        val filter =
            PropertyFilter(
                type = PropertyType.Apartment,
                query = "  kochi ",
                minRent = 10_000,
                maxRent = 30_000,
                minBedrooms = 2,
                minBathrooms = 1,
                minAreaSqm = 40,
                maxAreaSqm = 120,
                furnishedOnly = true,
                petFriendlyOnly = true,
                parkingOnly = true,
                verifiedOnly = true,
                amenities = setOf(Amenity.Wifi, Amenity.Gym),
            )
        val query = PropertyQueryBuilder.build("kerala", filter)
        val where = query.sql.substringAfter("WHERE ").substringBefore(" ORDER BY").split(" AND ")
        assertEquals(
            listOf(
                "region_id = ?",
                "type = ?",
                "(title LIKE ? ESCAPE '\\' OR locality LIKE ? ESCAPE '\\')",
                "rent_amount >= ?",
                "rent_amount <= ?",
                "bedrooms >= ?",
                "bathrooms >= ?",
                "area_sqm >= ?",
                "area_sqm <= ?",
                "furnished = 1",
                "pet_friendly = 1",
                "parking = 1",
                "verified = 1",
                "amenities LIKE ?",
                "amenities LIKE ?",
            ),
            where,
        )
        val amenityArgs = listOf(Amenity.Gym, Amenity.Wifi).sortedBy { it.id }.map { "%\"${it.id}\"%" }
        assertEquals(
            listOf<Any>("kerala", "apartment", "%kochi%", "%kochi%", 10_000L, 30_000L, 2, 1, 40, 120) + amenityArgs,
            query.args,
        )
        // Placeholders and arguments always line up: nothing user-typed is concatenated into the SQL.
        assertEquals(query.args.size, query.sql.count { it == '?' })
        assertFalse(query.sql.contains("kochi"))
    }

    @Test
    fun blankSearchTextAddsNoClause() {
        val query = PropertyQueryBuilder.build("kerala", PropertyFilter(query = "   "))
        assertFalse(query.sql.contains("LIKE"))
    }

    @Test
    fun likeWildcardsInUserTextAreEscaped() {
        assertEquals("100\\% \\_ok\\\\", PropertyQueryBuilder.escapeLike("100% _ok\\"))
        val query = PropertyQueryBuilder.build("kerala", PropertyFilter(query = "50%"))
        assertTrue(query.args.contains("%50\\%%"))
    }

    @Test
    fun eachSortOrderHasADeterministicTieBreaker() {
        SortOrder.entries.forEach { sort ->
            val orderBy = PropertyQueryBuilder.orderBy(sort)
            assertTrue("$sort ends with id", orderBy.endsWith("id ASC"))
        }
        assertTrue(PropertyQueryBuilder.orderBy(SortOrder.PriceLowToHigh).startsWith("rent_amount ASC"))
        assertTrue(PropertyQueryBuilder.orderBy(SortOrder.PriceHighToLow).startsWith("rent_amount DESC"))
        assertTrue(PropertyQueryBuilder.orderBy(SortOrder.Newest).startsWith("created_at DESC"))
        assertTrue(PropertyQueryBuilder.orderBy(SortOrder.Rating).startsWith("rating DESC"))
    }
}
