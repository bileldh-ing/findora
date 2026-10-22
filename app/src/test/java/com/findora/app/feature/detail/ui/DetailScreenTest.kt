package com.findora.app.feature.detail.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.detail.DetailError
import com.findora.app.feature.detail.DetailScreen
import com.findora.app.feature.detail.DetailUiEvent
import com.findora.app.feature.detail.DetailUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class DetailScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val similar = seedProperties.filter { it.type == georgie.type && it.id != georgie.id }.take(3)
    private val events = mutableListOf<DetailUiEvent>()
    private val opened = mutableListOf<String>()

    private fun showContent(darkTheme: Boolean = false) {
        var state by mutableStateOf<DetailUiState>(
            DetailUiState.Content(georgie, isFavorite = false, similar = similar, similarFavoriteIds = emptySet()),
        )
        compose.setContent {
            FindoraTheme(darkTheme = darkTheme) {
                DetailScreen(
                    state = state,
                    onEvent = { event ->
                        events += event
                        val content = state as DetailUiState.Content
                        if (event ==
                            DetailUiEvent.ToggleFavorite
                        ) {
                            state = content.copy(isFavorite = !content.isFavorite)
                        }
                    },
                    onBack = {},
                    onOpenProperty = { opened += it },
                )
            }
        }
    }

    @Test
    fun showsTitlePriceRatingAndSpecs() {
        showContent()
        compose.onNodeWithTag("detailTitle").assertIsDisplayed().assert(isHeading())
        compose.onNodeWithText("Georgie House").assertIsDisplayed()
        val price = PriceFormatter.format(georgie.rent, Locale.getDefault(), PriceStyle.Localized)
        compose.onNodeWithTag("detailPrice").assert(hasContentDescription("$price / month"))
        compose.onNodeWithTag("detailRating").assert(hasContentDescription("Rated 4.5 out of 5, 147 reviews"))
        compose.onNodeWithContentDescription("Bedrooms: ${georgie.bedrooms}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Bathrooms: ${georgie.bathrooms}").assertIsDisplayed()
        compose.onNodeWithContentDescription("Area: ${georgie.areaSqm} m²").assertIsDisplayed()
        compose.onNodeWithContentDescription("Floor: Ground").assertIsDisplayed()
        compose.onNodeWithTag("detailPageIndicator").assert(hasContentDescription("Photo 1 of ${georgie.images.size}"))
        compose.onNodeWithTag("detailVerified").assertIsDisplayed()
    }

    @Test
    fun favouriteShareAndBackButtonsAreLabelledAndWork() {
        showContent(darkTheme = true)
        compose.onNodeWithTag("detailFavorite").assert(hasContentDescription("Save")).performClick()
        assertEquals(DetailUiEvent.ToggleFavorite, events.last())
        compose.onNodeWithTag("detailFavorite").assert(hasContentDescription("Remove from saved"))
        compose.onNodeWithTag("detailShare").assert(hasContentDescription("Share")).performClick()
        assertEquals(DetailUiEvent.Share, events.last())
        val back = compose.onNodeWithTag("detailBack").fetchSemanticsNode()
        assertEquals(listOf("Back"), back.config[SemanticsProperties.ContentDescription])
    }

    @Test
    fun agentLocationAndSimilarHomesAreActionable() {
        showContent()
        val list = compose.onNodeWithTag("detailList")
        list.performScrollToNode(hasTestTag("detailAgent"))
        compose.onNodeWithText(georgie.agent.name).assertIsDisplayed()
        compose.onNodeWithContentDescription("Call ${georgie.agent.name}").performClick()
        compose.onNodeWithContentDescription("Email ${georgie.agent.name}").performClick()
        list.performScrollToNode(hasTestTag("detailLocation"))
        compose.onNodeWithText("Open in maps").performClick()
        assertEquals(
            listOf(DetailUiEvent.CallAgent, DetailUiEvent.EmailAgent, DetailUiEvent.OpenInMaps),
            events.takeLast(3),
        )
        list.performScrollToNode(hasTestTag("detailSimilar"))
        compose.onNodeWithText("Similar homes").assert(isHeading())
        compose.onNodeWithTag("heart_${similar.first().id}").performClick()
        assertEquals(DetailUiEvent.ToggleSimilarFavorite(similar.first().id), events.last())
    }

    @Test
    fun tappingAPhotoOpensTheGalleryAndCloseReturns() {
        showContent()
        compose
            .onNodeWithContentDescription("Photo 1 of ${georgie.images.size}. ${georgie.images[0].altText}")
            .performClick()
        compose.onNodeWithTag("gallery").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close gallery").performClick()
        compose.onNodeWithTag("gallery").assertDoesNotExist()
        assertTrue(events.isEmpty())
    }

    @Test
    fun aMissingListingOffersAWayBack() {
        var backs = 0
        compose.setContent {
            FindoraTheme {
                DetailScreen(
                    state = DetailUiState.Error(DetailError.NotFound),
                    onEvent = { events += it },
                    onBack = { backs++ },
                    onOpenProperty = {},
                )
            }
        }
        compose.onNodeWithText("Listing not available").assertIsDisplayed()
        compose.onNodeWithTag("detailNotFound").assertIsDisplayed()
        compose.onNodeWithText("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun anUnreadableListingOffersRetry() {
        compose.setContent {
            FindoraTheme {
                DetailScreen(
                    state = DetailUiState.Error(DetailError.Unavailable),
                    onEvent = { events += it },
                    onBack = {},
                    onOpenProperty = {},
                )
            }
        }
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf<DetailUiEvent>(DetailUiEvent.Retry), events)
    }
}
