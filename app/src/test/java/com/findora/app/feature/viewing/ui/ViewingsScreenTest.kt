package com.findora.app.feature.viewing.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Viewing
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.viewing.ViewingItem
import com.findora.app.feature.viewing.ViewingTestEnv
import com.findora.app.feature.viewing.ViewingsScreen
import com.findora.app.feature.viewing.ViewingsUiEvent
import com.findora.app.feature.viewing.ViewingsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Duration
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class ViewingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val now = Instant.parse("2026-10-26T09:00:00Z")
    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val other = seedProperties.first { it.id != georgie.id }
    private val upcoming =
        ViewingItem(Viewing(1, "guest", georgie.id, now.plus(Duration.ofDays(1)), 30, "Ring twice", now), georgie)
    private val past = ViewingItem(Viewing(2, "guest", other.id, now.minus(Duration.ofDays(2)), 30, "", now), other)
    private val events = mutableListOf<ViewingsUiEvent>()
    private val opened = mutableListOf<String>()

    private fun show(state: ViewingsUiState) {
        compose.setContent {
            FindoraTheme {
                ViewingsScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    onOpenProperty = { opened += it },
                )
            }
        }
    }

    private fun content(pending: ViewingItem? = null) =
        ViewingsUiState.Content(listOf(upcoming), listOf(past), pending, ViewingTestEnv.TUNIS)

    @Test
    fun listsUpcomingAndPastAndOpensTheListing() {
        show(content())
        compose.onNodeWithText("My viewings").assertIsDisplayed()
        compose.onNodeWithText("Upcoming").assertIsDisplayed()
        compose.onNodeWithText("Past").assertIsDisplayed()
        compose.onNodeWithText("Note: Ring twice").assertIsDisplayed()
        compose.onNodeWithTag("viewing_2").performClick()
        assertEquals(listOf(other.id), opened)
        // Only upcoming viewings can be cancelled.
        compose.onNodeWithTag("cancel_1").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasTestTag("cancel_2")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithTag("cancel_1").performClick()
        assertEquals(listOf<ViewingsUiEvent>(ViewingsUiEvent.RequestCancel(1)), events)
    }

    @Test
    fun cancellingAsksForConfirmation() {
        show(content(pending = upcoming))
        compose.onNodeWithText("Cancel this viewing?").assertIsDisplayed()
        compose.onNodeWithText("Keep it").performClick()
        compose.onNodeWithTag("confirmCancel").performClick()
        assertEquals(listOf(ViewingsUiEvent.DismissCancel, ViewingsUiEvent.ConfirmCancel), events)
    }

    @Test
    fun anEmptyListExplainsHowToBook() {
        show(ViewingsUiState.Content(emptyList(), emptyList(), null, ViewingTestEnv.TUNIS))
        compose.onNodeWithTag("viewingsEmpty").assertIsDisplayed()
        compose.onNodeWithText("No viewings yet").assertIsDisplayed()
    }
}
