package com.findora.app.feature.owner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.findora.app.core.data.repository.SeedRegionRepository
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.model.UserAccount
import com.findora.app.core.domain.owner.ListingDraft
import com.findora.app.core.domain.owner.ListingError
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.WizardStep
import com.findora.app.core.testing.completeDraft
import com.findora.app.core.testing.fileSeedReader
import com.findora.app.core.testing.testPhoto
import com.findora.app.feature.owner.wizard.ListingWizardScreen
import com.findora.app.feature.owner.wizard.WizardEvent
import com.findora.app.feature.owner.wizard.WizardUiState
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
@ConscryptMode(ConscryptMode.Mode.OFF)
class ListingWizardScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val events = mutableListOf<WizardEvent>()
    private var picked = 0
    private val regions = runBlocking { SeedRegionRepository(fileSeedReader(), Dispatchers.Unconfined).regions() }
    private val owner = UserAccount("owner-1", "ada@findora.app", "Ada Lovelace")

    private fun show(state: WizardUiState) {
        compose.setContent {
            FindoraTheme {
                ListingWizardScreen(
                    state = state,
                    onEvent = { events += it },
                    onClose = {},
                    onCreateAccount = {},
                    onPickPhotos = { picked++ },
                    pinPicker = { _, _, _, onPin, modifier ->
                        Box(modifier.testTag("fakeMap"))
                        if (state.draft.pin == null) onPin(GeoPoint(9.97, 76.28))
                    },
                )
            }
        }
    }

    private fun state(
        step: WizardStep,
        draft: ListingDraft = ListingDraft(regionId = "kerala"),
        errors: Map<ListingField, ListingError> = emptyMap(),
    ) = WizardUiState(
        loading = false,
        listingId = "own-1",
        step = step,
        draft = draft,
        errors = errors,
        regions = regions,
        owner = owner,
    )

    @Test
    fun basicsShowsInlineErrorsAndSendsEdits() {
        show(
            state(
                WizardStep.Basics,
                errors =
                    mapOf(
                        ListingField.Type to ListingError.Required,
                        ListingField.Title to ListingError.TooShort,
                        ListingField.Description to ListingError.Required,
                    ),
            ),
        )
        compose.onNodeWithText("Step 1 of 7 · Basics").assertIsDisplayed()
        compose.onNodeWithTag("error_type").assertIsDisplayed()
        compose.onNodeWithText("At least 8 characters").assertIsDisplayed()
        compose.onNodeWithTag("type_house").performClick()
        compose.onNodeWithTag("field_title").performTextInput("Villa")
        compose.onNodeWithTag("wizardNext").performClick()
        assertEquals(
            listOf(WizardEvent.TypeSelected(PropertyType.House), WizardEvent.TitleChanged("Villa"), WizardEvent.Next),
            events,
        )
    }

    @Test
    fun locationOffersRegionsLocalitiesTheMapAndTheNeighbourhoodPin() {
        show(state(WizardStep.Location, ListingDraft(regionId = "kerala", locality = "Kakkanad")))
        compose.onNodeWithTag("region_paris").assertExists()
        compose.onNodeWithTag("locality_Kakkanad").assertExists()
        compose.onNodeWithTag("fakeMap").assertExists()
        assertTrue(events.contains(WizardEvent.PinPlaced(GeoPoint(9.97, 76.28))))
        compose.onNodeWithTag("pinAtLocality").performScrollTo().performClick()
        compose.onNodeWithTag("region_tunis").performScrollTo().performClick()
        assertTrue(events.contains(WizardEvent.PinAtLocality))
        assertTrue(events.contains(WizardEvent.RegionSelected("tunis")))
    }

    @Test
    fun detailsReportParsedNumbersAndToggles() {
        show(state(WizardStep.Details, errors = mapOf(ListingField.Area to ListingError.OutOfRange)))
        compose.onNodeWithText("Enter a number from 8 to 5000").assertIsDisplayed()
        compose.onNodeWithTag("field_bedrooms").performTextInput("3a")
        compose.onNodeWithTag("field_floor").performTextInput("-1")
        compose.onNodeWithTag("switch_parking").performScrollTo().performClick()
        assertTrue(events.contains(WizardEvent.BedroomsChanged(3)))
        assertTrue(events.contains(WizardEvent.FloorChanged(-1)))
        assertTrue(events.contains(WizardEvent.ParkingChanged(true)))
    }

    @Test
    fun amenitiesToggle() {
        show(state(WizardStep.Amenities))
        compose.onNodeWithTag("amenity_balcony").performClick()
        assertEquals(listOf(WizardEvent.AmenityToggled(Amenity.Balcony)), events)
    }

    @Test
    fun photosStepOpensThePickerAndManagesEachPhoto() {
        val draft = ListingDraft(regionId = "kerala", photos = listOf(testPhoto(index = 0), testPhoto(index = 1)))
        show(state(WizardStep.Photos, draft))
        compose.onNodeWithText("Add photos (10 left)").performClick()
        assertEquals(1, picked)
        compose.onNodeWithTag("photoEarlier_0").assertIsNotEnabled()
        compose.onNodeWithTag("photoCover_1").performScrollTo().performClick()
        compose.onNodeWithTag("photoRemove_0").performScrollTo().performClick()
        compose.onNodeWithTag("photoLater_0").performScrollTo().performClick()
        assertEquals(
            listOf(WizardEvent.CoverChosen(1), WizardEvent.PhotoRemoved(0), WizardEvent.PhotoMoved(0, 1)),
            events,
        )
    }

    @Test
    fun priceStepShowsTheDepositLimit() {
        show(state(WizardStep.Price, completeDraft(), mapOf(ListingField.Deposit to ListingError.TooHigh)))
        compose.onNodeWithText("A deposit can be at most 12 months of rent").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("currency_EUR").performClick()
        assertTrue(events.contains(WizardEvent.CurrencySelected(com.findora.app.core.domain.model.Currency.EUR)))
    }

    @Test
    fun previewShowsTheCardEverySectionAndPublish() {
        show(state(WizardStep.Preview, completeDraft()))
        compose.onNodeWithTag("previewCard").assertExists()
        compose.onNodeWithTag("summary_Location").performScrollTo().performClick()
        compose.onNodeWithTag("wizardPublish").performClick()
        assertEquals(listOf(WizardEvent.GoTo(WizardStep.Location), WizardEvent.Publish), events)
    }

    @Test
    fun guestsAreAskedToCreateAnAccount() {
        show(WizardUiState(loading = false, needsAccount = true))
        compose.onNodeWithTag("wizardMessage").assertIsDisplayed()
        compose.onNodeWithText("Create account").assertIsDisplayed()
    }
}
