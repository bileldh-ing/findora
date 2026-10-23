package com.findora.app.feature.savedsearch.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.feature.saved.SavedScreen
import com.findora.app.feature.saved.SavedUiState
import com.findora.app.feature.savedsearch.SaveSearchNameField
import com.findora.app.feature.savedsearch.SavedSearchItem
import com.findora.app.feature.savedsearch.SavedSearchesUiEvent
import com.findora.app.feature.savedsearch.SavedSearchesUiState
import com.findora.app.feature.savedsearch.SearchNameState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun renamingOpensTheNameDialogWithTheCurrentName() {
        show(listOf(SavedSearchItem(search, "Tunis")))
        compose.onNode(hasContentDescription("Rename search")).performClick()
        compose.onNodeWithTag("saveSearchName").assertTextEquals("Marsa flats")
        compose.onNodeWithTag("saveSearchConfirm").assertIsEnabled().performClick()
        assertEquals(listOf(SavedSearchesUiEvent.Rename(3, "Marsa flats")), events)
        compose.onNodeWithTag("saveSearchDialog").assertDoesNotExist()
    }

    // The name field is tested outside the AlertDialog: a focused field's blinking cursor keeps a Robolectric
    // dialog window from ever going idle, so typing is exercised on the field itself.
    @Test
    fun anEditedNameIsTrimmedAndSavedWithDone() {
        val state = SearchNameState("Marsa flats")
        var saved: String? = null
        compose.setContent { FindoraTheme { SaveSearchNameField(state = state, onDone = { saved = it }) } }
        compose.onNodeWithTag("saveSearchName").performTextReplacement("  Beach flats ")
        compose.onNodeWithTag("saveSearchName").performImeAction()
        assertEquals("Beach flats", saved)
        assertEquals("Beach flats", state.nameToSave)
    }

    @Test
    fun noSearchesShowsHowToSaveOne() {
        show(emptyList())
        compose.onNodeWithTag("savedSearchesEmpty").assertExists()
    }

    @Test
    fun aBlankNameCannotBeSaved() {
        val state = SearchNameState("")
        val saved = mutableListOf<String>()
        compose.setContent { FindoraTheme { SaveSearchNameField(state = state, onDone = { saved += it }) } }
        compose.onNodeWithTag("saveSearchName").performImeAction()
        compose.onNodeWithTag("saveSearchName").performTextReplacement("   ")
        compose.onNodeWithTag("saveSearchName").performImeAction()
        assertNull(state.nameToSave)
        assertEquals(emptyList<String>(), saved)
        compose.onNodeWithTag("saveSearchName").performTextReplacement("  Kochi houses ")
        compose.onNodeWithTag("saveSearchName").performImeAction()
        assertEquals(listOf("Kochi houses"), saved)
    }
}
