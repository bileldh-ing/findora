package com.findora.app.core.data.seed

import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.testing.assetsDir
import com.findora.app.core.testing.fileSeedReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.Instant
import java.time.YearMonth

/** Guards the bundled sample dataset: every rule here mirrors scripts/build_seed validation. */
class SeedIntegrityTest {
    private val reader = fileSeedReader()
    private val seed = reader.readListings()
    private val regions = reader.readRegions()
    private val listings = seed.listings
    private val regionById = regions.regions.associateBy(RegionDto::id)

    @Test
    fun seedIsLabelledAsSampleData() {
        assertTrue(seed.sampleData)
        assertTrue(regions.sampleData)
        assertTrue(seed.notice.contains("Sample data"))
        assertTrue(listings.all(ListingDto::sampleData))
    }

    @Test
    fun hasAtLeastSixtyListingsWithUniqueIds() {
        assertTrue("found ${listings.size}", listings.size >= 60)
        assertEquals(listings.size, listings.map(ListingDto::id).toSet().size)
    }

    @Test
    fun containsTheFourDesignListings() {
        val expected =
            mapOf(
                "Georgie House" to "Kochi",
                "Max House" to "Edappally",
                "Luna Haven" to "Fort Kochi",
                "The Serene Nest" to "Kakkanad",
            )
        expected.forEach { (title, locality) ->
            val listing = listings.single { it.title == title }
            assertEquals(locality, listing.locality)
            assertEquals("kerala", listing.region)
            assertEquals("INR", listing.currency)
        }
    }

    @Test
    fun keralaIsTheDefaultRegionAndPacksUseLocalCurrencies() {
        assertEquals("kerala", regions.defaultRegionId)
        assertEquals(listOf("kerala"), regions.regions.filter(RegionDto::isDefault).map(RegionDto::id))
        val currencies = regions.regions.associate { it.id to it.currency }
        assertEquals(mapOf("kerala" to "INR", "tunis" to "TND", "paris" to "EUR", "dubai" to "AED"), currencies)
        regions.regions.forEach { region ->
            val bounds = region.toDomain().bounds
            assertTrue("${region.id} center", GeoPoint(region.center.latitude, region.center.longitude) in bounds)
            region.localities.forEach { assertTrue(it.name, GeoPoint(it.latitude, it.longitude) in bounds) }
        }
    }

    @Test
    fun everyListingHasValidFields() {
        listings.forEach { listing ->
            val where = listing.id
            val region = regionById.getValue(listing.region)
            PropertyType.fromId(listing.type)
            assertEquals(where, region.currency, Currency.fromCode(listing.currency).code)
            assertTrue(where, listing.title.isNotBlank() && listing.description.length > 40)
            assertTrue(where, listing.rating in 3.8..5.0)
            assertTrue(where, listing.reviewsCount > 0 && listing.rentPerMonth > 0 && listing.areaSqm > 0)
            assertTrue(where, listing.bathrooms >= 1 && listing.bedrooms >= 0 && listing.floor >= 0)
            assertTrue(where, listing.amenities.isNotEmpty() && listing.tags.isNotEmpty())
            assertTrue(where, Instant.parse(listing.createdAt).isBefore(Instant.parse("2026-10-23T00:00:00Z")))
            assertTrue(where, listing.agent.avatar.initials.length in 1..2)
            assertTrue(where, listing.agent.phone.startsWith("+"))
            assertTrue(where, listing.agent.responseTimeMinutes > 0)
        }
    }

    @Test
    fun coordinatesAreInsideTheirRegionBounds() {
        listings.forEach { listing ->
            val bounds = regionById.getValue(listing.region).toDomain().bounds
            assertTrue(listing.id, GeoPoint(listing.latitude, listing.longitude) in bounds)
        }
    }

    @Test
    fun everyListingHasFourToEightCreditedImages() {
        listings.forEach { listing ->
            assertTrue(listing.id, listing.images.size in 4..8)
            assertEquals(
                listing.id,
                listing.images.size,
                listing.images
                    .map(ImageDto::url)
                    .toSet()
                    .size,
            )
            listing.images.forEach { image ->
                assertTrue(image.url, image.url.startsWith("https://images.pexels.com/photos/"))
                assertTrue(image.url, maxOf(image.width, image.height) >= 1200)
                assertTrue(image.url, Regex("^#[0-9A-F]{6}$").matches(image.dominantColor))
                assertTrue(image.url, image.photographer.isNotBlank())
                assertTrue(image.url, image.photographerUrl.startsWith("https://www.pexels.com/@"))
                assertTrue(image.url, image.sourceUrl.startsWith("https://www.pexels.com/photo/"))
                assertEquals("Pexels License", image.licence)
                assertTrue(image.fallbackAsset, File(assetsDir, image.fallbackAsset).isFile)
                image.offlineAsset?.let { assertTrue(it, File(assetsDir, it).isFile) }
            }
        }
    }

    @Test
    fun coverPhotosAreUniqueAcrossListings() {
        val covers = listings.map { it.images.first().url }
        assertEquals(covers.size, covers.toSet().size)
    }

    @Test
    fun priceHistoryHasTwelveConsecutiveMonthsEndingAtCurrentRent() {
        listings.forEach { listing ->
            val months = listing.priceHistory.map { YearMonth.parse(it.month) }
            assertEquals(listing.id, 12, months.size)
            months.zipWithNext().forEach { (a, b) -> assertEquals(listing.id, a.plusMonths(1), b) }
            assertEquals(listing.id, listing.rentPerMonth, listing.priceHistory.last().amount)
            assertTrue(listing.id, listing.priceHistory.all { it.amount > 0 })
        }
    }

    @Test
    fun offlinePackIsSmallAndBundlesTheDesignCovers() {
        val pack = File(assetsDir, "offline").listFiles { file -> file.extension == "webp" }.orEmpty()
        assertEquals(12, pack.size)
        assertTrue("offline pack is ${pack.sumOf(File::length)} bytes", pack.sumOf(File::length) < 3L * 1024 * 1024)
        listOf("Georgie House", "Max House", "Luna Haven", "The Serene Nest").forEach { title ->
            assertTrue(
                title,
                listings
                    .single { it.title == title }
                    .images
                    .first()
                    .offlineAsset != null,
            )
        }
    }

    @Test
    fun everyListingMapsToTheDomainModel() {
        val properties = listings.map(ListingDto::toDomain)
        assertEquals(listings.size, properties.size)
        properties.forEach { assertEquals(it.id, it.images.size, listings.single { l -> l.id == it.id }.images.size) }
    }
}
