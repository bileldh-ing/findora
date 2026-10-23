package com.findora.app.core.data.repository

import android.app.Application
import app.cash.turbine.test
import com.findora.app.core.data.local.FindoraDatabase
import com.findora.app.core.data.local.entity.NotificationEntity
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.testing.MutableClock
import com.findora.app.core.testing.inMemoryDatabase
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.first
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
class RoomChatRepositoryTest {
    private lateinit var db: FindoraDatabase
    private val clock = MutableClock()
    private val georgie = seedProperties.first()
    private val other = seedProperties.first { it.id != georgie.id }

    @Before
    fun setUp() {
        db = inMemoryDatabase()
    }

    @After
    fun tearDown() = db.close()

    private fun kotlinx.coroutines.test.TestScope.chats() =
        RoomChatRepository(db.chatDao(), clock, UnconfinedTestDispatcher(testScheduler))

    private fun kotlinx.coroutines.test.TestScope.notifications() =
        RoomNotificationRepository(db.notificationDao(), clock, UnconfinedTestDispatcher(testScheduler))

    @Test
    fun oneThreadPerUserAndListingWithTheAgentSnapshotted() =
        runTest {
            val repo = chats()
            val first = repo.startConversation("user-1", georgie)
            val again = repo.startConversation("user-1", georgie)
            val someoneElse = repo.startConversation("user-2", georgie)
            assertEquals(first, again)
            assertTrue(first.id != someoneElse.id)
            assertEquals(georgie.agent.name, first.agent.name)
            assertEquals(georgie.agent.avatar, first.agent.avatar)
            assertEquals(georgie.title, first.propertyTitle)
            assertEquals(first, repo.observeConversationFor("user-1", georgie.id).first())
        }

    @Test
    fun threadsAndMessagesAreIsolatedPerUser() =
        runTest {
            val repo = chats()
            val mine = repo.startConversation("user-1", georgie)
            repo.sendMessage("user-1", mine.id, "Is it still available?")
            // Another user can neither read, write to nor open the thread.
            assertNull(repo.sendMessage("user-2", mine.id, "Hi"))
            assertNull(repo.observeConversation("user-2", mine.id).first())
            assertTrue(repo.observeMessages("user-2", mine.id).first().isEmpty())
            assertTrue(repo.observeConversations("user-2").first().isEmpty())
            assertEquals(0, repo.markConversationRead("user-2", mine.id))
            assertEquals(
                listOf("Is it still available?"),
                repo.observeMessages("user-1", mine.id).first().map { it.body },
            )
        }

    @Test
    fun blankMessagesAreRejectedAndTextIsTrimmed() =
        runTest {
            val repo = chats()
            val thread = repo.startConversation("user-1", georgie)
            assertNull(repo.sendMessage("user-1", thread.id, "   "))
            assertEquals("Hello", repo.sendMessage("user-1", thread.id, "  Hello \n")?.body)
        }

    @Test
    fun deliveryGoesFromSendingToSentToRead() =
        runTest {
            val repo = chats()
            val thread = repo.startConversation("user-1", georgie)
            repo.observeMessages("user-1", thread.id).test {
                assertTrue(awaitItem().isEmpty())
                val sent = requireNotNull(repo.sendMessage("user-1", thread.id, "Can I visit this week?"))
                val pending = awaitItem().single()
                assertEquals(DeliveryStatus.Sending, pending.status)
                assertTrue(pending.fromUser)
                repo.markDelivered(sent.id)
                assertEquals(DeliveryStatus.Sent, awaitItem().single().status)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                DeliveryStatus.Sent,
                repo
                    .observeMessages("user-1", thread.id)
                    .first()
                    .single()
                    .status,
            )
            repo.markReadByAgent(thread.id)
            assertEquals(
                DeliveryStatus.Read,
                repo
                    .observeMessages("user-1", thread.id)
                    .first()
                    .single()
                    .status,
            )
        }

    @Test
    fun unreadCountsCoverOnlyAgentMessagesOfTheUser() =
        runTest {
            val repo = chats()
            val a = repo.startConversation("user-1", georgie)
            val b = repo.startConversation("user-1", other)
            val theirs = repo.startConversation("user-2", georgie)
            repo.sendMessage("user-1", a.id, "Hi")
            assertFalse(repo.hasAgentReplied(a.id))
            repo.receiveAgentMessage(a.id, "Hello from the agent")
            repo.receiveAgentMessage(a.id, "It is available")
            repo.receiveAgentMessage(b.id, "Viewing slots")
            repo.receiveAgentMessage(theirs.id, "Not yours")
            assertTrue(repo.hasAgentReplied(a.id))
            assertEquals(3, repo.observeUnreadCount("user-1").first())
            assertEquals(1, repo.observeUnreadCount("user-2").first())
            val summaries = repo.observeConversations("user-1").first().associateBy { it.conversation.id }
            assertEquals(2, summaries.getValue(a.id).unreadCount)
            assertEquals(1, summaries.getValue(b.id).unreadCount)
            assertEquals(2, repo.markConversationRead("user-1", a.id))
            assertEquals(1, repo.observeUnreadCount("user-1").first())
            assertEquals(1, repo.observeUnreadCount("user-2").first())
        }

