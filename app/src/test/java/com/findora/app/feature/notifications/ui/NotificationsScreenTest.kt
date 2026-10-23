package com.findora.app.feature.notifications.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.Conversation
import com.findora.app.core.domain.chat.ConversationSummary
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.testing.notification
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.chat.ConversationsScreen
import com.findora.app.feature.chat.ConversationsUiState
import com.findora.app.feature.notifications.NotificationsScreen
import com.findora.app.feature.notifications.NotificationsUiEvent
import com.findora.app.feature.notifications.NotificationsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class NotificationsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-10-26T10:10:00Z")
    private val events = mutableListOf<NotificationsUiEvent>()

    private fun show(state: NotificationsUiState) {
        compose.setContent {
            FindoraTheme {
                NotificationsScreen(state = state, onEvent = { events += it }, onBack = {})
            }
        }
    }

    private fun content(vararg items: com.findora.app.core.domain.notification.AppNotification) =
        NotificationsUiState.Content(items.toList(), zone, now.atZone(zone).toLocalDate())

    @Test
    fun listsEachKindWithUnreadDotsAndOpensOnTap() {
        val message = notification(1, createdAt = now)
        val booked = notification(2, NotificationKind.ViewingBooked, read = true, createdAt = now)
        val reminder = notification(3, NotificationKind.ViewingReminder, createdAt = now)
        show(content(message, booked, reminder))
        // One sentence per row for TalkBack, the unread state first.
        compose
            .onNode(
                hasContentDescription(
                    "Unread. New message from Anu Thomas. Yes, it is still available.",
                    substring = true,
                ),
            ).assertExists()
        compose.onNode(hasContentDescription("Unread. Viewing in one hour", substring = true)).assertExists()
        val bookedText = description("notification_2")
        assertTrue(bookedText.startsWith("Viewing booked. " + seedProperties.first().title))
        compose.onNodeWithTag("notification_1").performClick()
        assertEquals(NotificationsUiEvent.Open(message), events.single())
    }

    private fun description(tag: String): String =
        compose
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.ContentDescription]
            .single()

    @Test
    fun markAllReadIsOfferedOnlyWithUnreadEntries() {
        show(content(notification(1, createdAt = now)))
        compose.onNodeWithTag("markAllRead").assertIsEnabled().performClick()
        assertEquals(listOf<NotificationsUiEvent>(NotificationsUiEvent.MarkAllRead), events)
    }

    @Test
    fun withEverythingReadMarkAllIsDisabled() {
        show(content(notification(1, read = true, createdAt = now)))
        compose.onNodeWithTag("markAllRead").assertIsNotEnabled()
    }

    @Test
    fun theDeleteActionRemovesAnEntry() {
        show(content(notification(7, createdAt = now)))
        val row = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.CustomActions)).fetchSemanticsNode()
        val action = row.config[SemanticsActions.CustomActions].single { it.label == "Delete" }
        action.action()
        assertEquals(listOf<NotificationsUiEvent>(NotificationsUiEvent.Delete(7)), events)
    }

    @Test
    fun nothingYetSaysAllCaughtUp() {
        show(content())
        compose.onNodeWithText("You're all caught up").assertIsDisplayed()
        compose.onNodeWithTag("markAllRead").assertIsNotEnabled()
    }

    @Test
    fun theChatTabListsThreadsWithPreviewTimeAndUnreadBadge() {
        val georgie = seedProperties.first()
        val conversation =
            Conversation(
                "c1",
                "guest",
                georgie.id,
                georgie.title,
                ChatAgent(georgie.agent.id, georgie.agent.name, georgie.agent.avatar),
                now,
            )
        val replied =
            ConversationSummary(conversation, ChatMessage("a1", "c1", false, "Yes, it is", now, DeliveryStatus.Sent), 2)
        val opened = mutableListOf<String>()
        compose.setContent {
            FindoraTheme {
                ConversationsScreen(
                    state =
                        ConversationsUiState.Content(
                            listOf(replied),
                            emptySet(),
                            zone,
                            now.atZone(zone).toLocalDate(),
                        ),
                    onOpenConversation = { opened += it },
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithText("Demo agent — automated replies").assertIsDisplayed()
        compose.onNode(hasContentDescription("2 unread messages", substring = true)).assertExists()
        compose.onNode(hasContentDescription(georgie.agent.name, substring = true)).performClick()
        assertEquals(listOf("c1"), opened)
    }

    @Test
    fun theChatTabWithoutThreadsExplainsHowToStartOne() {
        compose.setContent {
            FindoraTheme {
                ConversationsScreen(
                    state = ConversationsUiState.Content(emptyList(), emptySet(), zone, now.atZone(zone).toLocalDate()),
                    onOpenConversation = {},
                    contentPadding = PaddingValues(),
                )
            }
        }
        compose.onNodeWithText("No conversations yet").assertIsDisplayed()
    }
}
