package com.findora.app.feature.saved

import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.locations.LocationsUiEffect
import com.findora.app.feature.locations.LocationsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedAndLocationsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @Test
    fun savedListsTheCurrentUsersFavouritesAndRemovesThem() =
        runTest(main.dispatcher) {
            val favorites = FakeFavoritesRepository()
            val auth = FakeAuthRepository()
            val first = seedProperties[0].id
            val second = seedProperties[1].id
            favorites.toggle(AuthRepository.GUEST_USER_ID, first)
            favorites.toggle(auth.account.id, second)
            val vm = SavedViewModel(favorites, auth)
            vm.state.test {
                assertTrue(awaitItem().isLoading)
                assertEquals(listOf(first), awaitItem().homes.map { it.id })
                auth.signIn()
                assertEquals(listOf(second), awaitItem().homes.map { it.id })
                vm.toggleFavorite(second)
                val empty = awaitItem()
                assertFalse(empty.isLoading)
                assertEquals(emptyList<Any>(), empty.homes)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun locationsSwitchesRegionOrSearchesANeighbourhoodThenShowsHome() =
        runTest(main.dispatcher) {
            val settings = FakeSettingsRepository()
            val filters = InMemoryHomeFilterRepository()
            val vm =
                LocationsViewModel(
                    CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
                    filters,
                )
            vm.state.test {
                var state = awaitItem()
                while (state.regions.isEmpty() || state.currentRegionId == null) state = awaitItem()
                assertEquals("kerala", state.currentRegionId)
                assertEquals(4, state.regions.size)
                cancelAndIgnoreRemainingEvents()
            }
            vm.effects.test {
                vm.selectLocality("tunis", "Carthage")
                assertEquals(LocationsUiEffect.ShowHome, awaitItem())
                assertEquals("tunis", settings.settings.value.regionId)
                assertEquals("Carthage", filters.filter.value.query)

                vm.selectRegion("paris")
                assertEquals(LocationsUiEffect.ShowHome, awaitItem())
                assertEquals("paris", settings.settings.value.regionId)
                assertEquals("", filters.filter.value.query)
            }
        }
}
