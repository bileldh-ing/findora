package com.findora.app.feature.match.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeInteractionRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.observeMatchContext
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.match.deck.DeckScreen
import com.findora.app.feature.match.deck.DeckUiEffect
import com.findora.app.feature.match.deck.DeckViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DeckScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val guest = AuthRepository.GUEST_USER_ID
    private val favorites = FakeFavoritesRepository()
    private val interactions = FakeInteractionRepository()
    private val effects = mutableListOf<DeckUiEffect>()
    private val opened = mutableListOf<String>()
    private var matchesOpened = 0

    /** Three Kerala homes so the deck can be finished quickly; the real ViewModel over in-memory fakes. */
    private fun showDeck(
        darkTheme: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ): DeckViewModel {
        val homes = seedProperties.filter { it.regionId == "kerala" }.take(3)
        val properties = FakePropertyRepository(homes)
        val auth = FakeAuthRepository()
        val settings = FakeSettingsRepository()
        val vm =
            DeckViewModel(
                properties = properties,
                favorites = favorites,
                interactions = interactions,
                auth = auth,
                currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined)),
                clock = { Instant.ofEpochSecond(1_790_000_000) },
                observeMatchContext = observeMatchContext(auth = auth, properties = properties, settings = settings),
            )
        compose.setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(vm) { vm.effects.collect { effects += it } }
            FindoraTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    DeckScreen(
                        state = state,
                        onEvent = vm::onEvent,
                        onBack = {},
                        onOpenMatches = { matchesOpened++ },
                        onOpenProperty = { opened += it },
                        reduceMotion = true,
                    )
                }
            }
        }
        compose.waitUntil(5_000) { !vm.state.value.isLoading }
        return vm
    }

    private fun currentId(vm: DeckViewModel): String =
        vm.state.value.current!!
            .property.id

    private fun favoriteIds(): Set<String> = runBlocking { favorites.observeFavoriteIds(guest).first() }

    @Test
    fun swipesRightLeftAndUpThenShowsTheEndOfTheDeck() {
        val vm = showDeck()
        compose.onNodeWithTag("deckProgress").assertTextEquals("1 / 3")
        val first = currentId(vm)
        compose.onNodeWithTag("deckCard_$first").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(first in favoriteIds())
        compose.onNodeWithTag("deckProgress").assertTextEquals("2 / 3")

        val second = currentId(vm)
        compose.onNodeWithTag("deckCard_$second").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertFalse(second in favoriteIds())
        assertEquals(
            InteractionType.DeckSkipped,
            interactions.events.value
                .last()
                .type,
        )

        val third = currentId(vm)
        compose.onNodeWithTag("deckCard_$third").performTouchInput { swipeUp() }
        compose.waitForIdle()
        assertEquals(listOf<DeckUiEffect>(DeckUiEffect.OpenScheduleViewing(third)), effects)
        assertTrue(third in favoriteIds())

        compose.onNodeWithTag("deckFinished").assertIsDisplayed()
        compose.onNodeWithText("You have seen every home").assertIsDisplayed()
        compose.onNodeWithText("You kept 2 homes this round.").assertIsDisplayed()
        compose.onNodeWithTag("deckSeeMatches").performClick()
        assertEquals(1, matchesOpened)
        compose.onNodeWithTag("deckRestart").performClick()
        compose.waitForIdle()
        // Restart deals the skipped home again; the saved ones stay out.
        assertEquals(
            listOf(second),
            vm.state.value.cards
                .map { it.property.id },
        )
    }

    @Test
    fun aShortDragSpringsBackWithoutActing() {
        val vm = showDeck()
        val first = currentId(vm)
        compose.onNodeWithTag("deckCard_$first").performTouchInput {
            swipe(center, center.copy(x = center.x + width * 0.1f), durationMillis = 800)
        }
        compose.waitForIdle()
        assertEquals(first, currentId(vm))
        assertTrue(interactions.events.value.isEmpty())
    }

    @Test
    fun buttonsUndoAndTalkBackActionsDoWhatTheGesturesDo() {
        val vm = showDeck()
        compose.onNodeWithTag("deckUndo").assertIsNotEnabled()
        val first = currentId(vm)
        compose.onNodeWithTag("deckSave").performClick()
        compose.waitForIdle()
        assertTrue(first in favoriteIds())
        compose.onNodeWithTag("deckUndo").performClick()
        compose.waitForIdle()
        assertFalse(first in favoriteIds())
        assertEquals(first, currentId(vm))

        compose.onNodeWithTag("deckSkip").performClick()
        compose.waitForIdle()
        assertEquals(
            InteractionType.DeckSkipped,
            interactions.events.value
                .last()
                .type,
        )

        val second = currentId(vm)
        val card = compose.onNodeWithTag("deckCard_$second")
        val actions = card.fetchSemanticsNode().config[SemanticsActions.CustomActions]
        assertEquals(listOf("Save", "Skip", "Super like"), actions.map { it.label })
        compose.runOnIdle { actions.first { it.label == "Super like" }.action() }
        compose.waitForIdle()
        assertEquals(listOf<DeckUiEffect>(DeckUiEffect.OpenScheduleViewing(second)), effects)

        val third = currentId(vm)
        compose.onNodeWithTag("deckCard_$third").performClick()
        assertEquals(listOf(third), opened)
    }

    @Test
    fun inRightToLeftAndDarkASwipeRightStillSaves() {
        val vm = showDeck(darkTheme = true, direction = LayoutDirection.Rtl)
        val first = currentId(vm)
        compose.onNodeWithTag("deckCard_$first").performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertTrue(first in favoriteIds())
        compose.onNodeWithTag("deckSuperLike").performClick()
        compose.waitForIdle()
        assertEquals(2, vm.state.value.savedCount)
    }
}
