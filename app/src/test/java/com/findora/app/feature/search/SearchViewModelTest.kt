package com.findora.app.feature.search

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSearchHistoryRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = FakeSearchHistoryRepository()
    private val filters = InMemoryHomeFilterRepository()
    private val auth = FakeAuthRepository()

    private fun viewModel() =
        SearchViewModel(
            properties = FakePropertyRepository(),
            history = history,
            filters = filters,
            auth = auth,
            currentRegion =
                CurrentRegion(
                    FakeSettingsRepository(),
                    SeedRegionRepository(fileSeedReader(), main.dispatcher),
                ),
        )

    private suspend fun ReceiveTurbine<SearchUiState>.awaitUntil(predicate: (SearchUiState) -> Boolean): SearchUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun typingShowsDebouncedNeighbourhoodAndHomeSuggestions() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                assertTrue(awaitItem().showRecents)
                vm.onEvent(SearchUiEvent.QueryChanged("K"))
                vm.onEvent(SearchUiEvent.QueryChanged("Ka"))
                val state = awaitUntil { it.suggestionsLoaded }
                assertEquals("Ka", state.query)
                assertTrue(state.suggestions.any { it.text == "Kakkanad" && it.kind == SearchSuggestion.Kind.Locality })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun textWithoutMatchesReportsNoSuggestions() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.QueryChanged("zzqx"))
                val state = awaitUntil { it.suggestionsLoaded }
                assertEquals(emptyList<SearchSuggestion>(), state.suggestions)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun submittingAppliesTheTextToHomeRecordsItAndCloses() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(SearchUiEvent.Submit("  Fort Kochi "))
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            assertEquals("Fort Kochi", filters.filter.value.query)
            assertEquals(
                listOf("Fort Kochi"),
                history.observeRecent(AuthRepository.GUEST_USER_ID).first().map { it.query },
            )
        }

    @Test
    fun submittingBlankClearsTheSearchWithoutRecordingIt() =
        runTest(main.dispatcher) {
            filters.update { it.copy(query = "Kaloor") }
            val vm = viewModel()
            assertEquals("Kaloor", vm.state.value.query)
            vm.effects.test {
                vm.onEvent(SearchUiEvent.Submit("   "))
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            assertEquals("", filters.filter.value.query)
            assertEquals(emptyList<Any>(), history.observeRecent(AuthRepository.GUEST_USER_ID).first())
        }

    @Test
    fun recentSearchesAreKeptPerUserAndCanBeDeletedOrCleared() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.Submit("Kakkanad"))
                vm.onEvent(SearchUiEvent.Submit("Edappally"))
                vm.onEvent(SearchUiEvent.Submit("Vyttila"))
                assertEquals(
                    listOf("Vyttila", "Edappally", "Kakkanad"),
                    awaitUntil { it.recents.size == 3 }.recents.map { it.query },
                )
                vm.onEvent(SearchUiEvent.DeleteRecent("edappally"))
                assertEquals(
                    listOf("Vyttila", "Kakkanad"),
                    awaitUntil { it.recents.size == 2 }.recents.map { it.query },
                )

                auth.signIn()
                assertEquals(emptyList<Any>(), awaitUntil { it.recents.isEmpty() }.recents)
                vm.onEvent(SearchUiEvent.Submit("Kaloor"))
                assertEquals(listOf("Kaloor"), awaitUntil { it.recents.size == 1 }.recents.map { it.query })
                vm.onEvent(SearchUiEvent.ClearRecents)
                assertEquals(emptyList<Any>(), awaitUntil { it.recents.isEmpty() }.recents)
                cancelAndIgnoreRemainingEvents()
            }
            // The guest's history was untouched by the signed-in user's clear.
            assertEquals(2, history.observeRecent(AuthRepository.GUEST_USER_ID).first().size)
        }
}
