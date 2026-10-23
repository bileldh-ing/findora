package com.findora.app.core.domain.compare

import com.findora.app.core.data.repository.InMemoryCompareRepository
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.Property
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CompareTableTest {
    private val base = seedProperties.first()

    private fun home(
        id: String,
        rent: Long,
        area: Int,
        beds: Int = 2,
        baths: Int = 1,
        rating: Double = 4.0,
        amenities: List<Amenity> = emptyList(),
        currency: Currency = base.rent.currency,
    ): Property =
        base.copy(
            id = id,
            title = "Home $id",
            rent = Money(rent, currency),
            areaSqm = area,
            bedrooms = beds,
            bathrooms = baths,
            rating = rating,
            amenities = amenities,
        )

    @Test
    fun highlightsTheBestValueOfEveryRowInItsOwnDirection() {
        val a =
            home("a", rent = 20_000, area = 100, beds = 3, baths = 2, rating = 4.2, amenities = listOf(Amenity.Wifi))
        val b = home("b", rent = 15_000, area = 50, beds = 2, baths = 2, rating = 4.8)
        val c =
            home(
                "c",
                rent = 18_000,
                area = 120,
                beds = 1,
                baths = 1,
                rating = 3.9,
                amenities = listOf(Amenity.Wifi, Amenity.Gym, Amenity.Lift),
            )
        val table = CompareTable.build(listOf(a, b, c), matchScores = mapOf("a" to 80, "b" to 65, "c" to 91))
        assertEquals(setOf(1), table.row(CompareMetric.Price)?.best) // cheapest
        assertEquals(setOf(2), table.row(CompareMetric.Area)?.best) // largest
        // 20000/100 = 200, 15000/50 = 300, 18000/120 = 150 per m²: lowest wins.
        assertEquals(setOf(2), table.row(CompareMetric.PricePerSqm)?.best)
        assertEquals(150.0, table.row(CompareMetric.PricePerSqm)!!.values[2]!!, 1e-9)
        assertEquals(setOf(0), table.row(CompareMetric.Bedrooms)?.best)
        assertEquals(setOf(0, 1), table.row(CompareMetric.Bathrooms)?.best) // ties share the highlight
        assertEquals(setOf(2), table.row(CompareMetric.Amenities)?.best)
        assertEquals(setOf(1), table.row(CompareMetric.Rating)?.best)
        assertEquals(setOf(2), table.row(CompareMetric.Match)?.best)
        assertEquals(listOf(Amenity.Wifi, Amenity.Lift, Amenity.Gym), table.amenities)
        assertTrue(table.hasAmenity(2, Amenity.Gym))
        assertFalse(table.hasAmenity(1, Amenity.Wifi))
    }

    @Test
    fun noHighlightWhenAllEqualOrOnlyOneColumn() {
        val a = home("a", rent = 10_000, area = 80)
        val b = home("b", rent = 10_000, area = 80)
        val equal = CompareTable.build(listOf(a, b))
        equal.rows.forEach { assertTrue("${it.metric} should not highlight", it.best.isEmpty()) }
        val single = CompareTable.build(listOf(a))
        single.rows.forEach { assertTrue(it.best.isEmpty()) }
    }

    @Test
    fun matchRowOnlyWithAProfileAndUnknownValuesAreSkipped() {
        val a = home("a", rent = 10_000, area = 0)
        val b = home("b", rent = 12_000, area = 60)
        val c = home("c", rent = 11_000, area = 50)
        val table = CompareTable.build(listOf(a, b, c))
        assertNull(table.row(CompareMetric.Match))
        val perSqm = table.row(CompareMetric.PricePerSqm)!!
        assertNull(perSqm.values[0]) // no area, no price per m²
        assertEquals(setOf(1), perSqm.best) // 200 vs 220
        val partial = CompareTable.build(listOf(a, b), matchScores = mapOf("a" to 70))
        assertTrue(partial.row(CompareMetric.Match)!!.best.isEmpty()) // one known value only
    }

    @Test
    fun pricesInDifferentCurrenciesAreNeverRanked() {
        val a = home("a", rent = 900, area = 60, currency = Currency.entries.first())
        val b = home("b", rent = 50_000, area = 60, currency = Currency.entries.last())
        val table = CompareTable.build(listOf(a, b))
        assertTrue(table.row(CompareMetric.Price)!!.best.isEmpty())
        assertTrue(table.row(CompareMetric.PricePerSqm)!!.best.isEmpty())
    }

    @Test
    fun selectionKeepsOrderRefusesDuplicatesAndCapsAtThree() {
        var selection = emptyList<String>()
        listOf("a", "b", "c").forEach { id ->
            val (next, result) = CompareSelection.add(selection, id)
            assertEquals(CompareAddResult.Added, result)
            selection = next
        }
        assertEquals(CompareAddResult.AlreadyAdded, CompareSelection.add(selection, "b").second)
        val (full, result) = CompareSelection.add(selection, "d")
        assertEquals(CompareAddResult.Full, result)
        assertEquals(listOf("a", "b", "c"), full)
        assertEquals(listOf("b", "c", "a"), CompareSelection.move(selection, 0, 2))
        assertEquals(listOf("c", "a", "b"), CompareSelection.move(selection, 2, -5)) // clamped
        assertEquals(selection, CompareSelection.move(selection, 7, 0)) // unknown column
    }

    @Test
    fun repositoryKeepsOneSelectionPerUser() =
        runTest {
            val repository = InMemoryCompareRepository()
            assertEquals(CompareAddResult.Added, repository.add("u1", "a"))
            repository.add("u1", "b")
            repository.add("u2", "c")
            repository.move("u1", 1, 0)
            assertEquals(listOf("b", "a"), repository.observe("u1").first())
            assertEquals(listOf("c"), repository.observe("u2").first())
            repository.remove("u1", "b")
            assertEquals(listOf("a"), repository.observe("u1").first())
            repository.clear("u1")
            assertEquals(emptyList<String>(), repository.observe("u1").first())
            assertEquals(listOf("c"), repository.observe("u2").first())
        }
}
