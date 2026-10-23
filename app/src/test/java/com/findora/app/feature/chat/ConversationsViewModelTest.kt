package com.findora.app.feature.chat

import app.cash.turbine.test
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeChatRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.RecordingAgentResponder
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.viewing.awaitUntil
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeViewingRepository
import com.findora.app.core.testing.RecordingAlertScheduler
import com.findora.app.core.testing.RecordingWidgetRefresher
import com.findora.app.navigation.MainShellViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class ConversationsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private var now: Instant = Instant.parse("2026-10-26T09:10:00Z")
    private val auth = FakeAuthRepository()
    private val chats = FakeChatRepository { now }
    private val responder = RecordingAgentResponder()
    private val guest = AuthRepository.GUEST_USER_ID

    private fun viewModel() = ConversationsViewModel(chats, responder, auth, { now }, ZoneId.of("Africa/Tunis"))

    private fun ConversationsUiState.content() = this as ConversationsUiState.Content

    @Test
    fun listsTheUsersThreadsNewestFirstWithUnreadCountsAndTyping() =
        runTest(main.dispatcher) {
            val (first, second) = seedProperties.take(2)
            val a = chats.startConversation(guest, first)
            val b = chats.startConversation(guest, second)
            val theirs = chats.startConversation("someone-else", first)
            chats.sendMessage(guest, a.id, "Is it still available?")
            now = now.plusSeconds(60)
            chats.receiveAgentMessage(a.id, "Yes it is")
            chats.receiveAgentMessage(theirs.id, "Not for you")
            now = now.plusSeconds(60)
            chats.sendMessage(guest, b.id, "Can I visit this week?")
            val vm = viewModel()
            vm.state.test {
                val content = awaitUntil { it is ConversationsUiState.Content }.content()
                assertEquals(listOf(b.id, a.id), content.conversations.map { it.conversation.id })
                assertEquals(listOf(0, 1), content.conversations.map { it.unreadCount })
                assertEquals(1, content.unreadTotal)
                assertEquals(
                    "Yes it is",
                    content.conversations
                        .last()
                        .lastMessage.body,
                )
                responder.typing.value = setOf(b.id)
                assertEquals(setOf(b.id), awaitUntil { it.content().typing.isNotEmpty() }.content().typing)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun noThreadsIsEmpty() =
        runTest(main.dispatcher) {
            viewModel().state.test {
                assertTrue(awaitUntil { it is ConversationsUiState.Content }.content().conversations.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theChatTabBadgeCountsUnreadAgentMessagesOfTheSignedInUser() =
        runTest(main.dispatcher) {
            val thread = chats.startConversation(auth.account.id, seedProperties.first())
            chats.receiveAgentMessage(thread.id, "Hello")
            chats.receiveAgentMessage(thread.id, "Viewing slots")
            val vm =
                MainShellViewModel(
                    chats,
                    auth,
                    FakeFavoritesRepository(),
                    FakeViewingRepository { Instant.EPOCH },
                    RecordingAlertScheduler(),
                    RecordingWidgetRefresher(),
                )
            vm.unreadMessages.test {
                assertEquals(0, awaitItem())
                auth.signIn()
                assertEquals(2, awaitItem())
                chats.markConversationRead(auth.account.id, thread.id)
                assertEquals(0, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}
