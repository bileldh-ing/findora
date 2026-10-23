package com.findora.app.feature.match.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.MatchFactor
import com.findora.app.core.domain.match.MatchResult
import com.findora.app.core.domain.match.MatchScoreCalculator
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.testing.seedProperties
import com.findora.app.core.ui.match.MatchSummaryCard
import com.findora.app.core.ui.match.WhyThisMatchContent
import com.findora.app.feature.detail.DetailScreen
import com.findora.app.feature.detail.DetailUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import java.time.Instant
import kotlin.math.roundToInt

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class WhyThisMatchTest {
    @get:Rule
    val compose = createComposeRule()

    private val georgie = seedProperties.first { it.title == "Georgie House" }
    private val now = Instant.parse("2026-10-01T10:00:00Z")

    private fun result(profile: PreferenceProfile): MatchResult =
        MatchScoreCalculator.score(georgie, profile, now = now)

    private val fitting = PreferenceProfile(budget = BudgetRange(null, georgie.rent.amount * 2, Currency.INR))

    @Test
    fun theSheetShowsABarAndPointsForEveryFactor() {
        val match = result(fitting)
        compose.setContent { FindoraTheme { WhyThisMatchContent(result = match, reduceMotion = true) } }
        compose.onNodeWithText("Why this match?").assertIsDisplayed()
        compose.onNodeWithContentDescription("${match.score}% match").assertIsDisplayed()
        MatchFactor.entries.forEach { factor ->
            compose.onNodeWithTag("whyThisMatch").assertExists()
            compose.onNodeWithTag("factor_${factor.name}").assertExists()
        }
        val budget = match.reasons.first()
        compose
            .onNodeWithContentDescription(
                "Budget. Within your budget. Points: ${budget.points.roundToInt()}/30",
            ).assertExists()
    }

    @Test
    fun theCardShowsTheTopThreeReasonsAndOpensTheBreakdown() {
        var opened = 0
        val match = result(fitting)
        compose.setContent {
            FindoraTheme(
                darkTheme = true,
            ) { MatchSummaryCard(result = match, onWhy = { opened++ }, reduceMotion = true) }
        }
        compose.onNodeWithText("Your match").assertIsDisplayed()
        compose.onAllNodesWithTag("reasonChip").assertCountEquals(3)
        compose.onNodeWithText("Within your budget").assertIsDisplayed()
        compose.onNodeWithTag("matchWhy").performClick()
        assertEquals(1, opened)
    }

    @Test
    fun aMissingMustHaveIsSpelledOutInsteadOfReasons() {
        val excluded = MatchResult(0, result(fitting).reasons, setOf(MustHave.Balcony))
        compose.setContent { FindoraTheme { MatchSummaryCard(result = excluded, onWhy = {}, reduceMotion = true) } }
        compose.onNodeWithTag("matchExcluded").assertIsDisplayed()
        compose.onAllNodesWithTag("reasonChip").assertCountEquals(0)
        compose.onNodeWithContentDescription("0% match").assertIsDisplayed()
    }

    @Test
    fun theDetailPageShowsTheRingAndOpensWhyThisMatch() {
        val match = result(fitting)
        compose.setContent {
            FindoraTheme {
                DetailScreen(
                    state =
                        DetailUiState.Content(
                            georgie,
                            isFavorite = false,
                            similar = emptyList(),
                            similarFavoriteIds = emptySet(),
                            match = match,
                        ),
                    onEvent = {},
                    onBack = {},
                    onOpenProperty = {},
                    onScheduleViewing = {},
                )
            }
        }
        compose.onNodeWithTag("detailList").performScrollToNode(hasTestTag("matchCard"))
        compose.onNodeWithContentDescription("${match.score}% match").assertIsDisplayed()
        compose.onNodeWithTag("matchWhy").performClick()
        compose.onNodeWithTag("whyThisMatch").assertExists()
        compose.onNodeWithTag("factor_LearnedBehavior").assertExists()
    }
}
