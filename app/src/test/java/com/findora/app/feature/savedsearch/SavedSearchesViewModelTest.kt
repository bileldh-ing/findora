package com.findora.app.feature.savedsearch

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.alert.SaveSearch
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.repository.NewSavedSearch
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSavedSearchRepository
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

class SavedSearchesViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val savedSearches = FakeSavedSearchRepository()
    private val filters = InMemoryHomeFilterRepository()
    private val settings = FakeSettingsRepository()
    private val scheduler = RecordingAlertScheduler()
    private val tunisFlats = PropertyFilter(type = PropertyType.Apartment, maxRent = 2_000, query = "La Marsa")
    private val guest = AuthRepository.GUEST_USER_ID

    private fun currentRegion() = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher))

    private fun viewModel() = SavedSearchesViewModel(savedSearches, filters, currentRegion(), scheduler, auth)

    private suspend fun ReceiveTurbine<SavedSearchesUiState>.awaitLoaded(): SavedSearchesUiState {
        while (true) {
            val item = awaitItem()
            if (!item.isLoading) return item
        }
    }

    private suspend fun save(
        user: String = guest,
        name: String = "Marsa flats",
    ) = savedSearches.create(user, NewSavedSearch(name, "T2 La Marsa", "tunis", tunisFlats, emptySet()))

    @Test
    fun listsOnlyTheCurrentUsersSearchesWithTheirRegion() =
        runTest(main.dispatcher) {
            save()
            save(user = "someone-else", name = "Theirs")
            viewModel().state.test {
                val state = awaitLoaded()
                assertEquals(listOf("Marsa flats"), state.items.map { it.search.name })
                assertEquals("Tunis", state.items.single().regionName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun runningASearchAppliesItsRegionAndFilterAndShowsHome() =
        runTest(main.dispatcher) {
            val id = save()
            filters.update { it.copy(query = "something else") }
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(SavedSearchesUiEvent.Run(id))
                assertEquals(SavedSearchesUiEffect.ShowResults, awaitItem())
            }
            assertEquals(tunisFlats, filters.filter.value)
            assertEquals("tunis", settings.settings.value.regionId)
        }

    @Test
    fun renameAlertsFrequencyAndDeleteAreSaved() =
        runTest(main.dispatcher) {
            val id = save()
            val vm = viewModel()
            vm.state.test {
                awaitLoaded()
                vm.onEvent(SavedSearchesUiEvent.Rename(id, "Beach flats"))
                assertEquals(
                    "Beach flats",
                    awaitLoaded()
                        .items
                        .single()
                        .search.name,
                )
                vm.onEvent(SavedSearchesUiEvent.SetFrequency(id, AlertFrequency.Instant))
                assertEquals(
                    AlertFrequency.Instant,
                    awaitLoaded()
                        .items
                        .single()
                        .search.frequency,
                )
                vm.onEvent(SavedSearchesUiEvent.SetAlerts(id, false))
                assertFalse(
                    awaitLoaded()
                        .items
                        .single()
                        .search.alertsEnabled,
                )
                assertEquals(0, scheduler.scheduled)
                vm.onEvent(SavedSearchesUiEvent.SetAlerts(id, true))
                assertTrue(
                    awaitLoaded()
                        .items
                        .single()
                        .search.alertsEnabled,
                )
                assertEquals(1, scheduler.scheduled)
                vm.onEvent(SavedSearchesUiEvent.Delete(id))
                assertTrue(awaitLoaded().items.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun saveSearchKeepsTheHomeFilterAndRegionForTheUser() =
        runTest(main.dispatcher) {
            auth.signIn()
            filters.update { PropertyFilter(type = PropertyType.House, minBedrooms = 2) }
            val properties = FakePropertyRepository()
            val vm =
                SaveSearchViewModel(SaveSearch(savedSearches, properties, scheduler), filters, currentRegion(), auth)
            vm.effects.test {
                vm.save("   ")
                vm.save("  Family houses ")
                assertEquals(SaveSearchEffect.Saved("Family houses"), awaitItem())
            }
            val saved = savedSearches.observeSavedSearches(auth.account.id).first().single()
            assertEquals("Family houses", saved.name)
            assertEquals("kerala", saved.regionId)
            assertEquals(PropertyFilter(type = PropertyType.House, minBedrooms = 2), saved.filter)
            assertTrue(savedSearches.observeSavedSearches(guest).first().isEmpty())
            assertEquals(1, scheduler.scheduled)
        }

    @Test
    fun aSearchLinkAppliesTheSearchForItsOwnerOnly() =
        runTest(main.dispatcher) {
            auth.signIn()
            val mine = save(user = auth.account.id)
            val theirs = save(user = "someone-else", name = "Theirs")

            fun link(id: Long) =
                SavedSearchLinkViewModel(
                    SavedStateHandle(mapOf(SavedSearchLinkViewModel.SEARCH_ID_KEY to id)),
                    savedSearches,
                    filters,
                    currentRegion(),
                    auth,
                )

            // The main dispatcher is unconfined, so the link resolves during construction.
            assertEquals(SavedSearchLinkState.Done(applied = false), link(theirs).state.value)
            assertEquals(PropertyFilter(), filters.filter.value)
            assertEquals(SavedSearchLinkState.Done(applied = true), link(mine).state.value)
            assertEquals(tunisFlats, filters.filter.value)
            assertEquals("tunis", settings.settings.value.regionId)
        }
}
