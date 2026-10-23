package com.findora.app.feature.search.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.search.NaturalLanguageQueryParser
import com.findora.app.core.domain.search.QueryMeaning
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.feature.search.AskUiState
import com.findora.app.feature.search.SearchMode
import com.findora.app.feature.search.SearchScreen
import com.findora.app.feature.search.SearchUiEvent
import com.findora.app.feature.search.SearchUiState
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class AskFindoraScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<SearchUiEvent>()
    private val regions by lazy {
        runBlocking {
            SeedRegionRepository(
                fileSeedReader(),
                Dispatchers.Unconfined,
            ).regions()
        }
    }

    private fun askState(query: String): SearchUiState {
        val intent = if (query.isBlank()) null else NaturalLanguageQueryParser.parse(query, regions)
        return SearchUiState(
            query = query,
            mode = SearchMode.Ask,
            ask =
                AskUiState(
                    intent = intent,
                    regionId = "tunis",
                    regionName = "Tunis",
                    regionCurrency = Currency.TND,
                    switchesRegion = true,
                ),
        )
    }

    private fun show(
        state: SearchUiState,
        voiceAvailable: Boolean = false,
    ) {
        compose.setContent {
            FindoraTheme {
                SearchScreen(
                    state = state,
                    onEvent = { events += it },
                    onBack = {},
                    autoFocus = false,
                    voiceAvailable = voiceAvailable,
                    onVoice = { events += SearchUiEvent.Dictated("voice") },
                )
            }
        }
    }

    @Test
    fun emptyAskModeExplainsItRunsOnDeviceAndOffersExamples() {
        show(askState(""))
        compose.onNodeWithTag("askOnDevice").assertIsDisplayed()
        compose.onNodeWithText("Try asking").assertIsDisplayed()
        compose.onNodeWithText("pet friendly villa with pool in Palm Jumeirah").performClick()
        assertEquals(SearchUiEvent.QueryChanged("pet friendly villa with pool in Palm Jumeirah"), events.last())
    }

    @Test
    fun understoodPartsAppearAsChipsWithNotesAndApply() {
        show(askState("2 bedroom in La Marsa under 2000 TND, pets ok, spacious"))
        listOf("Bedrooms: 2+", "La Marsa", "Under 2,000 TND", "Pets OK").forEach {
            compose.onNodeWithContentDescription("Remove $it").assertIsDisplayed()
        }
        compose.onNodeWithText("Not understood: spacious").assertIsDisplayed()
        compose.onNodeWithText("Switches your region to Tunis.").assertIsDisplayed()
        compose.onNodeWithText("Order: Recommended").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Pets OK").performClick()
        assertEquals(SearchUiEvent.RemoveMeaning(QueryMeaning.PetFriendly), events.last())
        compose.onNodeWithTag("askApply").performScrollTo().performClick()
        assertEquals(SearchUiEvent.ApplyAsk, events.last())
    }

    @Test
    fun removingAChipTakesItOffTheScreen() {
        var state by mutableStateOf(askState("furnished villa with pool"))
        compose.setContent {
            FindoraTheme {
                SearchScreen(
                    state = state,
                    onEvent = { event ->
                        if (event is SearchUiEvent.RemoveMeaning) {
                            state = state.copy(ask = state.ask.copy(intent = state.ask.intent?.without(event.meaning)))
                        }
                    },
                    onBack = {},
                    autoFocus = false,
                )
            }
        }
        compose.onNodeWithContentDescription("Remove Furnished").performClick()
        compose.onNodeWithContentDescription("Remove Furnished").assertDoesNotExist()
        compose.onNodeWithContentDescription("Remove Houses").assertIsDisplayed()
        compose.onNodeWithContentDescription("Remove Swimming pool").assertIsDisplayed()
    }

    @Test
    fun aSentenceWithNothingUnderstoodOffersAPlainTextSearch() {
        show(askState("something lovely"))
        compose.onNodeWithTag("askNothing").assertIsDisplayed()
        compose.onNodeWithTag("askApply").assertDoesNotExist()
        compose.onNodeWithText("Search these words instead").performClick()
        assertEquals(SearchUiEvent.Submit("something lovely"), events.last())
    }

    @Test
    fun modeSwitchAndVoiceButtonOnlyWhenARecognizerExists() {
        show(SearchUiState())
        compose.onNodeWithContentDescription("Voice search").assertDoesNotExist()
        compose.onNodeWithText("Ask Findora").performClick()
        assertEquals(SearchUiEvent.SetMode(SearchMode.Ask), events.last())
    }

    @Test
    fun voiceButtonStartsVoiceInputWhenAvailable() {
        show(askState(""), voiceAvailable = true)
        compose.onNodeWithContentDescription("Voice search").performClick()
        assertTrue(events.last() is SearchUiEvent.Dictated)
    }
}
