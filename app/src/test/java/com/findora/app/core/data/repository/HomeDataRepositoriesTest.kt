package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.entity.MessageEntity
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

    private fun message(
        id: String,
        recipient: String,
        sentAt: Long,
    ) = MessageEntity(
        id = id,
        conversationId = "c-$recipient",
        propertyId = "p1",
        senderId = "agent",
        recipientId = recipient,
        body = "Message $id",
        sentAtMillis = sentAt,
        readAtMillis = null,
    )

    @Test
    fun unreadMessagesAreNewestFirstPerUserAndSwipedOnesBecomeRead() =
        runTest {
            val repo = RoomMessageRepository(db.messageDao(), clock, UnconfinedTestDispatcher(testScheduler))
            db.messageDao().insert(message("m1", "user-1", sentAt = 1_000))
            db.messageDao().insert(message("m2", "user-1", sentAt = 2_000))
            db.messageDao().insert(message("m3", "user-2", sentAt = 3_000))
            repo.observeUnread("user-1").test {
                assertEquals(listOf("m2", "m1"), awaitItem().map { it.id })
                // Another user cannot mark someone else's message read.
                repo.markRead("user-2", "m1")
                repo.markRead("user-1", "m2")
                assertEquals(listOf("m1"), awaitItem().map { it.id })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("m3"), repo.observeUnread("user-2").first().map { it.id })
        }

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
