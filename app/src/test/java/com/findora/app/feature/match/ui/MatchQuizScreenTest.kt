package com.findora.app.feature.match.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.feature.match.quiz.MatchQuizScreen
import com.findora.app.feature.match.quiz.MatchQuizUiEffect
import com.findora.app.feature.match.quiz.MatchQuizViewModel
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h2400dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class MatchQuizScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val profiles = FakeMatchProfileRepository()
    private val effects = mutableListOf<MatchQuizUiEffect>()

    /** The real screen driven by the real ViewModel over in-memory repositories. */
    private fun showQuiz(
        darkTheme: Boolean = false,
        direction: LayoutDirection = LayoutDirection.Ltr,
    ) {
        val vm =
            MatchQuizViewModel(
                auth = FakeAuthRepository(),
                profiles = profiles,
                properties = FakePropertyRepository(),
                currentRegion =
                    CurrentRegion(
                        FakeSettingsRepository(),
                        SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined),
                    ),
            )
        compose.setContent {
            val state by vm.state.collectAsStateWithLifecycle()
            LaunchedEffect(vm) { vm.effects.collect { effects += it } }
            FindoraTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    MatchQuizScreen(state = state, onEvent = vm::onEvent, reduceMotion = true)
                }
            }
        }
    }

    private fun next() = compose.onNodeWithTag("quizNext").performClick()

    @Test
    fun walkingThroughTheSixStepsSavesTheAnswers() {
        showQuiz()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithText("What is your monthly budget?").assertIsDisplayed()
        compose.onNodeWithTag("quizBudgetSlider").assertIsDisplayed()
        compose.onNodeWithText("Any budget").assertIsDisplayed()
        next()

        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithTag("quizBeds_2").performClick()
        compose.onNodeWithTag("quizBaths_1").performClick()
        next()

        compose.onNodeWithText("Which kinds of home suit you?").assertIsDisplayed()
        compose.onNodeWithTag("type_apartment").performClick()
        next()

        compose.onNodeWithTag("area_Kakkanad").performScrollTo().performClick()
        compose.onNodeWithTag("commute_Edappally").performScrollTo().performClick()
        next()

        compose.onNodeWithTag("lifestyle_remote_worker").performClick()
        compose.onNodeWithTag("lifestyle_nature").performClick()
        next()

        compose.onNodeWithText("Step 6 of 6").assertIsDisplayed()
        compose.onNodeWithText("See my matches").assertIsDisplayed()
        compose.onNodeWithTag("mustHave_parking").performClick()
        next()
        compose.waitUntil { effects.isNotEmpty() }

        assertEquals(listOf(MatchQuizUiEffect.Finished), effects)
        val saved = profiles.profiles.value.getValue(AuthRepository.GUEST_USER_ID)
        assertEquals(2, saved.minBedrooms)
        assertEquals(1, saved.minBathrooms)
        assertEquals(setOf(PropertyType.Apartment), saved.propertyTypes)
        assertEquals(listOf("Kakkanad"), saved.neighbourhoods.map { it.name })
        assertEquals("Edappally", saved.commute?.name)
        assertEquals(setOf(LifestyleTag.RemoteWorker, LifestyleTag.Nature), saved.lifestyle)
        assertEquals(setOf(MustHave.Parking), saved.mustHaves)
        assertEquals(null, saved.budget)
    }

    @Test
    fun backAndSkipMoveBetweenStepsAndBackOnTheFirstStepCloses() {
        showQuiz()
        compose.onNodeWithTag("quizSkip").performClick()
        compose.onNodeWithText("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Step 1 of 6").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntil { effects.isNotEmpty() }
        assertEquals(listOf(MatchQuizUiEffect.Closed), effects)
    }

    @Test
    fun theProgressBarAnnouncesTheStepAndTheQuizWorksInDarkRtl() {
        showQuiz(darkTheme = true, direction = LayoutDirection.Rtl)
        compose.onNodeWithTag("quizProgress").assertIsDisplayed()
        compose.onNodeWithContentDescription("Step 1 of 6").assertIsDisplayed()
        next()
        compose.onNodeWithContentDescription("Step 2 of 6").assertIsDisplayed()
        compose.onNodeWithTag("quizStep_Rooms").assertIsDisplayed()
    }
}
