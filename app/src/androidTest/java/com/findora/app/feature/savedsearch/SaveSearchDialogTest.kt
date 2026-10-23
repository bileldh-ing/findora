package com.findora.app.feature.savedsearch

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.AlertFrequency
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.SavedSearch
import com.findora.app.feature.saved.SavedScreen
import com.findora.app.feature.saved.SavedUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** The name dialog in a real window (an AlertDialog never reports idle under Robolectric, so it runs here). */
@RunWith(AndroidJUnit4::class)
class SaveSearchDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun renamingASearchSendsTheTrimmedNewName() {
        val events = mutableListOf<SavedSearchesUiEvent>()
        val search =
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
        compose.setContent {
            FindoraTheme {
                SavedScreen(
                    state = SavedUiState(isLoading = false),
                    onToggleFavorite = {},
                    onOpenProperty = {},
                    searches =
                        SavedSearchesUiState(
                            isLoading = false,
                            items = listOf(SavedSearchItem(search, "Tunis")),
                        ),
                    onSearchEvent = { events += it },
                )
            }
        }
        compose.onNodeWithText("Searches").performClick()
        compose.onNode(hasContentDescription("Rename search")).performClick()
        compose.onNodeWithTag("saveSearchName").assertTextEquals("Marsa flats")
        compose.onNodeWithTag("saveSearchName").performTextReplacement("  Beach flats ")
        compose.onNodeWithTag("saveSearchConfirm").performClick()
        assertEquals(listOf(SavedSearchesUiEvent.Rename(3, "Beach flats")), events)
        compose.onNodeWithTag("saveSearchDialog").assertDoesNotExist()
    }

    @Test
    fun saveStaysDisabledWhileTheNameIsBlank() {
        var saved: String? = null
        compose.setContent {
            FindoraTheme { SaveSearchDialog(initialName = "", onConfirm = { saved = it }, onDismiss = {}) }
        }
        compose.onNodeWithTag("saveSearchConfirm").assertIsNotEnabled()
        compose.onNodeWithTag("saveSearchName").performTextReplacement("   ")
        compose.onNodeWithTag("saveSearchConfirm").assertIsNotEnabled()
        assertNull(saved)
        compose.onNodeWithTag("saveSearchName").performTextReplacement("Kochi houses")
        compose.onNodeWithTag("saveSearchConfirm").assertIsEnabled().performClick()
        assertEquals("Kochi houses", saved)
    }
}
