package com.findora.app.core.data.chat

import com.findora.app.core.domain.chat.ActiveConversation
import com.findora.app.core.domain.chat.AgentReply
import com.findora.app.core.domain.chat.AgentReplyEngine
import com.findora.app.core.domain.chat.AgentReplyFormatter
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.format.DisplayPreferences
import com.findora.app.core.domain.model.NotificationToggle
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.ObserveDisplayPreferences
import com.findora.app.core.domain.viewing.ViewingSlotGenerator
import com.findora.app.core.testing.FakeChatRepository
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeReferenceDataRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.RecordingChatNotifier
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class DemoAgentResponderTest {
    private val zone = ZoneId.of("Africa/Tunis")
    private val now: Instant = LocalDateTime.of(2026, 10, 26, 10, 10).atZone(zone).toInstant()
    private val guest = AuthRepository.GUEST_USER_ID
    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val chats = FakeChatRepository { now }
    private val notifications = FakeNotificationRepository { now }
    private val notifier = RecordingChatNotifier()
    private val active = ActiveConversation()
    private val timing = AgentTiming(deliverMillis = 500, readMillis = 1_000)
    private val settings = FakeSettingsRepository()

    /** Writes the parts' class names, so the tests can see what the engine decided. */
    private val formatter =
        object : AgentReplyFormatter {
            override fun format(
                reply: AgentReply,
                zone: ZoneId,
                display: DisplayPreferences,
            ): String = reply.parts.joinToString(" ") { it::class.simpleName.orEmpty() }

            override fun listingGone(): String = "gone"
        }

    private fun TestScope.responder(properties: FakePropertyRepository = FakePropertyRepository()) =
        DemoAgentResponder(
            chats = chats,
            properties = properties,
            viewings = FakeViewingRepository { now },
            notifications = notifications,
            engine = AgentReplyEngine(),
            formatter = formatter,
            generator = ViewingSlotGenerator({ now }, zone),
            notifier = notifier,
            active = active,
            clock = { now },
            zone = zone,
            timing = timing,
            settings = settings,
            displayPreferences = ObserveDisplayPreferences(settings, FakeReferenceDataRepository()),
            scope = backgroundScope,
        )

    /**
     * The agent runs in [TestScope.backgroundScope] (it stands in for the application scope), and
     * `advanceUntilIdle` does not wait for background work, so the tests move virtual time past every pause
     * the agent can take (delivery + reading + the longest typing time) instead.
     */
    private fun TestScope.finishAgentWork() {
        advanceTimeBy(AGENT_WORK_BOUND_MILLIS)
        runCurrent()
    }

    private suspend fun send(text: String): Pair<String, String> {
        val thread = chats.startConversation(guest, georgie)
        val message = requireNotNull(chats.sendMessage(guest, thread.id, text))
        return thread.id to message.id
    }

    private suspend fun statusOf(id: String): DeliveryStatus =
        chats.messages
            .first()
            .single { it.id == id }
            .status

    @Test
    fun theAgentDeliversReadsTypesThenRepliesAndNotifiesWhenTheThreadIsClosed() =
        runTest {
            val agent = responder()
            val (thread, message) = send("Is it still available?")
            agent.onUserMessage(guest, thread, message, "Is it still available?")
            runCurrent()
            assertEquals(DeliveryStatus.Sending, statusOf(message))
            advanceTimeBy(timing.deliverMillis + 1)
            assertEquals(DeliveryStatus.Sent, statusOf(message))
            advanceTimeBy(timing.readMillis)
            assertEquals(DeliveryStatus.Read, statusOf(message))
            assertEquals(setOf(thread), agent.typing.value)
            finishAgentWork()
            assertTrue(agent.typing.value.isEmpty())
            val reply = chats.messages.value.last()
            assertEquals("Greeting Availability", reply.body)
            assertTrue(!reply.fromUser)
            val entry = notifications.observeNotifications(guest).first().single()
            assertEquals(NotificationKind.NewMessage, entry.kind)
            assertEquals(thread, entry.conversationId)
            assertEquals(georgie.agent.name, entry.subject)
            assertEquals(reply.body, entry.preview)
            assertEquals(listOf(thread), notifier.posted.map { it.conversationId })
        }

    @Test
    fun aReplyToTheOpenThreadIsReadAtOnceAndNotNotified() =
        runTest {
            val agent = responder()
            val (thread, message) = send("Can I visit this week?")
            active.enter(thread)
            agent.onUserMessage(guest, thread, message, "Can I visit this week?")
            finishAgentWork()
            val reply = chats.messages.value.last()
            assertEquals("Greeting ViewingTimes", reply.body)
            assertEquals(DeliveryStatus.Read, reply.status)
            assertTrue(notifications.observeNotifications(guest).first().isEmpty())
            assertTrue(notifier.posted.isEmpty())
        }

    @Test
    fun messagesAreAnsweredInOrderAndTheAgentIntroducesItselfOnce() =
        runTest {
            val agent = responder()
            val (thread, first) = send("Is the price negotiable?")
            val second = requireNotNull(chats.sendMessage(guest, thread, "Do you allow pets?")).id
            agent.onUserMessage(guest, thread, first, "Is the price negotiable?")
            agent.onUserMessage(guest, thread, second, "Do you allow pets?")
            finishAgentWork()
            val replies =
                chats.messages.value
                    .filter { !it.fromUser }
                    .map { it.body }
            assertEquals(listOf("Greeting Price", "Pets"), replies)
            // One notification per thread: the second reply replaced the first.
            assertEquals(listOf("Pets"), notifications.observeNotifications(guest).first().map { it.preview })
        }

    @Test
    fun aListingThatLeftTheAppGetsAPoliteAnswer() =
        runTest {
            val agent = responder(FakePropertyRepository(listings = seedProperties.filter { it.id != georgie.id }))
            val (thread, message) = send("Is it still available?")
            agent.onUserMessage(guest, thread, message, "Is it still available?")
            finishAgentWork()
            assertEquals(
                "gone",
                chats.messages.value
                    .last()
                    .body,
            )
        }

    @Test
    fun turnedOffNotificationsAreNotPostedNorListed() =
        runTest {
            settings.setNotificationEnabled(NotificationToggle.Messages, false)
            settings.setNotificationEnabled(NotificationToggle.InApp, false)
            val agent = responder()
            val (thread, message) = send("Is it still available?")
            agent.onUserMessage(guest, thread, message, "Is it still available?")
            finishAgentWork()
            // The reply itself is still stored; only the notifications are skipped.
            assertEquals("Greeting Availability", chats.messages.value.last().body)
            assertTrue(notifier.posted.isEmpty())
            assertTrue(notifications.observeNotifications(guest).first().isEmpty())
        }

    @Test
    fun messageNotificationsCanBeOffWhileTheCenterStillListsTheReply() =
        runTest {
            settings.setNotificationEnabled(NotificationToggle.Messages, false)
            val agent = responder()
            val (thread, message) = send("Is it still available?")
            agent.onUserMessage(guest, thread, message, "Is it still available?")
            finishAgentWork()
            assertTrue(notifier.posted.isEmpty())
            assertEquals(1, notifications.observeNotifications(guest).first().size)
        }

    private companion object {
        /** Far beyond two queued replies of the longest pauses (0.5 s + 1 s + 4 s each). */
        const val AGENT_WORK_BOUND_MILLIS = 60_000L
    }
}
