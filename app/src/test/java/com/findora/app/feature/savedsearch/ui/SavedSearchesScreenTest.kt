package com.findora.app.feature.savedsearch.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.feature.saved.SavedScreen
import com.findora.app.feature.saved.SavedUiState
import com.findora.app.feature.savedsearch.SaveSearchDialog
import com.findora.app.feature.savedsearch.SavedSearchItem
import com.findora.app.feature.savedsearch.SavedSearchesUiEvent
import com.findora.app.feature.savedsearch.SavedSearchesUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class SavedSearchesScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SavedSearchesUiEvent>()
    private val search =
        SavedSearch(
            id = 3,
            userId = "u",
            name = "Marsa flats",
            query = "T2 La Marsa",
            regionId = "tunis",
            filter = PropertyFilter(type = PropertyType.Apartment),
            alertsEnabled = true,
            frequency = AlertFrequency.Daily,
            createdAt = Instant.EPOCH,
            lastCheckedAt = null,
        )

    private fun show(items: List<SavedSearchItem>) {
        compose.setContent {
            FindoraTheme {
                SavedScreen(
                    state = SavedUiState(isLoading = false),
                    onToggleFavorite = {},
                    onOpenProperty = {},
                    searches = SavedSearchesUiState(isLoading = false, items = items),
                    onSearchEvent = { events += it },
                )
            }
        }
        compose.onNodeWithText("Searches").performClick()
    }

    @Test
    fun theSearchesTabListsSearchesWithTheirActions() {
        show(listOf(SavedSearchItem(search, "Tunis")))
        compose.onNodeWithText("Marsa flats").assertExists()
        compose.onNodeWithText("T2 La Marsa · Tunis").assertExists()
        compose.onNodeWithTag("savedSearchRun-3").performClick()
        compose.onNodeWithText("On every refresh").performClick()
        compose.onNodeWithTag("savedSearchAlerts-3").performClick()
        compose.onNode(hasContentDescription("Delete search")).performClick()
        assertEquals(
            listOf(
                SavedSearchesUiEvent.Run(3),
                SavedSearchesUiEvent.SetFrequency(3, AlertFrequency.Instant),
                SavedSearchesUiEvent.SetAlerts(3, false),
                SavedSearchesUiEvent.Delete(3),
            ),
            events,
        )
    }

    @Test
    fun renamingUsesTheNameDialog() {
        show(listOf(SavedSearchItem(search, "Tunis")))
        compose.onNode(hasContentDescription("Rename search")).performClick()
        compose.onNodeWithTag("saveSearchName").performTextReplacement("Beach flats")
        compose.onNodeWithTag("saveSearchConfirm").performClick()
        assertEquals(listOf(SavedSearchesUiEvent.Rename(3, "Beach flats")), events)
    }

    @Test
    fun noSearchesShowsHowToSaveOne() {
        show(emptyList())
        compose.onNodeWithTag("savedSearchesEmpty").assertExists()
    }

    @Test
    fun aBlankNameCannotBeSaved() {
        var saved: String? = null
        compose.setContent {
            FindoraTheme { SaveSearchDialog(initialName = "", onConfirm = { saved = it }, onDismiss = {}) }
        }
        compose.onNodeWithTag("saveSearchConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("saveSearchName").performTextReplacement("  Kochi houses ")
        compose.onNodeWithTag("saveSearchConfirm").performClick()
        assertEquals("Kochi houses", saved)
    }
}
