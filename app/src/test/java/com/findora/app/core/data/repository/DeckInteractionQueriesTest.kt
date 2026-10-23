package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.testing.inMemoryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant

/** The two Phase 5B queries over the existing interaction table (no schema change). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class DeckInteractionQueriesTest {
    private lateinit var db: FindoraDatabase
    private lateinit var repository: RoomInteractionRepository

    @Before
    fun setUp() {
        db = inMemoryDatabase()
        repository = RoomInteractionRepository(db.interactionEventDao(), Dispatchers.Unconfined)
    }

    @After
    fun tearDown() = db.close()

    private fun event(
        property: String,
        type: InteractionType,
        at: Long,
        user: String = "u",
    ) = InteractionEvent(user, property, type, Instant.ofEpochMilli(at))

    @Test
    fun matchIdsAreDistinctNewestFirstAndPerUser() =
        runTest {
            repository.record(event("p1", InteractionType.DeckSaved, 10))
            repository.record(event("p2", InteractionType.DeckSuperLiked, 20))
            repository.record(event("p3", InteractionType.DeckSkipped, 30))
            repository.record(event("p1", InteractionType.DeckSaved, 40))
            repository.record(event("p4", InteractionType.DeckSaved, 50, user = "other"))
            repository.observePropertyIds("u", InteractionType.DeckMatches).test {
                assertEquals(listOf("p1", "p2"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("p3"), repository.observePropertyIds("u", setOf(InteractionType.DeckSkipped)).first())
        }

    @Test
    fun removeDeletesOnlyTheNewestMatchingEvent() =
        runTest {
            val saved = event("p1", InteractionType.DeckSaved, 10)
            repository.record(saved)
            repository.record(saved)
            repository.record(event("p1", InteractionType.Viewed, 10))
            repository.remove(saved)
            val left = repository.observeRecent("u", limit = 10).first()
            assertEquals(2, left.size)
            assertEquals(1, left.count { it.type == InteractionType.DeckSaved })
            repository.remove(saved)
            assertEquals(emptyList<String>(), repository.observePropertyIds("u", InteractionType.DeckMatches).first())
            // Removing an event that is not stored is a no-op.
            repository.remove(event("p9", InteractionType.DeckSkipped, 99))
            assertEquals(1, repository.observeRecent("u", limit = 10).first().size)
        }
}