    @Test
    fun theListShowsTheNewestMessageAndNewestThreadFirst() =
        runTest {
            val repo = chats()
            val a = repo.startConversation("user-1", georgie)
            val b = repo.startConversation("user-1", other)
            // A thread without messages is not listed.
            assertTrue(repo.observeConversations("user-1").first().isEmpty())
            repo.sendMessage("user-1", a.id, "First")
            clock.advance(Duration.ofMinutes(1))
            repo.sendMessage("user-1", b.id, "Second")
            clock.advance(Duration.ofMinutes(1))
            repo.receiveAgentMessage(a.id, "Reply in A")
            val list = repo.observeConversations("user-1").first()
            assertEquals(listOf(a.id, b.id), list.map { it.conversation.id })
            assertEquals("Reply in A", list.first().lastMessage.body)
            assertFalse(list.first().lastMessage.fromUser)
            assertEquals("Second", list.last().lastMessage.body)
            assertTrue(list.last().lastMessage.fromUser)
        }

    @Test
    fun deletingAnAccountRemovesItsThreadsAndNotifications() =
        runTest {
            val repo = chats()
            val thread = repo.startConversation("user-1", georgie)
            repo.sendMessage("user-1", thread.id, "Hi")
            repo.receiveAgentMessage(thread.id, "Hello")
            notifications().add("user-1", NewNotification(NotificationKind.NewMessage, "Agent", georgie.id))
            db.accountDao().deleteGuestData("user-1")
            assertTrue(repo.observeConversations("user-1").first().isEmpty())
            assertNull(repo.observeConversation("user-1", thread.id).first())
            assertTrue(notifications().observeNotifications("user-1").first().isEmpty())
        }

    @Test
    fun notificationsArePerUserAndAMessageReplacesTheThreadsUnreadOne() =
        runTest {
            val repo = notifications()
            repo.add(
                "user-1",
                NewNotification(NotificationKind.ViewingBooked, "Georgie", georgie.id, eventAt = Instant.EPOCH),
            )
            clock.advance(Duration.ofMinutes(1))
            repo.add("user-1", NewNotification(NotificationKind.NewMessage, "Anu", georgie.id, "First", "c1"))
            clock.advance(Duration.ofMinutes(1))
            repo.add("user-1", NewNotification(NotificationKind.NewMessage, "Anu", georgie.id, "Second", "c1"))
            repo.add("user-2", NewNotification(NotificationKind.NewMessage, "Anu", georgie.id, "Theirs", "c9"))
            val mine = repo.observeNotifications("user-1").first()
            assertEquals(listOf("Second", ""), mine.map { it.preview })
            assertEquals(Instant.EPOCH, mine.last().eventAt)
            assertEquals(2, repo.observeUnreadCount("user-1").first())
            // Scoped writes: another user cannot touch these.
            repo.markRead("user-2", mine.first().id)
            repo.delete("user-2", mine.first().id)
            assertEquals(2, repo.observeUnreadCount("user-1").first())
            repo.markConversationRead("user-1", "c1")
            assertEquals(1, repo.observeUnreadCount("user-1").first())
            repo.markAllRead("user-1")
            assertEquals(0, repo.observeUnreadCount("user-1").first())
            assertEquals(1, repo.observeUnreadCount("user-2").first())
            repo.delete("user-1", mine.first().id)
            assertEquals(1, repo.observeNotifications("user-1").first().size)
        }

    @Test
    fun aNotificationKindFromANewerVersionIsSkipped() =
        runTest {
            db.notificationDao().add(
                NotificationEntity(
                    userId = "user-1",
                    kind = "price_alert_v9",
                    subject = "x",
                    preview = "",
                    propertyId = georgie.id,
                    conversationId = null,
                    eventAtMillis = null,
                    createdAtMillis = 0,
                    readAtMillis = null,
                ),
            )
            assertTrue(notifications().observeNotifications("user-1").first().isEmpty())
        }
}
