package com.findora.app.core.domain.owner

import com.findora.app.core.domain.model.GeoBounds
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.testing.completeDraft
import com.findora.app.core.testing.testPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListingValidatorTest {
    private val kerala = GeoBounds(south = 8.17, west = 74.85, north = 12.8, east = 77.42)

    @Test
    fun aCompleteDraftPassesEveryStep() {
        WizardStep.entries.forEach { step ->
            assertEquals(
                step.name,
                emptyMap<ListingField, ListingError>(),
                ListingValidator.validate(step, completeDraft(), kerala),
            )
        }
        assertNull(ListingValidator.firstInvalidStep(completeDraft(), kerala))
    }

    @Test
    fun anEmptyDraftFailsAtBasicsWithEveryBasicsField() {
        val errors = ListingValidator.validate(WizardStep.Basics, ListingDraft())
        assertEquals(
            mapOf(
                ListingField.Type to ListingError.Required,
                ListingField.Title to ListingError.Required,
                ListingField.Description to ListingError.Required,
            ),
            errors,
        )
        assertEquals(WizardStep.Basics, ListingValidator.firstInvalidStep(ListingDraft()))
    }

    @Test
    fun titleAndDescriptionLengthsAreTrimmedAndBounded() {
        val short = completeDraft().copy(title = "  Flat   ", description = "Too short")
        val errors = ListingValidator.validate(WizardStep.Basics, short)
        assertEquals(ListingError.TooShort, errors[ListingField.Title])
        assertEquals(ListingError.TooShort, errors[ListingField.Description])
        val long = completeDraft().copy(title = "x".repeat(ListingValidator.TITLE_MAX + 1))
        assertEquals(ListingError.TooLong, ListingValidator.validate(WizardStep.Basics, long)[ListingField.Title])
        val exact = completeDraft().copy(title = "x".repeat(ListingValidator.TITLE_MIN))
        assertTrue(ListingValidator.validate(WizardStep.Basics, exact).isEmpty())
    }

    @Test
    fun locationNeedsRegionLocalityAndAPinInsideTheRegion() {
        val missing = completeDraft().copy(regionId = null, locality = " ", pin = null)
        assertEquals(
            mapOf(
                ListingField.Region to ListingError.Required,
                ListingField.Locality to ListingError.Required,
                ListingField.Pin to ListingError.Required,
            ),
            ListingValidator.validate(WizardStep.Location, missing, kerala),
        )
        val paris = completeDraft().copy(pin = GeoPoint(48.85, 2.35))
        assertEquals(
            ListingError.OutsideRegion,
            ListingValidator.validate(WizardStep.Location, paris, kerala)[ListingField.Pin],
        )
        // Without known bounds the pin is only required.
        assertTrue(ListingValidator.validate(WizardStep.Location, paris).isEmpty())
    }

    @Test
    fun detailsAreRequiredAndRangeChecked() {
        val draft = completeDraft().copy(bedrooms = null, bathrooms = 0, areaSqm = 7, floor = -3)
        assertEquals(
            mapOf(
                ListingField.Bedrooms to ListingError.Required,
                ListingField.Bathrooms to ListingError.OutOfRange,
                ListingField.Area to ListingError.OutOfRange,
                ListingField.Floor to ListingError.OutOfRange,
            ),
            ListingValidator.validate(WizardStep.Details, draft),
        )
        // A studio (0 bedrooms) on the second basement level is fine.
        val studio = completeDraft().copy(bedrooms = 0, floor = ListingValidator.FLOOR_MIN)
        assertTrue(ListingValidator.validate(WizardStep.Details, studio).isEmpty())
    }

    @Test
    fun amenitiesAreOptional() {
        assertTrue(
            ListingValidator.validate(WizardStep.Amenities, completeDraft().copy(amenities = emptySet())).isEmpty(),
        )
    }

    @Test
    fun photosNeedOneAndAtMostTwelve() {
        assertEquals(
            ListingError.Required,
            ListingValidator.validate(
                WizardStep.Photos,
                completeDraft().copy(photos = emptyList()),
            )[ListingField.Photos],
        )
        val many = (0..ListingValidator.PHOTOS_MAX).map { testPhoto(index = it) }
        assertEquals(
            ListingError.TooMany,
            ListingValidator.validate(WizardStep.Photos, completeDraft().copy(photos = many))[ListingField.Photos],
        )
    }

    @Test
    fun priceNeedsRentAndCurrencyAndCapsTheDeposit() {
        val missing = completeDraft().copy(rent = null, currency = null, deposit = null)
        assertEquals(
            mapOf(ListingField.Rent to ListingError.Required, ListingField.Currency to ListingError.Required),
            ListingValidator.validate(WizardStep.Price, missing),
        )
        assertEquals(
            ListingError.OutOfRange,
            ListingValidator.validate(WizardStep.Price, completeDraft().copy(rent = 0))[ListingField.Rent],
        )
        val deposit = completeDraft().copy(rent = 1_000, deposit = 12_001)
        assertEquals(ListingError.TooHigh, ListingValidator.validate(WizardStep.Price, deposit)[ListingField.Deposit])
        assertTrue(ListingValidator.validate(WizardStep.Price, deposit.copy(deposit = 12_000)).isEmpty())
    }

    @Test
    fun previewReportsEveryErrorAndFirstInvalidStepIsTheEarliest() {
        val draft = completeDraft().copy(photos = emptyList(), rent = null)
        val errors = ListingValidator.validate(WizardStep.Preview, draft, kerala)
        assertEquals(setOf(ListingField.Photos, ListingField.Rent), errors.keys)
        assertEquals(WizardStep.Photos, ListingValidator.firstInvalidStep(draft, kerala))
    }

    @Test
    fun stepsNavigateInOrder() {
        assertEquals(WizardStep.Location, WizardStep.Basics.next())
        assertNull(WizardStep.Preview.next())
        assertNull(WizardStep.Basics.previous())
        assertEquals(7, WizardStep.Preview.number)
    }
}
