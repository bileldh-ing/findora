package com.findora.app.core.data.local

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.dao.PropertyDao
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedEntity
import com.findora.app.core.testing.seedListings
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PropertyDaoTest {
    private lateinit var db: FindoraDatabase
    private lateinit var dao: PropertyDao

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        dao = db.propertyDao()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun roundTripsEveryColumnIncludingJsonLists() =
        runTest {
            val entity = seedEntity(0, fetchedAtMillis = 42)
            dao.upsertAll(listOf(entity))
            val loaded = dao.findById(entity.id)
            assertEquals(entity, loaded)
            assertEquals(entity.images.size, loaded!!.toDomain().images.size)
            assertEquals(12, loaded.toDomain().priceHistory.size)
        }

    @Test
    fun observesRegionNewestFirstAndAppliesFilters() =
        runTest {
            dao.upsertAll(
                listOf(
                    seedEntity(0, id = "a", regionId = "kerala", rent = 10_000, createdAtMillis = 1),
                    seedEntity(1, id = "b", regionId = "kerala", rent = 25_000, createdAtMillis = 3),
                    seedEntity(2, id = "c", regionId = "kerala", rent = 40_000, createdAtMillis = 2),
                    seedEntity(3, id = "d", regionId = "paris", rent = 1_500, createdAtMillis = 4),
                ),
            )
            val all = dao.observeByRegion("kerala", null, null, null, null, false)
            all.test {
                assertEquals(listOf("b", "c", "a"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            dao.observeByRegion("kerala", null, 20_000, 30_000, null, false).test {
                assertEquals(listOf("b"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            val type = seedEntity(2).type
            dao.observeByRegion("kerala", type, null, null, null, false).test {
                assertTrue(awaitItem().all { it.type == type })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun filtersByBedroomsAndVerified() =
        runTest {
            val rows = seedListings.indices.map { seedEntity(it) }.filter { it.regionId == "kerala" }
            dao.upsertAll(rows)
            dao.observeByRegion("kerala", null, null, null, 3, true).test {
                val result = awaitItem()
                assertEquals(rows.count { it.bedrooms >= 3 && it.verified }, result.size)
                assertTrue(result.all { it.bedrooms >= 3 && it.verified })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun replaceRegionRemovesStaleRowsOnlyInThatRegion() =
        runTest {
            dao.upsertAll(
                listOf(
                    seedEntity(0, id = "keep", regionId = "kerala"),
                    seedEntity(1, id = "stale", regionId = "kerala"),
                    seedEntity(2, id = "other", regionId = "paris"),
                ),
            )
            dao.replaceRegion(
                "kerala",
                listOf(
                    seedEntity(0, id = "keep", regionId = "kerala", rent = 99),
                    seedEntity(3, id = "new", regionId = "kerala"),
                ),
            )
            assertNull(dao.findById("stale"))
            assertEquals(99L, dao.findById("keep")!!.rentAmount)
            assertEquals(2, dao.countInRegion("kerala"))
            assertEquals(1, dao.countInRegion("paris"))
            dao.replaceRegion("paris", emptyList())
            assertEquals(0, dao.countInRegion("paris"))
        }

    @Test
    fun tracksOldestFetchTimeAndObservesSingleRow() =
        runTest {
            assertNull(dao.oldestFetchInRegion("kerala"))
            dao.upsertAll(
                listOf(seedEntity(0, id = "x", fetchedAtMillis = 50), seedEntity(1, id = "y", fetchedAtMillis = 20)),
            )
            assertEquals(20L, dao.oldestFetchInRegion(seedEntity(0).regionId))
            dao.observeById("x").test {
                assertEquals("x", awaitItem()?.id)
                dao.replaceRegion(seedEntity(0).regionId, emptyList())
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
