package com.findora.app.feature.match

import app.cash.turbine.test
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.match.BudgetRange
import com.findora.app.core.domain.match.LifestyleTag
import com.findora.app.core.domain.match.LocationAnchor
import com.findora.app.core.domain.match.MustHave
import com.findora.app.core.domain.match.PreferenceProfile
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.repository.AuthRepository
import com.findora.app.core.domain.usecase.CurrentRegion
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeMatchProfileRepository
import com.findora.app.core.testing.FakePropertyRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.feature.match.quiz.MatchQuizUiEffect
import com.findora.app.feature.match.quiz.MatchQuizUiEvent
import com.findora.app.feature.match.quiz.MatchQuizViewModel
import com.findora.app.feature.match.quiz.QuizStep
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MatchQuizViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val profiles = FakeMatchProfileRepository()

    private fun viewModel() =
        MatchQuizViewModel(
            auth = auth,
            profiles = profiles,
            properties = FakePropertyRepository(),
            currentRegion =
                CurrentRegion(
                    FakeSettingsRepository(),
                    SeedRegionRepository(fileSeedReader(), main.dispatcher),
                ),
        )

    @Test
    fun startsOnTheBudgetStepWithTheRegionLoaded() =
        runTest(main.dispatcher) {
            val state = viewModel().state.value
            assertEquals(QuizStep.Budget, state.step)
            assertEquals(1, state.stepNumber)
            assertEquals(6, state.stepCount)
            assertEquals(1f / 6, state.progress, 1e-6f)
            assertEquals(Currency.INR, state.currency)
            assertTrue(state.localities.any { it.name == "Kakkanad" })
            assertTrue(state.bounds != null)
            assertFalse(state.isEditing)
        }

    @Test
    fun answeringEveryStepSavesTheProfileAndFinishes() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(MatchQuizUiEvent.SetBudget(min = null, max = 30_000))
                vm.onEvent(MatchQuizUiEvent.Next)
                assertEquals(QuizStep.Rooms, vm.state.value.step)
                vm.onEvent(MatchQuizUiEvent.SetBedrooms(2))
                vm.onEvent(MatchQuizUiEvent.SetBathrooms(1))
                vm.onEvent(MatchQuizUiEvent.Next)
                vm.onEvent(MatchQuizUiEvent.ToggleType(PropertyType.Apartment))
                vm.onEvent(MatchQuizUiEvent.ToggleType(PropertyType.House))
                vm.onEvent(MatchQuizUiEvent.ToggleType(PropertyType.House))
                vm.onEvent(MatchQuizUiEvent.Next)
                vm.onEvent(MatchQuizUiEvent.ToggleArea("Kakkanad"))
                vm.onEvent(MatchQuizUiEvent.SetCommute("Edappally"))
                vm.onEvent(MatchQuizUiEvent.Next)
                vm.onEvent(MatchQuizUiEvent.ToggleLifestyle(LifestyleTag.RemoteWorker))
                vm.onEvent(MatchQuizUiEvent.Next)
                assertTrue(vm.state.value.isLastStep)
                assertEquals(1f, vm.state.value.progress, 1e-6f)
                vm.onEvent(MatchQuizUiEvent.ToggleMustHave(MustHave.Parking))
                vm.onEvent(MatchQuizUiEvent.Next)
                assertEquals(MatchQuizUiEffect.Finished, awaitItem())
            }
            val saved = profiles.profiles.value.getValue(auth.account.id)
            assertEquals(BudgetRange(null, 30_000, Currency.INR), saved.budget)
            assertEquals(2, saved.minBedrooms)
            assertEquals(1, saved.minBathrooms)
            assertEquals(setOf(PropertyType.Apartment), saved.propertyTypes)
            assertEquals(listOf("Kakkanad"), saved.neighbourhoods.map { it.name })
            assertEquals("Edappally", saved.commute?.name)
            assertEquals(LocationAnchor.Kind.Commute, saved.commute?.kind)
            assertEquals(setOf(LifestyleTag.RemoteWorker), saved.lifestyle)
            assertEquals(setOf(MustHave.Parking), saved.mustHaves)
        }

    @Test
    fun skipClearsTheStepAndBackRevisitsItWhileBackOnTheFirstStepCloses() =
        runTest(main.dispatcher) {
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(MatchQuizUiEvent.Back)
                assertEquals(MatchQuizUiEffect.Closed, awaitItem())

                vm.onEvent(MatchQuizUiEvent.SetBudget(min = 10_000, max = null))
                vm.onEvent(MatchQuizUiEvent.Skip)
                assertEquals(QuizStep.Rooms, vm.state.value.step)
                assertNull(vm.state.value.draft.budgetMin)
                vm.onEvent(MatchQuizUiEvent.Back)
                assertEquals(QuizStep.Budget, vm.state.value.step)
                repeat(QuizStep.entries.size) { vm.onEvent(MatchQuizUiEvent.Skip) }
                assertEquals(MatchQuizUiEffect.Finished, awaitItem())
            }
            // Skipping everything still saves a (preference-free) profile for the guest, so the banner goes away.
            assertEquals(PreferenceProfile(), profiles.profiles.value[AuthRepository.GUEST_USER_ID])
        }

    @Test
    fun editingStartsFromTheSavedProfile() =
        runTest(main.dispatcher) {
            val existing = PreferenceProfile(minBedrooms = 3, lifestyle = setOf(LifestyleTag.Family))
            profiles.save(AuthRepository.GUEST_USER_ID, existing)
            val vm = viewModel()
            vm.state.test {
                val state = expectMostRecentItem()
                assertTrue(state.isEditing)
                assertEquals(3, state.draft.bedrooms)
                assertEquals(setOf(LifestyleTag.Family), state.draft.lifestyle)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
