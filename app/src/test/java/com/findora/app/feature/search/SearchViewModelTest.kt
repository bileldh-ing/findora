package com.findora.app.feature.search

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.alert.SaveSearch
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.search.QueryMeaning
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSavedSearchRepository
import com.findora.app.core.testing.FakeSearchHistoryRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.RecordingAlertScheduler
import com.findora.app.core.testing.fileSeedReader
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SearchViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val history = FakeSearchHistoryRepository()
    private val filters = InMemoryHomeFilterRepository()
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val profiles = FakeMatchProfileRepository()

    private val savedSearches = FakeSavedSearchRepository()
    private val alertScheduler = RecordingAlertScheduler()

    private fun viewModel(): SearchViewModel {
        val properties = FakePropertyRepository()
        return SearchViewModel(
            properties = properties,
            history = history,
            filters = filters,
            auth = auth,
            currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
            profiles = profiles,
            saveSearch = SaveSearch(savedSearches, properties, alertScheduler),
        )
    }

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

    @Test
    fun askModeShowsWhatTheSentenceMeansAndItsRegion() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.SetMode(SearchMode.Ask))
                assertEquals(null, awaitUntil { it.mode == SearchMode.Ask }.ask.intent)
                vm.onEvent(SearchUiEvent.QueryChanged("2 bedroom in La Marsa under 2000 TND, pets ok"))
                val ask = awaitUntil { it.ask.intent?.isEmpty == false }.ask
                assertEquals(
                    listOf(
                        QueryMeaning.Bedrooms(2),
                        QueryMeaning.Place("La Marsa", "tunis"),
                        QueryMeaning.Budget(null, 2_000, Currency.TND),
                        QueryMeaning.PetFriendly,
                    ),
                    ask.intent?.meanings,
                )
                assertTrue(ask.switchesRegion)
                assertEquals("Tunis", ask.regionName)
                assertFalse(ask.currencyMismatch)
                assertEquals(SortOrder.Recommended, ask.appliedSort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun removingAChipUpdatesTheInterpretationUntilTheTextChanges() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.Dictated("furnished villa with pool"))
                val first =
                    awaitUntil {
                        it.ask.intent
                            ?.meanings
                            ?.size == 3
                    }
                assertEquals(SearchMode.Ask, first.mode)
                vm.onEvent(SearchUiEvent.RemoveMeaning(QueryMeaning.Furnished))
                val removed =
                    awaitUntil {
                        it.ask.intent
                            ?.meanings
                            ?.size == 2
                    }.ask.intent!!
                assertFalse(removed.filter.furnishedOnly)
                vm.onEvent(SearchUiEvent.QueryChanged("furnished villa with pool and gym"))
                assertTrue(
                    awaitUntil {
                        it.ask.intent
                            ?.meanings
                            ?.size == 4
                    }.ask.intent!!.filter.furnishedOnly,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun applyingReplacesTheHomeFilterSwitchesRegionAndUsesBestMatchWithAProfile() =
        runTest(main.dispatcher) {
            profiles.save(AuthRepository.GUEST_USER_ID, PreferenceProfile(minBedrooms = 1))
            filters.update { it.copy(query = "Kaloor", verifiedOnly = true) }
            val vm = viewModel()
            vm.onEvent(SearchUiEvent.SetMode(SearchMode.Ask))
            vm.onEvent(SearchUiEvent.QueryChanged("T3 meublé à La Marsa moins de 2 000 DT"))
            vm.effects.test {
                vm.onEvent(SearchUiEvent.ApplyAsk)
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            assertEquals(
                PropertyFilter(
                    type = PropertyType.Apartment,
                    maxRent = 2_000,
                    minBedrooms = 2,
                    query = "La Marsa",
                    furnishedOnly = true,
                    sort = SortOrder.BestMatch,
                ),
                filters.filter.value,
            )
            assertEquals("tunis", settings.settings.value.regionId)
            assertEquals(
                listOf("T3 meublé à La Marsa moins de 2 000 DT"),
                history.observeRecent(AuthRepository.GUEST_USER_ID).first().map { it.query },
            )
        }

    @Test
    fun applyingKeepsAnExplicitSortAndTheRegionWhenNoneIsNamed() =
        runTest(main.dispatcher) {
            profiles.save(AuthRepository.GUEST_USER_ID, PreferenceProfile())
            val vm = viewModel()
            vm.onEvent(SearchUiEvent.QueryChanged("cheapest house with parking"))
            vm.effects.test {
                vm.onEvent(SearchUiEvent.ApplyAsk)
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            assertEquals(SortOrder.PriceLowToHigh, filters.filter.value.sort)
            assertTrue(filters.filter.value.parkingOnly)
            assertEquals("kerala", settings.settings.value.regionId)
        }

    @Test
    fun savingAnAskSearchKeepsItForTheUserWithAlertsAndAppliesIt() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(SearchUiEvent.QueryChanged("house with parking"))
            vm.effects.test {
                vm.onEvent(SearchUiEvent.SaveAsk("  Parking houses "))
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            val saved = savedSearches.observeSavedSearches(AuthRepository.GUEST_USER_ID).first().single()
            assertEquals("Parking houses", saved.name)
            assertEquals("house with parking", saved.query)
            assertEquals("kerala", saved.regionId)
            assertTrue(saved.alertsEnabled)
            assertEquals(filters.filter.value, saved.filter)
            assertTrue(saved.filter.parkingOnly)
            // Homes that already match are remembered, so only later ones alert.
            val matching =
                FakePropertyRepository()
                    .observeProperties("kerala")
                    .first()
                    .filter(saved.filter::accepts)
                    .map { it.id }
            assertEquals(matching.toSet(), savedSearches.seenIds(saved.id))
            assertEquals(1, alertScheduler.scheduled)
        }

    @Test
    fun applyingASentenceWithNothingUnderstoodSearchesTheWords() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.onEvent(SearchUiEvent.Dictated("something lovely"))
            vm.effects.test {
                vm.onEvent(SearchUiEvent.ApplyAsk)
                assertEquals(SearchUiEffect.Applied, awaitItem())
            }
            assertEquals("something lovely", filters.filter.value.query)
        }

    @Test
    fun lifestyleWordsWithoutAProfileAskForTheQuiz() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.Dictated("quiet family flat"))
                val ask = awaitUntil { it.ask.intent?.isEmpty == false }.ask
                assertTrue(ask.lifestyleNeedsProfile)
                profiles.save(AuthRepository.GUEST_USER_ID, PreferenceProfile())
                val withProfile = awaitUntil { it.ask.hasProfile }.ask
                assertFalse(withProfile.lifestyleNeedsProfile)
                assertEquals(SortOrder.BestMatch, withProfile.appliedSort)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun aBudgetInAnotherRegionsCurrencyIsFlagged() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(SearchUiEvent.Dictated("flat in Fort Kochi under 500 euros"))
                val ask = awaitUntil { it.ask.intent?.isEmpty == false }.ask
                assertEquals("kerala", ask.regionId)
                assertTrue(ask.currencyMismatch)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
