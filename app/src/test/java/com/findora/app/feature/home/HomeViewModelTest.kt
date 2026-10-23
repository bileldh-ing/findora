package com.findora.app.feature.home

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.findora.app.core.data.repository.InMemoryHomeFilterRepository
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.HomeCategory
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RefreshResult
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.domain.notification.NewNotification
import com.findora.app.core.domain.notification.NotificationKind
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeFavoritesRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakeNotificationRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.observeMatchContext
import com.findora.app.core.testing.seedProperties
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val properties = FakePropertyRepository()
    private val favorites = FakeFavoritesRepository()
    private val filters = InMemoryHomeFilterRepository()
    private val auth = FakeAuthRepository()
    private val settings = FakeSettingsRepository()
    private val notifications = FakeNotificationRepository()
    private val profiles = FakeMatchProfileRepository()

    private fun viewModel() =
        HomeViewModel(
            properties = properties,
            favorites = favorites,
            filters = filters,
            auth = auth,
            currentRegion = CurrentRegion(settings, SeedRegionRepository(fileSeedReader(), main.dispatcher)),
            notifications = notifications,
            observeMatchContext =
                observeMatchContext(
                    auth = auth,
                    profiles = profiles,
                    properties = properties,
                    settings = settings,
                    dispatcher = main.dispatcher,
                ),
        )

    private suspend fun ReceiveTurbine<HomeUiState>.awaitUntil(predicate: (HomeUiState) -> Boolean): HomeUiState {
        while (true) {
            val item = awaitItem()
            if (predicate(item)) return item
        }
    }

    @Test
    fun headerShowsTheRegionLikeTheReferenceAndTheSignedInInitials() =
        runTest(main.dispatcher) {
            auth.signIn()
            viewModel().state.test {
                val state = awaitUntil { it.region != null && it.regions.isNotEmpty() && it.userInitials != null }
                assertEquals("Kochi, Kerala", state.locationLabel)
                assertEquals("AL", state.userInitials)
                assertEquals(listOf("kerala", "tunis", "paris", "dubai"), state.regions.map { it.id })
                assertEquals(HomeCategory.RentalHouse, state.category)
                assertFalse(state.isFiltered)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun theFeedRefreshesOnceOnStartAndForcesOnPullToRefresh() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            assertEquals(listOf("kerala" to false), properties.refreshCalls)
            vm.onEvent(HomeUiEvent.Refresh)
            assertEquals(listOf("kerala" to false, "kerala" to true), properties.refreshCalls)
        }

    @Test
    fun aFailedRefreshIsReportedWithTheCachedCount() =
        runTest(main.dispatcher) {
            properties.refreshResult = FakePropertyRepository.failed(cached = 0)
            val vm = viewModel()
            vm.state.test {
                assertEquals(RefreshStatus.Failed(0), awaitUntil { it.refresh is RefreshStatus.Failed }.refresh)
                properties.refreshResult = FakePropertyRepository.failed(cached = 12)
                vm.onEvent(HomeUiEvent.Refresh)
                assertEquals(RefreshStatus.Failed(12), awaitUntil { it.refresh == RefreshStatus.Failed(12) }.refresh)
                properties.refreshResult = RefreshResult.Fetched(40)
                vm.onEvent(HomeUiEvent.Refresh)
                assertEquals(RefreshStatus.Idle, awaitUntil { it.refresh == RefreshStatus.Idle }.refresh)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun selectingACategoryFiltersThePagedFeed() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.listings.first()
            assertEquals("kerala" to PropertyFilter(), properties.requestedFilters.last())
            vm.onEvent(HomeUiEvent.SelectCategory(HomeCategory.Apartment))
            vm.listings.first()
            assertEquals("kerala" to PropertyFilter(type = PropertyType.Apartment), properties.requestedFilters.last())
            vm.onEvent(HomeUiEvent.SelectCategory(HomeCategory.Rooms))
            vm.listings.first()
            assertEquals(PropertyFilter(type = PropertyType.Room), properties.requestedFilters.last().second)
            vm.state.test {
                assertEquals(HomeCategory.Rooms, awaitUntil { it.category == HomeCategory.Rooms }.category)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun filterEventsEditTheSharedFilter() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            filters.update { it.copy(query = "Kochi", type = PropertyType.House) }
            vm.onEvent(HomeUiEvent.ApplyFilter(filters.filter.value.copy(minBedrooms = 2, sort = SortOrder.Newest)))
            assertEquals(2, filters.filter.value.activeCount)
            vm.onEvent(HomeUiEvent.ClearRefinements)
            assertEquals(PropertyFilter(type = PropertyType.House, query = "Kochi"), filters.filter.value)
            vm.onEvent(HomeUiEvent.ClearQuery)
            assertEquals(PropertyFilter(type = PropertyType.House), filters.filter.value)
            vm.onEvent(HomeUiEvent.ResetFilter)
            assertEquals(PropertyFilter(), filters.filter.value)
        }

    @Test
    fun favouritesToggleForTheCurrentUserOnly() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                vm.onEvent(HomeUiEvent.ToggleFavorite("p1"))
                assertEquals(setOf("p1"), awaitUntil { it.favoriteIds.isNotEmpty() }.favoriteIds)
                auth.signIn()
                assertEquals(emptySet<String>(), awaitUntil { it.favoriteIds.isEmpty() }.favoriteIds)
                vm.onEvent(HomeUiEvent.ToggleFavorite("p2"))
                assertEquals(setOf("p2"), awaitUntil { it.favoriteIds == setOf("p2") }.favoriteIds)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun anotherUserStartsFromTheDefaultFilter() =
        runTest(main.dispatcher) {
            viewModel()
            filters.update { it.copy(query = "Kakkanad", verifiedOnly = true) }
            auth.signIn()
            assertEquals(PropertyFilter(), filters.filter.value)
        }

    @Test
    fun choosingARegionSwitchesTheHeaderAndRefreshesIt() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                awaitUntil { it.region?.id == "kerala" }
                vm.onEvent(HomeUiEvent.SelectRegion("tunis"))
                val state = awaitUntil { it.region?.id == "tunis" }
                assertEquals("La Marsa, Tunis", state.locationLabel)
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals("tunis" to false, properties.refreshCalls.last())
            assertEquals("tunis", settings.settings.value.regionId)
        }

    @Test
    fun theBellCountsTheUnreadNotificationsOfTheCurrentUserOnly() =
        runTest(main.dispatcher) {
            val guest = AuthRepository.GUEST_USER_ID
            notifications.add(guest, NewNotification(NotificationKind.ViewingBooked, "Georgie House", "p1"))
            notifications.add(guest, NewNotification(NotificationKind.NewMessage, "Anu", "p1", conversationId = "c1"))
            notifications.add("someone-else", NewNotification(NotificationKind.ViewingBooked, "Other", "p2"))
            val vm = viewModel()
            vm.state.test {
                assertEquals(2, awaitUntil { it.unreadNotifications == 2 }.unreadNotifications)
                notifications.markAllRead(guest)
                assertEquals(0, awaitUntil { it.unreadNotifications == 0 }.unreadNotifications)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun withoutAProfileHomeOffersTheQuizAndBestMatchPagesFromTheDatabase() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.state.test {
                val state = awaitUntil { it.hasMatchProfile != null }
                assertEquals(false, state.hasMatchProfile)
                assertTrue(state.matchScores.isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
            vm.onEvent(HomeUiEvent.ApplyFilter(PropertyFilter(sort = SortOrder.BestMatch)))
            vm.listings.first()
            assertEquals(
                SortOrder.BestMatch,
                properties.requestedFilters
                    .last()
                    .second.sort,
            )
        }

    @Test
    fun withAProfileCardsGetScoresAndBestMatchRanksByScoreWithoutMustHaveFailures() =
        runTest(main.dispatcher) {
            profiles.save(
                AuthRepository.GUEST_USER_ID,
                PreferenceProfile(
                    budget = BudgetRange(null, 30_000, Currency.INR),
                    mustHaves = setOf(MustHave.Parking),
                ),
            )
            val kerala = seedProperties.filter { it.regionId == "kerala" }
            val vm = viewModel()
            vm.state.test {
                val state = awaitUntil { it.hasMatchProfile == true && it.matchScores.isNotEmpty() }
                assertEquals(kerala.filter { it.parking }.map { it.id }.toSet(), state.matchScores.keys)
                assertTrue(state.matchScores.values.all { it in 0..100 })
                cancelAndIgnoreRemainingEvents()
            }
            vm.listings.first()
            val pagedRequests = properties.requestedFilters.size
            vm.onEvent(HomeUiEvent.ApplyFilter(PropertyFilter(sort = SortOrder.BestMatch)))
            vm.listings.first()
            // Ranked in memory by BestMatchRanking (covered in BestMatchRankingTest), not paged from the database.
            assertEquals(pagedRequests, properties.requestedFilters.size)
            vm.onEvent(HomeUiEvent.ApplyFilter(PropertyFilter(sort = SortOrder.Newest)))
            vm.listings.first()
            assertEquals(
                SortOrder.Newest,
                properties.requestedFilters
                    .last()
                    .second.sort,
            )
        }
}
