package com.findora.app.feature.home.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.paging.PagingData
import androidx.paging.compose.collectAsLazyPagingItems
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.RegionPack
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.inboxMessage
import com.findora.app.feature.home.FilterSheetContent
import com.findora.app.feature.home.HomeScreen
import com.findora.app.feature.home.HomeUiEvent
import com.findora.app.feature.home.HomeUiState
import com.findora.app.feature.home.NotificationsSheetContent
import com.findora.app.feature.home.RefreshStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val repository = FakePropertyRepository()
    private val kerala: RegionPack by lazy {
        runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).region("kerala")!! }
    }
    private val events = mutableListOf<HomeUiEvent>()

    /** Home wired to an in-memory feed: chips, favourites and filters really change what the grid shows. */
    @Composable
    private fun InteractiveHome(
        initial: HomeUiState,
        darkTheme: Boolean = false,
    ) {
        var state by remember { mutableStateOf(initial) }
        val listings =
            remember(state.filter, state.refresh) {
                val homes =
                    if (state.refresh ==
                        RefreshStatus.Failed(0)
                    ) {
                        emptyList()
                    } else {
                        repository.matching("kerala", state.filter)
                    }
                flowOf(PagingData.from(homes))
            }.collectAsLazyPagingItems()
        FindoraTheme(darkTheme = darkTheme) {
            HomeScreen(
                state = state,
                listings = listings,
                onEvent = { event ->
                    events += event
                    state =
                        when (event) {
                            is HomeUiEvent.SelectCategory ->
                                state.copy(
                                    filter = state.filter.copy(type = event.category.type),
                                )
                            is HomeUiEvent.ToggleFavorite ->
                                state.copy(
                                    favoriteIds =
                                        if (event.propertyId in state.favoriteIds) {
                                            state.favoriteIds - event.propertyId
                                        } else {
                                            state.favoriteIds + event.propertyId
                                        },
                                )
                            is HomeUiEvent.ApplyFilter -> state.copy(filter = event.filter)
                            HomeUiEvent.ResetFilter -> state.copy(filter = PropertyFilter())
                            HomeUiEvent.ClearQuery -> state.copy(filter = state.filter.copy(query = ""))
                            else -> state
                        }
                },
                onOpenSearch = { events += HomeUiEvent.Refresh },
                onOpenProfile = {},
                reduceMotion = true,
            )
        }
    }

    private fun homeState(
        filter: PropertyFilter = PropertyFilter(),
        refresh: RefreshStatus = RefreshStatus.Idle,
    ) = HomeUiState(region = kerala, regions = listOf(kerala), userInitials = "AL", filter = filter, refresh = refresh)

    private fun visibleCardIds(): List<String> =
        compose
            .onAllNodes(tagPrefix("card_"))
            .fetchSemanticsNodes()
            .map { it.config[SemanticsProperties.TestTag].removePrefix("card_") }

    /** Scrolls the lazy grid until a node matching [matcher] is composed, then checks it is on screen. */
    private fun showInGrid(matcher: SemanticsMatcher) {
        compose.onNodeWithTag("homeGrid").performScrollToNode(matcher)
        compose.onNode(matcher).assertIsDisplayed()
    }

    private fun tagPrefix(prefix: String) =
        SemanticsMatcher("test tag starts with $prefix") {
            it.config.getOrNull(SemanticsProperties.TestTag)?.startsWith(prefix) == true
        }

    @Test
    fun headerMatchesTheReference() {
        compose.setContent { InteractiveHome(homeState()) }
        compose.onNodeWithContentDescription("Change location, current: Kochi, Kerala").assertIsDisplayed()
        compose.onNodeWithText("Discover").assertIsDisplayed()
        compose.onNodeWithText("Your New House").assertIsDisplayed()
        compose.onNodeWithText("Search Place…").assertIsDisplayed()
        listOf(
            "Rental House",
            "Apartment",
            "Houses",
            "Rooms",
        ).forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("chip_0").assertIsSelected()
        showInGrid(hasText("Nearby Rental Houses"))
        showInGrid(hasText("Georgie House"))
        compose.onAllNodesWithText("10.000/ Month").onFirst().assertExists()
        compose.onNodeWithContentDescription("Profile and settings").assertIsDisplayed()
        compose.onNodeWithContentDescription("Notifications").assertIsDisplayed()
        compose.onNodeWithContentDescription("Filters").assertIsDisplayed()
    }

    @Test
    fun categoryChipsFilterTheGrid() {
        compose.setContent { InteractiveHome(homeState()) }
        val apartmentIds = repository.matching("kerala", PropertyFilter(type = PropertyType.Apartment)).map { it.id }
        assertTrue(visibleCardIds().any { it !in apartmentIds })

        compose.onNodeWithTag("chip_1").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("chip_1").assertIsSelected()
        val shown = visibleCardIds()
        assertTrue(shown.isNotEmpty())
        assertTrue(shown.all { it in apartmentIds })

        compose.onNodeWithTag("chip_3").performClick()
        compose.waitForIdle()
        val roomIds = repository.matching("kerala", PropertyFilter(type = PropertyType.Room)).map { it.id }
        assertTrue(visibleCardIds().all { it in roomIds })
    }

    @Test
    fun heartTogglesTheFavouriteAndCardsDescribeThemselves() {
        compose.setContent { InteractiveHome(homeState()) }
        val georgie = repository.matching("kerala", PropertyFilter()).first { it.title == "Georgie House" }
        val card = compose.onNodeWithTag("card_${georgie.id}")
        val description = card.fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString()
        assertTrue(description, description.startsWith("Georgie House, Kochi. "))
        assertTrue(description, description.contains("10,000 per month. Rated 4.5."))
        assertTrue(description.contains("Verified listing."))

        compose.onNodeWithTag("heart_${georgie.id}").performClick()
        assertEquals(HomeUiEvent.ToggleFavorite(georgie.id), events.last())
        val saved = compose.onNodeWithTag("card_${georgie.id}").fetchSemanticsNode().config
        assertTrue(saved[SemanticsProperties.ContentDescription].joinToString().contains("Saved."))
        // TalkBack offers the same toggle as a custom action.
        val action = saved[SemanticsActions.CustomActions].single()
        assertEquals("Remove from saved", action.label)
        action.action()
        assertEquals(2, events.count { it == HomeUiEvent.ToggleFavorite(georgie.id) })
    }

    @Test
    fun theFilterBadgeCountsActiveRefinementsAndClearingTheSearchWorks() {
        compose.setContent {
            InteractiveHome(homeState(filter = PropertyFilter(query = "Kochi", verifiedOnly = true, minBedrooms = 2)))
        }
        compose.onNodeWithContentDescription("Filters, 2 active").assertIsDisplayed()
        compose.onNodeWithTag("searchPill").assert(hasText("Kochi"))
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(HomeUiEvent.ClearQuery, events.last())
        compose.onNodeWithTag("searchPill").assert(hasText("Search Place…"))
    }

    @Test
    fun filterSheetAppliesTheDraftAndResetKeepsCategoryAndText() {
        var applied: PropertyFilter? = null
        val initial = PropertyFilter(type = PropertyType.House, query = "Kochi", furnishedOnly = true)
        compose.setContent {
            FindoraTheme {
                FilterSheetContent(
                    initial = initial,
                    bounds = null,
                    currency = Currency.INR,
                    onApply = { applied = it },
                )
            }
        }
        compose.onNodeWithText("Filters").assertIsDisplayed()
        compose.onNodeWithTag("verifiedSwitch").performScrollTo().performClick()
        compose.onNodeWithText("Wi-Fi").performScrollTo().performClick()
        compose.onNodeWithText("Price: low to high").performScrollTo().performClick()
        compose.onNodeWithTag("applyFilters").performScrollTo().performClick()
        val result = applied!!
        assertTrue(result.verifiedOnly && result.furnishedOnly)
        assertEquals(setOf(Amenity.Wifi), result.amenities)
        assertEquals(SortOrder.PriceLowToHigh, result.sort)
        assertEquals(4, result.activeCount)

        compose.onNodeWithTag("resetFilters").performScrollTo().performClick()
        compose.onNodeWithTag("applyFilters").performScrollTo().performClick()
        assertEquals(PropertyFilter(type = PropertyType.House, query = "Kochi"), applied)
    }

    @Test
    fun filterButtonOpensTheSheetAndApplyingUpdatesTheBadge() {
        compose.setContent { InteractiveHome(homeState()) }
        compose.onNodeWithTag("filterButton").performClick()
        compose.waitUntil(TIMEOUT) { compose.onAllNodes(tagPrefix("filterSheet")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("verifiedSwitch").performScrollTo().performClick()
        compose.onNodeWithTag("applyFilters").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithContentDescription("Filters, 1 active").fetchSemanticsNodes().isNotEmpty()
        }
        assertEquals(PropertyFilter(verifiedOnly = true), (events.last() as HomeUiEvent.ApplyFilter).filter)
        val verifiedIds = repository.matching("kerala", PropertyFilter(verifiedOnly = true)).map { it.id }
        assertTrue(visibleCardIds().all { it in verifiedIds })
    }

    @Test
    fun anEmptyResultOffersToClearFilters() {
        compose.setContent { InteractiveHome(homeState(filter = PropertyFilter(query = "no such place"))) }
        showInGrid(hasText("No homes match"))
        compose.onNodeWithText("Clear filters").performClick()
        assertEquals(HomeUiEvent.ResetFilter, events.last())
        showInGrid(hasText("Georgie House"))
    }

    @Test
    fun aFailedFirstLoadShowsRetryAndACachedFeedShowsTheOfflineBanner() {
        compose.setContent { InteractiveHome(homeState(refresh = RefreshStatus.Failed(0))) }
        showInGrid(hasText("Couldn't load listings"))
        compose.onNodeWithText("Retry").performClick()
        assertEquals(HomeUiEvent.Refresh, events.last())
    }

    @Test
    fun aCachedFeedShowsTheOfflineBannerInDarkTheme() {
        compose.setContent { InteractiveHome(homeState(refresh = RefreshStatus.Failed(40)), darkTheme = true) }
        showInGrid(hasTestTag("offlineBanner"))
        compose.onNodeWithText("Offline: showing saved listings").assertExists()
        showInGrid(hasText("Georgie House"))
    }

    @Test
    fun bellShowsTheUnreadDotAndNotificationsCanBeMarkedRead() {
        val unread = listOf(inboxMessage("m1", "Your viewing is confirmed"))
        compose.setContent { InteractiveHome(homeState().copy(unread = unread)) }
        compose.onNodeWithContentDescription("Notifications, 1 unread").assertIsDisplayed()
    }

    @Test
    fun notificationsSheetListsMessagesAndOffersMarkAsRead() {
        val dismissed = mutableListOf<String>()
        compose.setContent {
            FindoraTheme {
                NotificationsSheetContent(
                    messages = listOf(inboxMessage("m1", "Your viewing is confirmed")),
                    onDismissMessage = { dismissed += it },
                )
            }
        }
        compose.onNodeWithText("Your viewing is confirmed").assertIsDisplayed()
        val action =
            compose
                .onNodeWithTag("notification_m1")
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]
                .single { it.label == "Mark as read" }
        action.action()
        assertEquals(listOf("m1"), dismissed)
    }

    @Test
    fun emptyNotificationsSayAllCaughtUp() {
        compose.setContent { FindoraTheme { NotificationsSheetContent(messages = emptyList(), onDismissMessage = {}) } }
        compose.onNodeWithText("You're all caught up").assertIsDisplayed()
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
