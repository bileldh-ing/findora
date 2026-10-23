package com.findora.app.feature.match.deck

import app.cash.turbine.test
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeInteractionRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.observeMatchContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class DeckViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val favorites = FakeFavoritesRepository()
    private val interactions = FakeInteractionRepository()
    private val profiles = FakeMatchProfileRepository()
    private val properties = FakePropertyRepository()
    private val settings = FakeSettingsRepository()
    private val guest = AuthRepository.GUEST_USER_ID

    private fun viewModel() =
        DeckViewModel(
            properties = properties,
            favorites = favorites,
            interactions = interactions,
            auth = auth,
            currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
            clock = { Instant.ofEpochSecond(1_790_000_000) },
            observeMatchContext =
                observeMatchContext(
                    auth = auth,
                    profiles = profiles,
                    interactions = interactions,
                    properties = properties,
                    settings = settings,
                ),
        )

    private fun regionId(): String =
        kotlinx.coroutines.runBlocking {
            CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)).region.first().id
        }

    @Test
    fun dealsTheRegionInRecommendedOrderWithoutAProfile() =
        runTest(main.dispatcher) {
            val state = viewModel().state.value
            val region = properties.matching(regionId(), com.findora.app.core.domain.model.PropertyFilter())
            assertFalse(state.isLoading)
            assertFalse(state.rankedByMatch)
            assertEquals(region.size, state.total)
            assertEquals(1, state.position)
            assertTrue(state.cards.all { it.matchPercent == null })
            assertFalse(state.canUndo)
        }

    @Test
    fun withAProfileCardsCarryTheirMatchScore() =
        runTest(main.dispatcher) {
            profiles.save(guest, PreferenceProfile(budget = BudgetRange(null, 60_000, Currency.INR)))
            val state = viewModel().state.value
            assertTrue(state.rankedByMatch)
            assertTrue(state.cards.all { it.matchPercent != null })
            val scores = state.cards.map { it.matchPercent!! }
            assertEquals(scores.sortedDescending(), scores)
        }

    @Test
    fun saveSkipSuperLikeAndUndoArePersistedAndReverted() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.effects.test {
                val first = vm.state.value.current!!.property.id
                vm.onEvent(DeckUiEvent.Act(DeckAction.Save))
                assertTrue(first in favorites.observeFavoriteIds(guest).first())
                assertEquals(InteractionType.DeckSaved, interactions.events.value.last().type)
                assertEquals(2, vm.state.value.position)
                assertEquals(DeckAction.Save, vm.state.value.lastAction)

                val second = vm.state.value.current!!.property.id
                vm.onEvent(DeckUiEvent.Act(DeckAction.Skip))
                assertFalse(second in favorites.observeFavoriteIds(guest).first())
                assertEquals(InteractionType.DeckSkipped, interactions.events.value.last().type)

                val third = vm.state.value.current!!.property.id
                vm.onEvent(DeckUiEvent.Act(DeckAction.SuperLike))
                assertEquals(DeckUiEffect.OpenScheduleViewing(third), awaitItem())
                assertTrue(third in favorites.observeFavoriteIds(guest).first())
                assertEquals(2, vm.state.value.savedCount)

                vm.onEvent(DeckUiEvent.Undo)
                assertFalse(third in favorites.observeFavoriteIds(guest).first())
                assertEquals(third, vm.state.value.current!!.property.id)
                assertEquals(DeckAction.Skip, vm.state.value.lastAction)

                vm.onEvent(DeckUiEvent.Undo)
                assertEquals(second, vm.state.value.current!!.property.id)
                assertTrue(interactions.events.value.none { it.type == InteractionType.DeckSkipped })

                vm.onEvent(DeckUiEvent.Undo)
                assertEquals(first, vm.state.value.current!!.property.id)
                assertTrue(favorites.observeFavoriteIds(guest).first().isEmpty())
                assertTrue(interactions.events.value.isEmpty())
                assertFalse(vm.state.value.canUndo)
                assertEquals(0, vm.state.value.savedCount)
                expectNoEvents()
            }
        }

    @Test
    fun undoKeepsAFavouriteTheUserHadBefore() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            val id = vm.state.value.current!!.property.id
            // Saved elsewhere while the deck was open: the deck must not remove it on Undo.
            favorites.toggle(guest, id)
            vm.onEvent(DeckUiEvent.Act(DeckAction.Save))
            vm.onEvent(DeckUiEvent.Undo)
            assertTrue(id in favorites.observeFavoriteIds(guest).first())
        }

    @Test
    fun reachesTheEndThenRestartsWithTheSkippedHomesOnly() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                val total = state.total
                val kept = state.cards.first().property.id
                vm.onEvent(DeckUiEvent.Act(DeckAction.Save))
                repeat(total - 1) { vm.onEvent(DeckUiEvent.Act(DeckAction.Skip)) }
                state = expectMostRecentItem()
                assertTrue(state.isFinished)
                assertNull(state.current)
                assertEquals(total, state.position)
                assertEquals(1f, state.progress)
                assertEquals(1, state.savedCount)
                vm.onEvent(DeckUiEvent.Restart)
                state = expectMostRecentItem()
                assertFalse(state.isFinished)
                assertEquals(total - 1, state.total)
                assertFalse(state.cards.any { it.property.id == kept })
                assertFalse(state.canUndo)
                cancelAndIgnoreRemainingEvents()
            }
            // Opening the deck again leaves out both the saved and the skipped homes.
            val reopened = viewModel().state.value
            assertTrue(reopened.isFinished)
            assertEquals(0, reopened.total)
        }

    @Test
    fun actingOnAFinishedDeckDoesNothingAndFailuresShowRetry() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            repeat(vm.state.value.total) { vm.onEvent(DeckUiEvent.Act(DeckAction.Skip)) }
            val events = interactions.events.value.size
            vm.onEvent(DeckUiEvent.Act(DeckAction.Save))
            assertEquals(events, interactions.events.value.size)
            assertNotNull(vm.state.value)

            val broken =
                DeckViewModel(
                    properties = object : com.findora.app.core.domain.repository.PropertyRepository by properties {
                        override fun observeProperties(
                            regionId: String,
                            filter: com.findora.app.core.domain.model.PropertyFilter,
                        ) = kotlinx.coroutines.flow.flow<List<com.findora.app.core.domain.model.Property>> {
                            throw java.io.IOException("cache unreadable")
                        }
                    },
                    favorites = favorites,
                    interactions = interactions,
                    auth = auth,
                    currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
                    clock = { Instant.EPOCH },
                    observeMatchContext = observeMatchContext(auth = auth),
                )
            assertTrue(broken.state.value.loadFailed)
            assertFalse(broken.state.value.isFinished)
        }
}
