package com.findora.app.feature.chat.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.LayoutDirection
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.chat.ChatAgent
import com.findora.app.core.domain.chat.ChatMessage
import com.findora.app.core.domain.chat.DeliveryStatus
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.chat.ChatItem
import com.findora.app.feature.chat.ChatScreen
import com.findora.app.feature.chat.ChatUiEvent
import com.findora.app.feature.chat.ChatUiState
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
class ChatScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val zone = ZoneId.of("UTC")
    private val now: Instant = Instant.parse("2026-10-26T10:10:00Z")
    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val agent = ChatAgent(georgie.agent.id, georgie.agent.name, georgie.agent.avatar)
    private val events = mutableListOf<ChatUiEvent>()
    private val opened = mutableListOf<String>()

    private fun content(
        items: List<ChatItem> = emptyList(),
        typing: Boolean = false,
        draft: String = "",
    ) = ChatUiState.Content(
        conversationId = if (items.isEmpty()) null else "c1",
        agent = agent,
        propertyId = georgie.id,
        propertyTitle = georgie.title,
        property = georgie,
        items = items,
        agentTyping = typing,
        draft = draft,
        zone = zone,
        today = now.atZone(zone).toLocalDate(),
    )

    private fun bubble(
        id: String,
        fromUser: Boolean,
        body: String,
        status: DeliveryStatus = DeliveryStatus.Read,
    ) = ChatItem.Bubble(ChatMessage(id, "c1", fromUser, body, now, status))

    /** A host that behaves like the ViewModel for the send flow: the draft and sent messages live in state. */
    private fun interactive(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            var state by remember { mutableStateOf(content()) }
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                FindoraTheme {
                    ChatScreen(
                        state = state,
                        onEvent = { event ->
                            events += event
                            state =
                                when (event) {
                                    is ChatUiEvent.DraftChanged -> state.copy(draft = event.text)
                                    ChatUiEvent.Send -> state.withSent(state.draft).copy(draft = "")
                                    is ChatUiEvent.SendQuickReply -> state.withSent(event.text)
                                    is ChatUiEvent.VisibilityChanged -> state
                                }
                        },
                        onBack = {},
                        onOpenProperty = { opened += it },
                    )
                }
            }
        }
    }

    private fun ChatUiState.Content.withSent(text: String): ChatUiState.Content {
        val day = ChatItem.DaySeparator(today)
        val message = bubble("m${items.size}", true, text, DeliveryStatus.Sending)
        return copy(
            conversationId = "c1",
            items = (if (items.isEmpty()) listOf(day) else items) + message,
            agentTyping = true,
        )
    }

    @Test
    fun anEmptyThreadShowsTheAgentTheDemoLabelTheListingAndAHint() {
        interactive()
        compose.onNodeWithText(agent.name).assertIsDisplayed()
        compose.onNodeWithText("Demo agent — automated replies").assertIsDisplayed()
        compose.onNodeWithTag("chatStartHint").assertIsDisplayed()
        compose.onNodeWithText(georgie.title).assertIsDisplayed()
        compose.onNodeWithTag("chatListingCard").performClick()
        assertEquals(listOf(georgie.id), opened)
        compose.onNodeWithTag("chatSend").assertIsNotEnabled()
    }

    @Test
    fun typingAndSendingAddsABubbleClearsTheComposerAndShowsTheAgentTyping() {
        interactive()
        compose.onNodeWithTag("chatInput").performTextInput("Do you allow pets?")
        compose.onNodeWithTag("chatSend").assertIsEnabled().performClick()
        assertTrue(ChatUiEvent.Send in events)
        compose.onNode(hasContentDescription("Do you allow pets?", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNode(hasContentDescription("You, ", substring = true)).assertIsDisplayed()
        compose.onNode(hasContentDescription("Sending", substring = true)).assertIsDisplayed()
        compose.onNodeWithTag("typingIndicator").assertIsDisplayed()
        compose.onNode(hasContentDescription("${agent.name} is typing")).assertIsDisplayed()
        compose.onNodeWithTag("chatSend").assertIsNotEnabled()
    }

    @Test
    fun aQuickReplySendsItsText() {
        interactive()
        compose.onNodeWithTag("quick_StillAvailable").performClick()
        assertEquals(ChatUiEvent.SendQuickReply("Is it still available?"), events.last())
        compose.onNode(hasContentDescription("Is it still available?", substring = true)).assertIsDisplayed()
        compose.onNodeWithText("Can I visit this week?").assertIsDisplayed()
        compose.onNodeWithText("Is the price negotiable?").assertIsDisplayed()
    }

    @Test
    fun bubblesReadSenderTimeTextAndDeliveryToTalkBack() {
        compose.setContent {
            FindoraTheme {
                ChatScreen(
                    state =
                        content(
                            items =
                                listOf(
                                    ChatItem.DaySeparator(now.atZone(zone).toLocalDate()),
                                    bubble("u1", true, "Can I visit this week?"),
                                    bubble("a1", false, "I can show it on Tue 27 Oct at 09:00."),
                                ),
                        ),
                    onEvent = {},
                    onBack = {},
                    onOpenProperty = {},
                )
            }
        }
        val user = compose.onNodeWithTag("bubble_u1").fetchSemanticsNode().config
        val userText = user[androidx.compose.ui.semantics.SemanticsProperties.ContentDescription].single()
        assertTrue(userText.startsWith("You, "))
        assertTrue(userText.endsWith("Can I visit this week?. Read"))
        val agentText =
            compose
                .onNodeWithTag("bubble_a1")
                .fetchSemanticsNode()
                .config[
                androidx.compose.ui.semantics.SemanticsProperties.ContentDescription,
            ].single()
        assertTrue(agentText.startsWith("${agent.name}, "))
        assertTrue(agentText.endsWith("I can show it on Tue 27 Oct at 09:00."))
    }

    @Test
    fun inRightToLeftTheUsersBubblesSitOnTheLeft() {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                FindoraTheme(darkTheme = true) {
                    ChatScreen(
                        state = content(items = listOf(bubble("u1", true, "مرحبا"), bubble("a1", false, "أهلاً"))),
                        onEvent = {},
                        onBack = {},
                        onOpenProperty = {},
                    )
                }
            }
        }
        val user = compose.onNodeWithTag("bubble_u1").fetchSemanticsNode().boundsInRoot
        val agentBubble = compose.onNodeWithTag("bubble_a1").fetchSemanticsNode().boundsInRoot
        assertTrue(user.left < agentBubble.left)
    }

    @Test
    fun aMissingThreadExplainsAndOffersBack() {
        var back = 0
        compose.setContent {
            FindoraTheme {
                ChatScreen(state = ChatUiState.NotFound, onEvent = {}, onBack = { back++ }, onOpenProperty = {})
            }
        }
        compose.onNodeWithText("Conversation not available").assertIsDisplayed()
        compose.onNodeWithTag("chatBack").performClick()
        assertEquals(1, back)
    }
}
