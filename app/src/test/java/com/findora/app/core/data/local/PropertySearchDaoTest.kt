package com.findora.app.core.data.local

import android.app.Application
import androidx.paging.testing.asSnapshot
import app.cash.turbine.test
import com.findora.app.core.data.remote.FakeRemoteConfig
import com.findora.app.core.data.remote.FakeRemoteDataSource
import com.findora.app.core.data.repository.CachePolicy
import com.findora.app.core.data.repository.OfflineFirstPropertyRepository
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedEntity
import com.findora.app.core.testing.seedListings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration
import kotlin.random.Random

/** The SQL feed end to end: PropertyQueryBuilder -> PropertySearchDao (Room) -> Paging 3 -> domain. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PropertySearchDaoTest {
    private lateinit var db: FindoraDatabase
    private val kerala = seedListings.indices.map { seedEntity(it) }.filter { it.regionId == "kerala" }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private suspend fun TestScope.repository(pageSize: Int = 7): OfflineFirstPropertyRepository {
        db.propertyDao().replaceRegion("kerala", kerala)
        return OfflineFirstPropertyRepository(
            remote = FakeRemoteDataSource(fileSeedReader(), FakeRemoteConfig(), Random(1)),
            dao = db.propertyDao(),
            searchDao = db.propertySearchDao(),
            clock = MutableClock(),
            policy = CachePolicy(maxAge = Duration.ofMinutes(15), pageSize = pageSize),
            ioDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
    }

    @Test
    fun pagingLoadsEveryMatchingRowAcrossPagesInBestMatchOrder() =
        runTest {
            val repo = repository(pageSize = 5)
            val all = repo.pagedProperties("kerala", PropertyFilter()).asSnapshot { scrollTo(kerala.size - 1) }
            assertEquals(kerala.size, all.size)
            assertEquals(all.size, all.map { it.id }.distinct().size)
            val verifiedThenUnverified = all.map { it.verified }
            assertEquals(verifiedThenUnverified.sortedDescending(), verifiedThenUnverified)
        }

    @Test
    fun categoryTextAndRefinementsFilterInSql() =
        runTest {
            val repo = repository()
            val apartments =
                repo.pagedProperties("kerala", PropertyFilter(type = PropertyType.Apartment)).asSnapshot { scrollTo(40) }
            assertEquals(kerala.count { it.type == "apartment" }, apartments.size)
            assertTrue(apartments.all { it.type == PropertyType.Apartment })

            val haven = repo.pagedProperties("kerala", PropertyFilter(query = "haven")).asSnapshot()
            assertEquals(listOf("Luna Haven"), haven.map { it.title })

            val fortKochi = repo.pagedProperties("kerala", PropertyFilter(query = "fort kochi")).asSnapshot()
            assertTrue(fortKochi.isNotEmpty())
            assertTrue(fortKochi.all { it.locality.contains("Fort Kochi") || it.title.contains("Fort Kochi", true) })

            val refined =
                PropertyFilter(minBedrooms = 2, furnishedOnly = true, verifiedOnly = true, amenities = setOf(Amenity.Wifi))
            val expected =
                kerala.count { it.bedrooms >= 2 && it.furnished && it.verified && "wifi" in it.amenities }
            val result = repo.pagedProperties("kerala", refined).asSnapshot { scrollTo(40) }
            assertEquals(expected, result.size)
            assertTrue(result.all { it.bedrooms >= 2 && it.furnished && it.verified && Amenity.Wifi in it.amenities })
        }

    @Test
    fun priceSortsOrderTheFeed() =
        runTest {
            val repo = repository()
            val cheapFirst =
                repo.pagedProperties("kerala", PropertyFilter(sort = SortOrder.PriceLowToHigh)).asSnapshot { scrollTo(40) }
            assertEquals(cheapFirst.map { it.rent.amount }.sorted(), cheapFirst.map { it.rent.amount })
            val maxRent = kerala.maxOf { it.rentAmount }
            val capped = repo.pagedProperties("kerala", PropertyFilter(maxRent = maxRent / 2)).asSnapshot { scrollTo(40) }
            assertTrue(capped.all { it.rent.amount <= maxRent / 2 })
        }

    @Test
    fun boundsCoverTheRegionAndAreNullWhenEmpty() =
        runTest {
            val repo = repository()
            repo.observeBounds("kerala").test {
                val bounds = awaitItem()!!
                assertEquals(kerala.minOf { it.rentAmount }, bounds.minRent)
                assertEquals(kerala.maxOf { it.rentAmount }, bounds.maxRent)
                assertEquals(kerala.minOf { it.areaSqm }, bounds.minAreaSqm)
                assertEquals(kerala.size, bounds.count)
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeBounds("nowhere").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun suggestionsListNeighbourhoodsBeforeHomeNames() =
        runTest {
            val repo = repository()
            val suggestions = repo.suggestions("kerala", "ka", limit = 8)
            val kinds = suggestions.map { it.kind }
            assertEquals(kinds.sortedBy { it.ordinal }, kinds)
            assertTrue(suggestions.any { it.text == "Kakkanad" && it.kind == SearchSuggestion.Kind.Locality })
            assertEquals(emptyList<SearchSuggestion>(), repo.suggestions("kerala", "  "))
            assertEquals(emptyList<SearchSuggestion>(), repo.suggestions("kerala", "%"))
        }
}
