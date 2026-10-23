package com.findora.app.feature.chat

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.findora.app.core.domain.chat.ActiveConversation
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeChatRepository
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.RecordingAgentResponder
import com.findora.app.core.testing.RecordingChatNotifier
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.viewing.RecordingInteractions
import com.findora.app.feature.viewing.awaitUntil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ChatViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val zone = ZoneId.of("Africa/Tunis")
    private val now: Instant = LocalDateTime.of(2026, 10, 26, 10, 10).atZone(zone).toInstant()
    private val guest = AuthRepository.GUEST_USER_ID
    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val auth = FakeAuthRepository()
    private val chats = FakeChatRepository { now }
    private val notifications = FakeNotificationRepository { now }
    private val responder = RecordingAgentResponder()
    private val interactions = RecordingInteractions()
    private val active = ActiveConversation()
    private val notifier = RecordingChatNotifier()

    private fun viewModel(
        conversationId: String? = null,
        propertyId: String? = null,
    ) = ChatViewModel(
        savedStateHandle =
            SavedStateHandle(
                buildMap {
                    conversationId?.let { put(ChatViewModel.CONVERSATION_ID_KEY, it) }
                    propertyId?.let { put(ChatViewModel.PROPERTY_ID_KEY, it) }
                },
            ),
        chats = chats,
        properties = FakePropertyRepository(),
        notifications = notifications,
        responder = responder,
        interactions = interactions,
        active = active,
        notifier = notifier,
        auth = auth,
        clock = { now },
        zone = zone,
    )

    private fun thread(
        user: String = guest,
        id: String = "c1",
    ): Conversation {
        val conversation =
            Conversation(
                id = id,
                userId = user,
                propertyId = georgie.id,
                propertyTitle = georgie.title,
                agent = ChatAgent(georgie.agent.id, georgie.agent.name, georgie.agent.avatar),
                createdAt = now,
            )
        chats.addConversation(conversation)
        return conversation
    }

    private fun ChatUiState.content() = this as ChatUiState.Content

    @Test
    fun fromAListingTheThreadStartsEmptyWithTheAgentAndQuickReplies() =
        runTest(main.dispatcher) {
            viewModel(propertyId = georgie.id).state.test {
                val content = awaitUntil { it is ChatUiState.Content }.content()
                assertNull(content.conversationId)
                assertEquals(georgie.agent.name, content.agent.name)
                assertEquals(georgie, content.property)
                assertTrue(content.isEmpty)
                assertEquals(3, content.quickReplies.size)
                assertFalse(content.canSend)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theFirstMessageCreatesTheThreadAndWakesTheAgent() =
        runTest(main.dispatcher) {
            val vm = viewModel(propertyId = georgie.id)
            vm.state.test {
                awaitUntil { it is ChatUiState.Content }
                vm.onEvent(ChatUiEvent.DraftChanged("Is it still available?"))
                assertTrue(awaitUntil { it.content().draft.isNotEmpty() }.content().canSend)
                vm.onEvent(ChatUiEvent.Send)
                val sent = awaitUntil { it.content().items.isNotEmpty() }.content()
                assertEquals("", sent.draft)
                assertTrue(sent.conversationId != null)
                val bubble =
                    sent.items
                        .filterIsInstance<ChatItem.Bubble>()
                        .single()
                        .message
                assertEquals("Is it still available?", bubble.body)
                assertTrue(bubble.fromUser)
                assertEquals(DeliveryStatus.Sending, bubble.status)
                cancelAndIgnoreRemainingEvents()
            }
            val conversation = chats.conversations.value.single()
            assertEquals(guest, conversation.userId)
            assertEquals(listOf(Triple(guest, conversation.id, "Is it still available?")), responder.calls)
            assertEquals(listOf(InteractionType.ContactedAgent), interactions.events.value.map { it.type })
        }

    @Test
    fun aQuickReplySendsItsTextAndKeepsTheDraft() =
        runTest(main.dispatcher) {
            thread()
            val vm = viewModel(conversationId = "c1")
            vm.state.test {
                awaitUntil { it is ChatUiState.Content }
                vm.onEvent(ChatUiEvent.DraftChanged("half-written"))
                vm.onEvent(ChatUiEvent.SendQuickReply("Is the price negotiable?"))
                val content = awaitUntil { it.content().items.isNotEmpty() }.content()
                assertEquals("half-written", content.draft)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("Is the price negotiable?", responder.calls.single().third)
            // An existing thread is not a new contact.
            assertTrue(interactions.events.value.isEmpty())
        }

    @Test
    fun blankDraftsAreNotSentAndLongOnesAreCapped() =
        runTest(main.dispatcher) {
            thread()
            val vm = viewModel(conversationId = "c1")
            vm.state.test {
                awaitUntil { it is ChatUiState.Content }
                vm.onEvent(ChatUiEvent.DraftChanged("   "))
                vm.onEvent(ChatUiEvent.Send)
                vm.onEvent(ChatUiEvent.DraftChanged("x".repeat(ChatViewModel.MAX_MESSAGE_LENGTH + 50)))
                assertEquals(
                    ChatViewModel.MAX_MESSAGE_LENGTH,
                    awaitUntil { it.content().draft.length > 3 }.content().draft.length,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(responder.calls.isEmpty())
        }

    @Test
    fun aFailedSendGivesTheDraftBack() =
        runTest(main.dispatcher) {
            thread()
            chats.failSends = true
            val vm = viewModel(conversationId = "c1")
            vm.state.test {
                awaitUntil { it is ChatUiState.Content }
                vm.onEvent(ChatUiEvent.DraftChanged("Hello"))
                vm.onEvent(ChatUiEvent.Send)
                assertEquals(
                    "Hello",
                    awaitUntil { it.content().draft == "Hello" && it.content().items.isEmpty() }.content().draft,
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertTrue(responder.calls.isEmpty())
        }

    @Test
    fun anotherUsersOrAMissingThreadIsNotFound() =
        runTest(main.dispatcher) {
            thread(user = "someone-else", id = "theirs")
            viewModel(conversationId = "theirs").state.test {
                assertEquals(ChatUiState.NotFound, awaitUntil { it != ChatUiState.Loading })
                cancelAndIgnoreRemainingEvents()
            }
            viewModel(conversationId = "nope").state.test {
                assertEquals(ChatUiState.NotFound, awaitUntil { it != ChatUiState.Loading })
                cancelAndIgnoreRemainingEvents()
            }
            viewModel(propertyId = "no-such-listing").state.test {
                assertEquals(ChatUiState.NotFound, awaitUntil { it != ChatUiState.Loading })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun whileVisibleTheThreadIsActiveAndItsMessagesAndNotificationsAreRead() =
        runTest(main.dispatcher) {
            thread()
            chats.addMessage(ChatMessage("a1", "c1", false, "Yes, it is available.", now, DeliveryStatus.Sent))
            notifications.add(guest, NewNotification(NotificationKind.NewMessage, "Agent", georgie.id, "Yes", "c1"))
            val vm = viewModel(conversationId = "c1")
            vm.state.test {
                awaitUntil { it is ChatUiState.Content }
                vm.onEvent(ChatUiEvent.VisibilityChanged(true))
                val read =
                    awaitUntil { state ->
                        state.content().items.any {
                            it is ChatItem.Bubble &&
                                it.message.status == DeliveryStatus.Read
                        }
                    }
                assertTrue(read is ChatUiState.Content)
                assertTrue(active.isActive("c1"))
                assertEquals(0, chats.observeUnreadCount(guest).first())
                assertEquals(0, notifications.observeUnreadCount(guest).first())
                assertEquals(listOf("c1"), notifier.cancelled.distinct())
                // A reply landing while the screen is open is read at once.
                chats.addMessage(ChatMessage("a2", "c1", false, "Anything else?", now, DeliveryStatus.Sent))
                awaitUntil { state ->
                    state.content().items.filterIsInstance<ChatItem.Bubble>().all {
                        it.message.status ==
                            DeliveryStatus.Read
                    }
                }
                vm.onEvent(ChatUiEvent.VisibilityChanged(false))
                cancelAndIgnoreRemainingEvents()
            }
            assertFalse(active.isActive("c1"))
        }

    @Test
    fun theAgentTypingFlagAndDaySeparatorsAreExposed() =
        runTest(main.dispatcher) {
            thread()
            val yesterday = now.minus(Duration.ofDays(1))
            chats.addMessage(ChatMessage("u1", "c1", true, "Hi", yesterday, DeliveryStatus.Read))
            chats.addMessage(ChatMessage("a1", "c1", false, "Hello", yesterday.plusSeconds(60), DeliveryStatus.Read))
            chats.addMessage(ChatMessage("u2", "c1", true, "Can I visit?", now, DeliveryStatus.Sent))
            val vm = viewModel(conversationId = "c1")
            vm.state.test {
                val content = awaitUntil { it is ChatUiState.Content }.content()
                val today = now.atZone(zone).toLocalDate()
                assertEquals(
                    listOf("day-${today.minusDays(1)}", "u1", "a1", "day-$today", "u2"),
                    content.items.map { it.key },
                )
                assertFalse(content.agentTyping)
                responder.typing.value = setOf("c1")
                assertTrue(awaitUntil { it.content().agentTyping }.content().agentTyping)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
