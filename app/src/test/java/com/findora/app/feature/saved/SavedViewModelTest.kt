package com.findora.app.feature.saved

import app.cash.turbine.test
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeInteractionRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SavedViewModelTest {
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
            val vm = SavedViewModel(favorites, auth, FakeInteractionRepository())
            assertTrue(SavedUiState().isLoading)
            vm.state.test {
                var state = awaitItem()
                while (state.isLoading) state = awaitItem()
                assertEquals(listOf(first), state.homes.map { it.id })
                auth.signIn()
                assertEquals(listOf(second), awaitItem().homes.map { it.id })
                vm.toggleFavorite(second)
                val empty = awaitItem()
                assertFalse(empty.isLoading)
                assertEquals(emptyList<Any>(), empty.homes)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
