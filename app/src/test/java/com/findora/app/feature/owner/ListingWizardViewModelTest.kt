package com.findora.app.feature.owner

import androidx.lifecycle.SavedStateHandle
import app.cash.turbine.test
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.domain.model.AppSettings
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.owner.ListingError
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.ListingStatus
import com.findora.app.core.domain.owner.OwnerListing
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.testing.FakeAuthRepository
import com.findora.app.core.testing.FakeListingPhotoStore
import com.findora.app.core.testing.FakeOwnerListingRepository
import com.findora.app.core.testing.FakeSettingsRepository
import com.findora.app.core.testing.MainDispatcherRule
import com.findora.app.core.testing.completeDraft
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.feature.owner.wizard.ListingWizardViewModel
import com.findora.app.feature.owner.wizard.SaveState
import com.findora.app.feature.owner.wizard.WizardEffect
import com.findora.app.feature.owner.wizard.WizardEvent
import com.findora.app.feature.owner.wizard.moved
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Instant

class ListingWizardViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    private val auth = FakeAuthRepository()
    private val repo = FakeOwnerListingRepository()
    private val photos = FakeListingPhotoStore()
    private val settings = FakeSettingsRepository(AppSettings(regionId = "tunis"))

    private fun viewModel(listingId: String? = null) =
        ListingWizardViewModel(
            savedStateHandle = SavedStateHandle(mapOf(ListingWizardViewModel.LISTING_ID_KEY to listingId)),
            auth = auth,
            listings = repo,
            photoStore = photos,
            regions = SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined),
            settings = settings,
        )

    private fun existing(
        draft: com.findora.app.core.domain.owner.ListingDraft = completeDraft(),
        owner: String = auth.account.id,
        status: ListingStatus = ListingStatus.Draft,
    ): String {
        repo.put(OwnerListing("own-9", owner, status, draft, Instant.EPOCH, Instant.EPOCH, null))
        return "own-9"
    }

    @Test
    fun aNewWizardCreatesADraftInTheRegionBeingBrowsed() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.state.test {
                var state = awaitItem()
                while (state.loading) state = awaitItem()
                assertEquals("own-1", state.listingId)
                assertEquals(WizardStep.Basics, state.step)
                assertEquals("tunis", state.draft.regionId)
                assertEquals(Currency.TND, state.draft.currency)
                assertEquals(4, state.regions.size)
                assertEquals(auth.account, state.owner)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun nextShowsTheStepsErrorsWhichThenFollowTheEditsLive() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.onEvent(WizardEvent.Next)
            val errors = vm.state.value.errors
            assertEquals(WizardStep.Basics, vm.state.value.step)
            assertEquals(ListingError.Required, errors[ListingField.Type])
            assertEquals(ListingError.Required, errors[ListingField.Title])
            assertEquals(ListingError.Required, errors[ListingField.Description])
            vm.onEvent(WizardEvent.TitleChanged("Flat"))
            assertEquals(ListingError.TooShort, vm.state.value.errors[ListingField.Title])
            vm.onEvent(WizardEvent.TitleChanged("Sea view studio in La Marsa"))
            assertNull(vm.state.value.errors[ListingField.Title])
            vm.onEvent(WizardEvent.TypeSelected(PropertyType.Apartment))
            vm.onEvent(WizardEvent.DescriptionChanged("Quiet studio two minutes from the beach, fully furnished."))
            assertTrue(
                vm.state.value.errors
                    .isEmpty(),
            )
            vm.onEvent(WizardEvent.Next)
            assertEquals(WizardStep.Location, vm.state.value.step)
            vm.onEvent(WizardEvent.Back)
            assertEquals(WizardStep.Basics, vm.state.value.step)
        }

    @Test
    fun localityPlacesThePinAndChangingRegionClearsWhatNoLongerFits() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.onEvent(WizardEvent.LocalitySelected("La Marsa"))
            val pin = vm.state.value.draft.pin
            assertEquals("La Marsa", vm.state.value.draft.locality)
            assertTrue(
                pin != null &&
                    pin in
                    vm.state.value.region!!
                        .bounds,
            )
            vm.onEvent(WizardEvent.RegionSelected("paris"))
            assertEquals("", vm.state.value.draft.locality)
            assertNull(vm.state.value.draft.pin)
            assertEquals(Currency.EUR, vm.state.value.draft.currency)
            vm.onEvent(WizardEvent.LocalitySelected("Montmartre"))
            vm.onEvent(
                WizardEvent.PinPlaced(
                    com.findora.app.core.domain.model
                        .GeoPoint(48.88, 2.34),
                ),
            )
            vm.onEvent(WizardEvent.PinAtLocality)
            assertEquals(
                vm.state.value.region!!
                    .localities
                    .first { it.name == "Montmartre" }
                    .location,
                vm.state.value.draft.pin,
            )
        }

    @Test
    fun editsAreAutosavedAfterAPauseAndImmediatelyOnStepChanges() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            advanceTimeBy(ListingWizardViewModel.AUTOSAVE_MILLIS + 1)
            val before = repo.saved.size
            vm.onEvent(WizardEvent.TitleChanged("Garden house"))
            vm.onEvent(WizardEvent.TitleChanged("Garden house near the lake"))
            advanceTimeBy(ListingWizardViewModel.AUTOSAVE_MILLIS - 1)
            assertEquals(before, repo.saved.size)
            advanceTimeBy(2)
            runCurrent()
            assertEquals(before + 1, repo.saved.size)
            assertEquals("Garden house near the lake", repo.saved.last().title)
            assertEquals(SaveState.Saved, vm.state.value.saveState)
            assertEquals(
                "Garden house near the lake",
                repo.listings.value
                    .getValue("own-1")
                    .draft.title,
            )
            vm.onEvent(WizardEvent.DescriptionChanged("A garden house a short walk from the lake, with parking."))
            vm.onEvent(WizardEvent.Back)
            runCurrent()
            assertEquals("A garden house a short walk from the lake, with parking.", repo.saved.last().description)
        }

    @Test
    fun backOnTheFirstStepClosesTheWizard() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(WizardEvent.Back)
                assertEquals(WizardEffect.Close, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun laterStepsAreOnlyReachableOnceEveryEarlierStepIsComplete() =
        runTest(main.dispatcher) {
            auth.signIn()
            val id = existing(completeDraft().copy(photos = emptyList()))
            val vm = viewModel(id)
            vm.onEvent(WizardEvent.GoTo(WizardStep.Preview))
            assertEquals(WizardStep.Photos, vm.state.value.step)
            assertEquals(ListingError.Required, vm.state.value.errors[ListingField.Photos])
            vm.onEvent(WizardEvent.GoTo(WizardStep.Basics))
            assertEquals(WizardStep.Basics, vm.state.value.step)
            assertTrue(
                vm.state.value.errors
                    .isEmpty(),
            )
        }

    @Test
    fun publishMakesACompleteListingLive() =
        runTest(main.dispatcher) {
            auth.signIn()
            val id = existing()
            val vm = viewModel(id)
            vm.onEvent(WizardEvent.GoTo(WizardStep.Preview))
            assertEquals(WizardStep.Preview, vm.state.value.step)
            vm.effects.test {
                vm.onEvent(WizardEvent.Publish)
                assertEquals(WizardEffect.Published(id), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(
                ListingStatus.Published,
                repo.listings.value
                    .getValue(id)
                    .status,
            )
            assertEquals(ListingStatus.Published, vm.state.value.status)
            assertFalse(vm.state.value.publishing)
        }

    @Test
    fun publishingAnIncompleteListingJumpsToItsFirstIncompleteStep() =
        runTest(main.dispatcher) {
            auth.signIn()
            val id = existing(completeDraft().copy(rent = null))
            val vm = viewModel(id)
            vm.onEvent(WizardEvent.Publish)
            assertEquals(WizardStep.Price, vm.state.value.step)
            assertEquals(ListingError.Required, vm.state.value.errors[ListingField.Rent])
            assertEquals(
                ListingStatus.Draft,
                repo.listings.value
                    .getValue(id)
                    .status,
            )
        }

    @Test
    fun photosAreImportedReorderedAndRemoved() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(WizardEvent.PhotosPicked(listOf("content://1", "bad://2", "content://3")))
                assertEquals(WizardEffect.PhotosFailed(1), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            val imported = vm.state.value.draft.photos
            assertEquals(2, imported.size)
            assertEquals(0, vm.state.value.importingPhotos)
            vm.onEvent(WizardEvent.CoverChosen(1))
            assertEquals(listOf(imported[1], imported[0]), vm.state.value.draft.photos)
            vm.onEvent(WizardEvent.PhotoMoved(0, 1))
            assertEquals(imported, vm.state.value.draft.photos)
            vm.onEvent(WizardEvent.PhotoRemoved(0))
            assertEquals(listOf(imported[1]), vm.state.value.draft.photos)
            // A never-published draft deletes the file at once.
            assertEquals(listOf(imported[0]), photos.deleted)
        }

    @Test
    fun picksBeyondTwelvePhotosAreLeftOut() =
        runTest(main.dispatcher) {
            auth.signIn()
            val vm = viewModel()
            vm.effects.test {
                vm.onEvent(WizardEvent.PhotosPicked((1..14).map { "content://$it" }))
                assertEquals(WizardEffect.PhotosOverLimit(12), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(12, vm.state.value.draft.photos.size)
            assertFalse(vm.state.value.canAddPhotos)
        }

    @Test
    fun guestsAndOtherOwnersListingsAreRefused() =
        runTest(main.dispatcher) {
            auth.continueAsGuest()
            assertTrue(viewModel().state.value.needsAccount)
            assertTrue(repo.listings.value.isEmpty())
            auth.signIn()
            val id = existing(owner = "someone-else")
            val vm = viewModel(id)
            assertTrue(vm.state.value.notFound)
            vm.onEvent(WizardEvent.TitleChanged("Not mine at all"))
            assertTrue(repo.saved.isEmpty())
        }

    @Test
    fun movedReordersAndClampsIndices() {
        assertEquals(listOf("b", "a", "c"), listOf("a", "b", "c").moved(0, 1))
        assertEquals(listOf("b", "c", "a"), listOf("a", "b", "c").moved(0, 9))
        assertEquals(listOf("a", "b"), listOf("a", "b").moved(5, 0))
    }
}
