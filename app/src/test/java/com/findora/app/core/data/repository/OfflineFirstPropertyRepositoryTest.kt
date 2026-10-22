package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.remote.FailureMode
import com.findora.app.core.data.remote.FakeRemoteConfig
import com.findora.app.core.data.remote.FakeRemoteDataSource
import com.findora.app.core.domain.model.DataError
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedListings
import kotlinx.coroutines.test.TestDispatcher
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class OfflineFirstPropertyRepositoryTest {
    private lateinit var db: FindoraDatabase
    private val config = FakeRemoteConfig()
    private val clock = MutableClock()
    private val keralaCount = seedListings.count { it.region == "kerala" }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun repository(dispatcher: TestDispatcher) =
        OfflineFirstPropertyRepository(
            remote = FakeRemoteDataSource(fileSeedReader(), config, Random(1)),
            dao = db.propertyDao(),
            clock = clock,
            policy = CachePolicy(maxAge = Duration.ofMinutes(15), pageSize = 7),
            ioDispatcher = dispatcher,
        )

    @Test
    fun firstRefreshFillsTheCacheAcrossPages() =
        runTest {
            val repo = repository(UnconfinedTestDispatcher(testScheduler))
            repo.observeProperties("kerala").test {
                assertEquals(emptyList<Any>(), awaitItem())
                assertEquals(RefreshResult.Fetched(keralaCount), repo.refresh("kerala"))
                val cached = awaitItem()
                assertEquals(keralaCount, cached.size)
                assertTrue(cached.zipWithNext().all { (a, b) -> !a.createdAt.isBefore(b.createdAt) })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun freshCacheSkipsTheNetworkUntilItExpiresOrIsForced() =
        runTest {
            val repo = repository(UnconfinedTestDispatcher(testScheduler))
            repo.refresh("kerala")
            config.failureMode = FailureMode.Always(DataError.Network)
            assertEquals(RefreshResult.UpToDate(keralaCount), repo.refresh("kerala"))
            assertEquals(RefreshResult.Failed(DataError.Network, keralaCount), repo.refresh("kerala", force = true))
            clock.advance(Duration.ofMinutes(16))
            assertEquals(RefreshResult.Failed(DataError.Network, keralaCount), repo.refresh("kerala"))
            config.reset()
            assertEquals(RefreshResult.Fetched(keralaCount), repo.refresh("kerala"))
        }

    @Test
    fun failureOnAnEmptyCacheReportsZeroAndKeepsWorkingOffline() =
        runTest {
            val repo = repository(UnconfinedTestDispatcher(testScheduler))
            config.failNext(count = 1, error = DataError.Timeout)
            assertEquals(RefreshResult.Failed(DataError.Timeout, 0), repo.refresh("paris"))
            assertEquals(RefreshResult.Fetched(8), repo.refresh("paris"))
            config.failureMode = FailureMode.Always(DataError.Server)
            assertEquals(RefreshResult.Failed(DataError.Server, 8), repo.refresh("paris", force = true))
            repo.observeProperties("paris").test {
                assertEquals(8, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun failureMidwayThroughPagingLeavesThePreviousCacheIntact() =
        runTest {
            val repo = repository(UnconfinedTestDispatcher(testScheduler))
            repo.refresh("kerala")
            clock.advance(Duration.ofHours(1))
            config.failureMode = FailureMode.Random(rate = 0.5)
            var result: RefreshResult
            do {
                result = repo.refresh("kerala", force = true)
            } while (result !is RefreshResult.Failed)
            assertEquals(keralaCount, (result as RefreshResult.Failed).cachedCount)
            assertEquals(keralaCount, db.propertyDao().countInRegion("kerala"))
        }

    @Test
    fun filtersAndSingleListingReadFromTheCache() =
        runTest {
            val repo = repository(UnconfinedTestDispatcher(testScheduler))
            repo.refresh("kerala")
            repo.observeProperties("kerala", PropertyFilter(type = PropertyType.House, maxRent = 20_000)).test {
                val houses = awaitItem()
                assertTrue(houses.isNotEmpty())
                assertTrue(houses.all { it.type == PropertyType.House && it.rent.amount <= 20_000 })
                cancelAndIgnoreRemainingEvents()
            }
            val georgie = seedListings.single { it.title == "Georgie House" }
            repo.observeProperty(georgie.id).test {
                val property = awaitItem()!!
                assertEquals("Kochi", property.locality)
                assertEquals(10_000L, property.rent.amount)
                assertEquals(property.images.first().offlineAsset, property.coverImage.offlineAsset)
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeProperty("missing").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
