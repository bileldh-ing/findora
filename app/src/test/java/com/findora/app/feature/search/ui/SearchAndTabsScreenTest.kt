package com.findora.app.feature.search.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.RecentSearch
import com.findora.app.core.domain.model.SearchSuggestion
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.locations.LocationsScreen
import com.findora.app.feature.locations.LocationsUiState
import com.findora.app.feature.saved.SavedScreen
import com.findora.app.feature.saved.SavedUiState
import com.findora.app.feature.search.SearchScreen
import com.findora.app.feature.search.SearchUiEvent
import com.findora.app.feature.search.SearchUiState
import com.findora.app.navigation.FloatingNavPill
import com.findora.app.navigation.MainTab
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class SearchAndTabsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SearchUiEvent>()
    private val recents = listOf(RecentSearch("Kakkanad", Instant.EPOCH), RecentSearch("Fort Kochi", Instant.EPOCH))

    private fun search(state: SearchUiState) {
        compose.setContent {
            FindoraTheme {
                SearchScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = { events += SearchUiEvent.ClearRecents },
                )
            }
        }
    }

    @Test
    fun emptyFieldShowsRecentSearchesThatCanBeReusedOrCleared() {
        search(SearchUiState(recents = recents))
        compose.onNodeWithText("Recent searches").assertIsDisplayed()
        compose.onNodeWithText("Kakkanad").performClick()
        assertEquals(SearchUiEvent.Submit("Kakkanad"), events.last())
        compose.onNodeWithText("Clear all").performClick()
        assertEquals(SearchUiEvent.ClearRecents, events.last())
    }

    @Test
    fun recentSearchesDeleteBySwipeOrAccessibilityAction() {
        search(SearchUiState(recents = recents))
        compose.onNodeWithTag("recent_Fort Kochi").performTouchInput { swipeLeft() }
        compose.waitForIdle()
        assertEquals(SearchUiEvent.DeleteRecent("Fort Kochi"), events.last())
        compose
            .onNodeWithTag("recent_Kakkanad")
            .fetchSemanticsNode()
            .config[SemanticsActions.CustomActions]
            .single { it.label == "Delete" }
            .action()
        assertEquals(SearchUiEvent.DeleteRecent("Kakkanad"), events.last())
    }

    @Test
    fun typingSendsTheTextAndTheKeyboardSearchSubmitsIt() {
        compose.setContent {
            var state by remember { mutableStateOf(SearchUiState()) }
            FindoraTheme {
                SearchScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        if (event is SearchUiEvent.QueryChanged) state = state.copy(query = event.query)
                    },
                    onBack = {},
                    autoFocus = false,
                )
            }
        }
        compose.onNodeWithTag("searchField").performTextInput("Vyttila")
        assertEquals(SearchUiEvent.QueryChanged("Vyttila"), events.last())
        compose.onNodeWithTag("searchField").performImeAction()
        assertEquals(SearchUiEvent.Submit("Vyttila"), events.last())
        compose.onNodeWithContentDescription("Clear search").performClick()
        assertEquals(SearchUiEvent.QueryChanged(""), events.last())
    }

    @Test
    fun suggestionsAreLabelledAndSubmitOnTap() {
        val suggestions =
            listOf(
                SearchSuggestion("Kakkanad", SearchSuggestion.Kind.Locality),
                SearchSuggestion("Luna Haven", SearchSuggestion.Kind.Property),
            )
        search(SearchUiState(query = "ka", suggestions = suggestions, suggestionsLoaded = true))
        compose.onNodeWithText("Suggestions").assertIsDisplayed()
        compose.onNodeWithText("Area").assertIsDisplayed()
        compose.onNodeWithText("Home").assertIsDisplayed()
        compose.onNodeWithText("Luna Haven").performClick()
        assertEquals(SearchUiEvent.Submit("Luna Haven"), events.last())
    }

    @Test
    fun noMatchesExplainsThatSearchStillWorks() {
        search(SearchUiState(query = "zzqx", suggestionsLoaded = true))
        compose.onNodeWithText("No matches. Press search to look for “zzqx” anyway.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        assertTrue(events.isNotEmpty())
    }

    @Test
    fun navPillSelectsTabsAndMarksTheActiveOne() {
        val selected = mutableListOf<MainTab>()
        compose.setContent {
            var current by remember { mutableStateOf(MainTab.Home) }
            FindoraTheme {
                FloatingNavPill(
                    selected = current,
                    onSelect = {
                        selected += it
                        current = it
                    },
                )
            }
        }
        listOf("Home", "Locations", "Saved", "Settings").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
        compose.onNodeWithTag("nav_home").assertIsSelected()
        compose.onNodeWithTag("nav_saved").performClick()
        compose.onNodeWithTag("nav_saved").assertIsSelected()
        compose.onNodeWithTag("nav_home").assertIsNotSelected()
        compose.onNodeWithTag("nav_settings").performClick()
        compose.onNodeWithTag("nav_locations").performClick()
        assertEquals(listOf(MainTab.Saved, MainTab.Settings, MainTab.Locations), selected)
    }

    @Test
    fun savedShowsAnEmptyStateThenTheSavedHomes() {
        var state by mutableStateOf(SavedUiState(isLoading = false, homes = emptyList()))
        val removed = mutableListOf<String>()
        val opened = mutableListOf<String>()
        compose.setContent {
            FindoraTheme(darkTheme = true) {
                SavedScreen(
                    state = state,
                    onToggleFavorite = { removed += it },
                    onOpenProperty = { opened += it },
                )
            }
        }
        compose.onNodeWithText("No saved homes yet").assertIsDisplayed()
        val home = seedProperties.first()
        state = SavedUiState(isLoading = false, homes = listOf(home))
        compose.onNodeWithText(home.title).assertIsDisplayed()
        compose.onNodeWithTag("heart_${home.id}").performClick()
        assertEquals(listOf(home.id), removed)
        compose.onNodeWithTag("card_${home.id}").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(listOf(home.id), opened)
    }

    @Test
    fun locationsPicksARegionOrANeighbourhood() {
        val regions = runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).regions() }
        val picks = mutableListOf<String>()
        compose.setContent {
            FindoraTheme {
                LocationsScreen(
                    state = LocationsUiState(regions = regions, currentRegionId = "kerala"),
                    onSelectRegion = { picks += it },
                    onSelectLocality = { region, locality -> picks += "$region/$locality" },
                )
            }
        }
        compose.onNodeWithText("Current").assertIsDisplayed()
        compose.onNodeWithTag("locality_Fort Kochi").performClick()
        compose.onNodeWithTag("selectRegion_tunis").performClick()
        assertEquals(listOf("kerala/Fort Kochi", "tunis"), picks)
    }
}
