package com.findora.app.feature.detail.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.insight.AffordabilityCalculator
import com.findora.app.core.domain.model.Money
import com.findora.app.core.testing.seedProperties
import com.findora.app.feature.detail.AffordabilityForm
import com.findora.app.feature.detail.AffordabilityUiState
import com.findora.app.feature.detail.DetailScreen
import com.findora.app.feature.detail.DetailUiEvent
import com.findora.app.feature.detail.DetailUiState
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
class DetailInsightsTest {
    @get:Rule
    val compose = createComposeRule()

    private val georgie = seedProperties.first { it.title == "Georgie House" }

    private fun price(amount: Long) = PriceFormatter.format(Money(amount, georgie.rent.currency), Locale.getDefault())

    /** The real screen with the real reducer, so opening the calculator works as in the app. */
    private fun showDetail(darkTheme: Boolean = false) {
        var state by mutableStateOf<DetailUiState>(
            DetailUiState.Content(georgie, isFavorite = false, similar = emptyList(), similarFavoriteIds = emptySet()),
        )
        compose.setContent {
            FindoraTheme(darkTheme = darkTheme) {
                DetailScreen(
                    state = state,
                    onEvent = { event ->
                        val content = state as DetailUiState.Content
                        if (event is DetailUiEvent.Affordability) {
                            state = content.copy(affordability = content.affordability.reduce(event.event))
                        }
                    },
                    onBack = {},
                    onOpenProperty = {},
                    onScheduleViewing = {},
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        compose.onNodeWithTag("detailList").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun priceHistoryShowsSparklineChangeAndSampleLabel() {
        showDetail()
        scrollTo("detailPriceHistory")
        compose.onNodeWithTag("priceSparkline").assertExists()
        compose.onNodeWithTag("priceChangeChip").assertIsDisplayed()
        compose.onNodeWithTag("priceHistorySample").assertIsDisplayed()
        val description =
            compose
                .onNodeWithTag("priceSparkline")
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()
        val history = georgie.priceHistory
        assertTrue(description, description.contains(price(history.maxOf { it.amount })))
        assertTrue(description, description.contains(price(history.minOf { it.amount })))
        assertTrue(description, description.contains("Sample data"))
    }

    @Test
    fun neighbourhoodRadarListsEveryScoreForTalkBack() {
        showDetail(darkTheme = true)
        scrollTo("detailNeighbourhood")
        compose.onNodeWithTag("neighbourhoodRadar").assertExists()
        compose.onNodeWithTag("neighbourhoodSample").assertIsDisplayed()
        val description =
            compose
                .onNodeWithTag("neighbourhoodRadar")
                .fetchSemanticsNode()
                .config[SemanticsProperties.ContentDescription]
                .single()
        listOf("Walkability", "Transport", "Safety", "Low noise", "Amenities").forEach { axis ->
            assertTrue(description, description.contains(axis))
        }
        assertTrue(description, description.contains(georgie.locality))
    }

    @Test
    fun affordabilityCardOpensTheCalculator() {
        showDetail()
        scrollTo("affordabilityCard")
        compose.onNodeWithText("Can I afford it?").assertIsDisplayed()
        compose.onNodeWithTag("affordabilityOpen").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("affordabilityIncome").assertIsDisplayed()
        compose.onNodeWithTag("affordabilityEmpty").assertIsDisplayed()
    }

    @Test
    fun calculatorUpdatesAsTheUserTypesAndSlides() {
        var state by mutableStateOf(AffordabilityUiState(georgie.rent))
        compose.setContent {
            FindoraTheme {
                AffordabilityForm(state = state, onEvent = { state = state.reduce(it) })
            }
        }
        compose.onNodeWithTag("affordabilityEmpty").assertIsDisplayed()
        val income = georgie.rent.amount * 6
        compose.onNodeWithTag("affordabilityIncome").performTextInput(income.toString())
        compose.onNodeWithTag("affordabilityVerdict").assertIsDisplayed()
        compose.onNodeWithText("Comfortable").assertIsDisplayed()
        compose.onNodeWithTag("affordabilityDonut").assertExists()
        compose
            .onNodeWithText(
                "The 30% rule suggests a rent of up to ${price(AffordabilityCalculator.maxRent(income))} a month.",
            ).assertIsDisplayed()
        compose.onNodeWithTag("affordabilityDeposit").performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        compose.onNodeWithText("Deposit: 4 months of rent").assertIsDisplayed()
        val report = requireNotNull(state.report)
        compose.onNodeWithTag("affordabilityUpfrontTotal").assertIsDisplayed()
        compose.onNodeWithText(price(report.upfrontTotal)).assertIsDisplayed()
        compose.onNodeWithText(price(georgie.rent.amount * 4)).assertIsDisplayed()
    }

    @Test
    fun anIncomeBelowTheRentIsOverBudget() {
        var state by mutableStateOf(AffordabilityUiState(georgie.rent))
        compose.setContent {
            FindoraTheme(darkTheme = true) {
                AffordabilityForm(state = state, onEvent = { state = state.reduce(it) })
            }
        }
        compose.onNodeWithTag("affordabilityIncome").performTextInput(georgie.rent.amount.toString())
        compose.onNodeWithText("Over budget").assertIsDisplayed()
    }
}
