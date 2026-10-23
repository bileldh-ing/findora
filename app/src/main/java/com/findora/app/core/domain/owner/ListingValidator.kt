package com.findora.app.core.domain.owner

import com.findora.app.core.domain.model.GeoBounds

/** A field of [ListingDraft] that can be wrong. */
enum class ListingField {
    Type,
    Title,
    Description,
    Region,
    Locality,
    Pin,
    Bedrooms,
    Bathrooms,
    Area,
    Floor,
    Photos,
    Rent,
    Currency,
    Deposit,
}

/** Why a field is wrong; the UI turns each into an inline message with the bound it refers to. */
enum class ListingError {
    Required,
    TooShort,
    TooLong,
    OutOfRange,
    OutsideRegion,
    TooMany,

    /** The deposit is more than [ListingValidator.MAX_DEPOSIT_MONTHS] months of rent. */
    TooHigh,
}

/**
 * Pure validation of a [ListingDraft], step by step, so the wizard can show inline errors on the step being left and
 * refuse to publish an incomplete listing. [regionBounds] are the bounds of the draft's region (the pin must lie
 * inside them); null skips that check.
 */
object ListingValidator {
    const val TITLE_MIN = 8
    const val TITLE_MAX = 80
    const val DESCRIPTION_MIN = 30
    const val DESCRIPTION_MAX = 2000
    const val BEDROOMS_MAX = 20
    const val BATHROOMS_MIN = 1
    const val BATHROOMS_MAX = 20
    const val AREA_MIN = 8
    const val AREA_MAX = 5000
    const val FLOOR_MIN = -2
    const val FLOOR_MAX = 120
    const val PHOTOS_MIN = 1
    const val PHOTOS_MAX = 12
    const val RENT_MAX = 100_000_000L
    const val MAX_DEPOSIT_MONTHS = 12

    fun validate(
        step: WizardStep,
        draft: ListingDraft,
        regionBounds: GeoBounds? = null,
    ): Map<ListingField, ListingError> =
        buildMap {
            when (step) {
                WizardStep.Basics -> basics(draft)
                WizardStep.Location -> location(draft, regionBounds)
                WizardStep.Details -> details(draft)
                WizardStep.Amenities -> Unit
                WizardStep.Photos -> photos(draft)
                WizardStep.Price -> price(draft)
                WizardStep.Preview ->
                    WizardStep.entries.filter { it != WizardStep.Preview }.forEach { other ->
                        putAll(validate(other, draft, regionBounds))
                    }
            }
        }

    /** The first step with an error, or null when the whole draft can be published. */
    fun firstInvalidStep(
        draft: ListingDraft,
        regionBounds: GeoBounds? = null,
    ): WizardStep? =
        WizardStep.entries
            .filter { it != WizardStep.Preview }
            .firstOrNull { validate(it, draft, regionBounds).isNotEmpty() }

    private fun MutableMap<ListingField, ListingError>.basics(draft: ListingDraft) {
        if (draft.type == null) put(ListingField.Type, ListingError.Required)
        text(ListingField.Title, draft.title, TITLE_MIN, TITLE_MAX)
        text(ListingField.Description, draft.description, DESCRIPTION_MIN, DESCRIPTION_MAX)
    }

    private fun MutableMap<ListingField, ListingError>.location(
        draft: ListingDraft,
        bounds: GeoBounds?,
    ) {
        if (draft.regionId.isNullOrBlank()) put(ListingField.Region, ListingError.Required)
        if (draft.locality.isBlank()) put(ListingField.Locality, ListingError.Required)
        val pin = draft.pin
        when {
            pin == null -> put(ListingField.Pin, ListingError.Required)
            bounds != null && pin !in bounds -> put(ListingField.Pin, ListingError.OutsideRegion)
        }
    }

    private fun MutableMap<ListingField, ListingError>.details(draft: ListingDraft) {
        number(ListingField.Bedrooms, draft.bedrooms, 0, BEDROOMS_MAX)
        number(ListingField.Bathrooms, draft.bathrooms, BATHROOMS_MIN, BATHROOMS_MAX)
        number(ListingField.Area, draft.areaSqm, AREA_MIN, AREA_MAX)
        number(ListingField.Floor, draft.floor, FLOOR_MIN, FLOOR_MAX)
    }

    private fun MutableMap<ListingField, ListingError>.photos(draft: ListingDraft) {
        when {
            draft.photos.size < PHOTOS_MIN -> put(ListingField.Photos, ListingError.Required)
            draft.photos.size > PHOTOS_MAX -> put(ListingField.Photos, ListingError.TooMany)
        }
    }

    private fun MutableMap<ListingField, ListingError>.price(draft: ListingDraft) {
        val rent = draft.rent
        when {
            rent == null -> put(ListingField.Rent, ListingError.Required)
            rent < 1 || rent > RENT_MAX -> put(ListingField.Rent, ListingError.OutOfRange)
        }
        if (draft.currency == null) put(ListingField.Currency, ListingError.Required)
        val deposit = draft.deposit ?: return
        when {
            deposit < 0 -> put(ListingField.Deposit, ListingError.OutOfRange)
            rent != null && rent > 0 && deposit > rent * MAX_DEPOSIT_MONTHS ->
                put(ListingField.Deposit, ListingError.TooHigh)
        }
    }

    private fun MutableMap<ListingField, ListingError>.text(
        field: ListingField,
        value: String,
        min: Int,
        max: Int,
    ) {
        val length = value.trim().length
        when {
            length == 0 -> put(field, ListingError.Required)
            length < min -> put(field, ListingError.TooShort)
            length > max -> put(field, ListingError.TooLong)
        }
    }

    private fun MutableMap<ListingField, ListingError>.number(
        field: ListingField,
        value: Int?,
        min: Int,
        max: Int,
    ) {
        when {
            value == null -> put(field, ListingError.Required)
            value !in min..max -> put(field, ListingError.OutOfRange)
        }
    }
}
