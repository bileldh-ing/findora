package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RoomSavedSearchRepositoryTest {
    private lateinit var db: FindoraDatabase
    private lateinit var repo: RoomSavedSearchRepository
    private lateinit var prices: RoomPriceWatchRepository
    private val clock = MutableClock()
    private val filter = PropertyFilter(type = PropertyType.House, maxRent = 15_000)

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repo = RoomSavedSearchRepository(db.savedSearchDao(), clock, Dispatchers.Unconfined)
        prices = RoomPriceWatchRepository(db.priceWatchDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun create(
        user: String = "u",
        name: String = "Houses under 15k",
        seen: Set<String> = setOf("p1", "p2"),
    ) = repo.create(user, NewSavedSearch(name, "houses", "kerala", filter, seen))

    @Test
    fun aSavedSearchKeepsItsFilterRegionAndBaseline() =
        runTest {
            val id = create(name = "   Houses \n under   15k ")
            val saved = requireNotNull(repo.savedSearch("u", id))
            assertEquals("Houses under 15k", saved.name)
            assertEquals(filter, saved.filter)
            assertEquals("kerala", saved.regionId)
            assertTrue(saved.alertsEnabled)
            assertEquals(AlertFrequency.Daily, saved.frequency)
            assertEquals(clock.now(), saved.lastCheckedAt)
            assertEquals(setOf("p1", "p2"), repo.seenIds(id))
        }

    @Test
    fun everyCallIsScopedToTheOwner() =
        runTest {
            val id = create()
            create(user = "other", name = "Theirs")
            assertNull(repo.savedSearch("other", id))
            assertFalse(repo.rename("other", id, "Mine now"))
            assertFalse(repo.setAlertsEnabled("other", id, false))
            assertFalse(repo.setFrequency("other", id, AlertFrequency.Instant))
            assertFalse(repo.delete("other", id))
            repo.observeSavedSearches("u").test {
                assertEquals(listOf("Houses under 15k"), awaitItem().map { it.name })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf(id), repo.alertingSearches("u").map { it.id })
        }

    @Test
    fun renameAlertsAndFrequencyAreSaved() =
        runTest {
            val id = create()
            assertFalse(repo.rename("u", id, "   "))
            assertTrue(repo.rename("u", id, "Family homes"))
            assertTrue(repo.setFrequency("u", id, AlertFrequency.Instant))
            assertTrue(repo.setAlertsEnabled("u", id, false))
            val saved = requireNotNull(repo.savedSearch("u", id))
            assertEquals("Family homes", saved.name)
            assertEquals(AlertFrequency.Instant, saved.frequency)
            assertFalse(saved.alertsEnabled)
            assertTrue(repo.alertingSearches("u").isEmpty())
        }

    @Test
    fun aCheckAddsSeenIdsOnceAndStampsTheTime() =
        runTest {
            val id = create()
            clock.advance(Duration.ofDays(1))
            repo.recordCheck(id, setOf("p2", "p3"), clock.now(), notified = true)
            assertEquals(setOf("p1", "p2", "p3"), repo.seenIds(id))
            assertEquals(clock.now(), repo.savedSearch("u", id)?.lastCheckedAt)
            assertEquals(clock.now().toEpochMilli(), db.savedSearchDao().find(id, "u")?.lastNotifiedAtMillis)
        }

    @Test
    fun deletingASearchOrTheAccountRemovesItsSeenIds() =
        runTest {
            val first = create()
            val second = create(name = "Second")
            assertTrue(repo.delete("u", first))
            assertTrue(repo.seenIds(first).isEmpty())
            prices.replace("u", mapOf("p1" to 10_000L))
            db.accountDao().deleteGuestData("u")
            assertTrue(repo.seenIds(second).isEmpty())
            assertNull(repo.savedSearch("u", second))
            assertTrue(prices.baselines("u").isEmpty())
        }

    @Test
    fun priceBaselinesAreReplacedPerUser() =
        runTest {
            prices.replace("u", mapOf("p1" to 10_000L, "p2" to 12_000L))
            prices.replace("other", mapOf("p1" to 9_000L))
            prices.replace("u", mapOf("p2" to 11_500L))
            assertEquals(mapOf("p2" to 11_500L), prices.baselines("u"))
            assertEquals(mapOf("p1" to 9_000L), prices.baselines("other"))
        }
}
