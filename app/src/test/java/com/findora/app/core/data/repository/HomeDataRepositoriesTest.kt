package com.findora.app.core.data.repository

import android.app.Application
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class HomeDataRepositoriesTest {
    private lateinit var db: FindoraDatabase
    private val clock = MutableClock()

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun searchHistoryDeletesOneEntryCaseInsensitively() =
        runTest {
            val repo = RoomSearchHistoryRepository(db.recentSearchDao(), clock, UnconfinedTestDispatcher(testScheduler))
            repo.record("user-1", "Fort  Kochi")
            clock.advance(Duration.ofSeconds(1))
            repo.record("user-1", "Kakkanad")
            repo.record("user-2", "Fort Kochi")
            repo.delete("user-1", "  fort kochi ")
            assertEquals(listOf("Kakkanad"), repo.observeRecent("user-1").first().map { it.query })
            assertEquals(listOf("Fort Kochi"), repo.observeRecent("user-2").first().map { it.query })
        }

    @Test
    fun theHomeFilterResetsWhenAnotherUserClaimsIt() {
        val filters = InMemoryHomeFilterRepository()
        filters.claim("guest")
        filters.update { it.copy(type = PropertyType.Room, query = "Kaloor") }
        filters.claim("guest")
        assertEquals(PropertyFilter(type = PropertyType.Room, query = "Kaloor"), filters.filter.value)
        filters.claim("user-1")
        assertEquals(PropertyFilter(), filters.filter.value)
        filters.update { it.copy(verifiedOnly = true) }
        filters.reset()
        assertEquals(PropertyFilter(), filters.filter.value)
    }
}
