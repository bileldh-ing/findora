package com.findora.app.feature.detail

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.domain.model.InteractionEvent
import com.findora.app.core.domain.model.InteractionType
import com.findora.app.core.domain.model.Property
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.InteractionRepository
import com.findora.app.core.domain.repository.PropertyRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.seedProperties
import com.findora.app.navigation.PropertyDeepLink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

class DetailViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val georgie: Property = seedProperties.first { it.title == "Georgie House" }
    private val interactions = RecordingInteractionRepository()
    private val favorites = FakeFavoritesRepository()
    private val auth = FakeAuthRepository()

    private fun viewModel(
        id: String = georgie.id,
        properties: PropertyRepository = FakePropertyRepository(),
    ) = DetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf(DetailViewModel.PROPERTY_ID_KEY to id)),
        properties = properties,
        favorites = favorites,
        interactions = interactions,
        auth = auth,
        clock = { Instant.EPOCH },
    )

    @Test
    fun opensWithTheListingAndSimilarHomesAndRecordsAView() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val content = awaitSettled() as DetailUiState.Content
                assertEquals(georgie, content.property)
                assertFalse(content.isFavorite)
                assertTrue(content.similar.isNotEmpty())
                assertTrue(content.similar.size <= DetailViewModel.SIMILAR_LIMIT)
                assertTrue(content.similar.none { it.id == georgie.id })
                assertTrue(content.similar.all { it.type == PropertyType.House && it.regionId == georgie.regionId })
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(
                    InteractionEvent(AuthRepository.GUEST_USER_ID, georgie.id, InteractionType.Viewed, Instant.EPOCH),
                ),
                interactions.events.value,
            )
        }

    @Test
    fun anUnknownListingIsNotFound() =
        runTest(main.dispatcher) {
            viewModel(id = "fd-missing-0001").state.test {
                assertEquals(DetailUiState.Error(DetailError.NotFound), awaitSettled())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aFailingSourceShowsAnErrorAndRetryRecovers() =
        runTest(main.dispatcher) {
            val flaky = FlakyPropertyRepository(failures = 1)
            val vm = viewModel(properties = flaky)
            vm.state.test {
                assertEquals(DetailUiState.Error(DetailError.Unavailable), awaitSettled())
                vm.onEvent(DetailUiEvent.Retry)
                assertEquals(georgie, (awaitSettled() as DetailUiState.Content).property)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun favouriteToggleFollowsTheUserAndRecordsTheSignal() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.state.test {
                assertFalse((awaitSettled() as DetailUiState.Content).isFavorite)
                vm.onEvent(DetailUiEvent.ToggleFavorite)
                assertTrue((awaitItem() as DetailUiState.Content).isFavorite)
                assertEquals(setOf(georgie.id), favorites.observeFavoriteIds(auth.account.id).first())
                vm.onEvent(DetailUiEvent.ToggleFavorite)
                val unsaved = awaitItem() as DetailUiState.Content
                assertFalse(unsaved.isFavorite)
                val similar = unsaved.similar.first()
                vm.onEvent(DetailUiEvent.ToggleSimilarFavorite(similar.id))
                assertEquals(setOf(similar.id), (awaitItem() as DetailUiState.Content).similarFavoriteIds)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                listOf(
                    InteractionType.Viewed,
                    InteractionType.Favorited,
                    InteractionType.Unfavorited,
                    InteractionType.Favorited,
                ),
                interactions.events.value.map { it.type },
            )
            assertTrue(interactions.events.value.all { it.userId == auth.account.id })
        }

    @Test
    fun shareContactAndMapActionsBecomeEffects() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            backgroundScope.launch { vm.state.collect {} }
            vm.effects.test {
                vm.onEvent(DetailUiEvent.Share)
                assertEquals(
                    DetailUiEffect.Share(georgie.title, "findora://property/${georgie.id}"),
                    awaitItem(),
                )
                vm.onEvent(DetailUiEvent.CallAgent)
                assertEquals(DetailUiEffect.Dial(georgie.agent.phone), awaitItem())
                vm.onEvent(DetailUiEvent.EmailAgent)
                val email = awaitItem() as DetailUiEffect.Email
                assertEquals(georgie.agent.email, email.address)
                assertTrue(email.address.endsWith(".example"))
                assertEquals(PropertyDeepLink.uriFor(georgie.id), email.link)
                vm.onEvent(DetailUiEvent.OpenInMaps)
                assertEquals(DetailUiEffect.OpenMap(georgie.location, georgie.title), awaitItem())
            }
            assertEquals(
                listOf(
                    InteractionType.Viewed,
                    InteractionType.Shared,
                    InteractionType.ContactedAgent,
                    InteractionType.ContactedAgent,
                ),
                interactions.events.value.map { it.type },
            )
        }

    @Test
    fun aFailingInteractionStoreNeverBreaksThePage() =
        runTest(main.dispatcher) {
            val vm =
                DetailViewModel(
                    savedStateHandle = SavedStateHandle(mapOf(DetailViewModel.PROPERTY_ID_KEY to georgie.id)),
                    properties = FakePropertyRepository(),
                    favorites = favorites,
                    interactions = RecordingInteractionRepository(fail = true),
                    auth = auth,
                    clock = { Instant.EPOCH },
                )
            vm.state.test {
                assertTrue(awaitSettled() is DetailUiState.Content)
                cancelAndIgnoreRemainingEvents()
            }
        }
}

/** The next state that is not Loading (an unconfined dispatcher may resolve before the first collection). */
private suspend fun ReceiveTurbine<DetailUiState>.awaitSettled(): DetailUiState {
    var state = awaitItem()
    while (state == DetailUiState.Loading) state = awaitItem()
    return state
}

/** Keeps every recorded event in memory; [fail] makes every write throw. */
private class RecordingInteractionRepository(
    private val fail: Boolean = false,
) : InteractionRepository {
    val events = MutableStateFlow<List<InteractionEvent>>(emptyList())

    override suspend fun record(event: InteractionEvent) {
        if (fail) throw IOException("disk full")
        events.update { it + event }
    }

    override fun observeRecent(
        userId: String,
        limit: Int,
    ): Flow<List<InteractionEvent>> = events.map { list -> list.filter { it.userId == userId }.takeLast(limit) }
}

/** Fails the first [failures] reads of a listing, then serves the seed data. */
private class FlakyPropertyRepository(
    private var failures: Int,
    private val delegate: FakePropertyRepository = FakePropertyRepository(),
) : PropertyRepository by delegate {
    override fun observeProperty(id: String): Flow<Property?> =
        flow {
            if (failures > 0) {
                failures -= 1
                throw IOException("cache unreadable")
            }
            emitAll(delegate.observeProperty(id))
        }
}
