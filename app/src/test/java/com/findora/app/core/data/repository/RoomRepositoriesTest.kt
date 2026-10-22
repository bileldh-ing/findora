package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.repository.SearchHistoryRepository
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedEntity
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class RoomRepositoriesTest {
    private lateinit var db: FindoraDatabase
    private val clock = MutableClock()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun favoritesToggleAndStayPerUser() =
        runTest {
            val repo = RoomFavoritesRepository(db.favoriteDao(), clock, UnconfinedTestDispatcher(testScheduler))
            db.propertyDao().upsertAll(listOf(seedEntity(0, id = "p1"), seedEntity(1, id = "p2")))
            repo.observeFavoriteIds("guest").test {
                assertEquals(emptySet<String>(), awaitItem())
                assertTrue(repo.toggle("guest", "p1"))
                assertEquals(setOf("p1"), awaitItem())
                clock.advance(Duration.ofSeconds(5))
                assertTrue(repo.toggle("guest", "p2"))
                assertEquals(setOf("p1", "p2"), awaitItem())
                assertFalse(repo.toggle("guest", "p1"))
                assertEquals(setOf("p2"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeFavorites("guest").test {
                assertEquals(listOf("p2"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            repo.observeFavoriteIds("someone").test {
                assertEquals(emptySet<String>(), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun searchHistoryTrimsNormalisesAndCaps() =
        runTest {
            val repo = RoomSearchHistoryRepository(db.recentSearchDao(), clock, UnconfinedTestDispatcher(testScheduler))
            repo.record("u", "   ")
            repeat(SearchHistoryRepository.MAX_STORED + 5) { index ->
                clock.advance(Duration.ofSeconds(1))
                repo.record("u", "query $index")
            }
            clock.advance(Duration.ofSeconds(1))
            repo.record("u", "  Fort   Kochi ")
            clock.advance(Duration.ofSeconds(1))
            repo.record("u", "fort kochi")
            repo.observeRecent("u", limit = 100).test {
                val items = awaitItem()
                assertEquals(SearchHistoryRepository.MAX_STORED, items.size)
                assertEquals("fort kochi", items.first().query)
                assertEquals(clock.now(), items.first().searchedAt)
                assertEquals(1, items.count { it.query.equals("fort kochi", ignoreCase = true) })
                repo.clear("u")
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun interactionsAreRecordedNewestFirst() =
        runTest {
            val repo = RoomInteractionRepository(db.interactionEventDao(), UnconfinedTestDispatcher(testScheduler))
            val start = Instant.parse("2026-10-01T10:00:00Z")
            repo.record(InteractionEvent("u", "p1", InteractionType.Viewed, start, dwellMillis = 3_000))
            repo.record(InteractionEvent("u", "p1", InteractionType.Favorited, start.plusSeconds(10)))
            val invalid = runCatching { repo.record(InteractionEvent("u", "p1", InteractionType.Viewed, start, -1)) }
            assertTrue(invalid.exceptionOrNull() is IllegalArgumentException)
            repo.observeRecent("u", limit = 10).test {
                val events = awaitItem()
                assertEquals(listOf(InteractionType.Favorited, InteractionType.Viewed), events.map { it.type })
                assertEquals(3_000L, events.last().dwellMillis)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun regionsComeFromTheSeedWithKeralaFirst() =
        runTest {
            val repo = SeedRegionRepository(fileSeedReader(), UnconfinedTestDispatcher(testScheduler))
            val regions = repo.regions()
            assertEquals(listOf("kerala", "tunis", "paris", "dubai"), regions.map { it.id })
            assertTrue(regions.first().isDefault)
            assertEquals("TND", repo.region("tunis")?.currency?.code)
            assertNull(repo.region("atlantis"))
            assertTrue(regions.first().localities.any { it.name == "Kakkanad" })
        }
}
