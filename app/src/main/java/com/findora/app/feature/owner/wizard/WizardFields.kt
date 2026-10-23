package com.findora.app.feature.owner.wizard

import androidx.annotation.PluralsRes
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import com.findora.app.R
import com.findora.app.core.designsystem.icon.FindoraIcons
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.owner.ListingError
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.domain.owner.WizardStep
import java.util.Locale

/** The inline message for [field] in [state], or null when it is fine. */
@Composable
internal fun errorText(
    state: WizardUiState,
    field: ListingField,
): String? {
    val error = state.errors[field] ?: return null
    val countLimit = countLimits[error]
    return when {
        error == ListingError.Required -> requiredText(field)
        error == ListingError.OutOfRange -> rangeText(field)
        error == ListingError.OutsideRegion ->
            stringResource(R.string.owner_error_pin_outside, state.region?.name.orEmpty())
        countLimit != null -> {
            val count = countLimit.count(field)
            pluralStringResource(countLimit.plural, count, count)
        }
        else -> stringResource(R.string.owner_error_required)
    }
}

/** An error whose message names a count: the plural to show and the limit it states for a field. */
private class CountLimit(
    @param:PluralsRes val plural: Int,
    val count: (ListingField) -> Int,
)

private val countLimits: Map<ListingError, CountLimit> =
    mapOf(
        ListingError.TooShort to
            CountLimit(R.plurals.owner_error_min_chars) { field ->
                if (field == ListingField.Title) ListingValidator.TITLE_MIN else ListingValidator.DESCRIPTION_MIN
            },
        ListingError.TooLong to
            CountLimit(R.plurals.owner_error_max_chars) { field ->
                if (field == ListingField.Title) ListingValidator.TITLE_MAX else ListingValidator.DESCRIPTION_MAX
            },
        ListingError.TooMany to CountLimit(R.plurals.owner_error_photos_max) { ListingValidator.PHOTOS_MAX },
        ListingError.TooHigh to CountLimit(R.plurals.owner_error_deposit_high) { ListingValidator.MAX_DEPOSIT_MONTHS },
    )

@Composable
private fun requiredText(field: ListingField): String =
    when (field) {
        ListingField.Type, ListingField.Region, ListingField.Locality, ListingField.Currency ->
            stringResource(R.string.owner_error_choose)
        ListingField.Pin -> stringResource(R.string.owner_error_pin)
        ListingField.Photos -> stringResource(R.string.owner_error_photos)
        else -> stringResource(R.string.owner_error_required)
    }

@Composable
internal fun rangeText(field: ListingField): String {
    val range =
        when (field) {
            ListingField.Bedrooms -> 0 to ListingValidator.BEDROOMS_MAX
            ListingField.Bathrooms -> ListingValidator.BATHROOMS_MIN to ListingValidator.BATHROOMS_MAX
            ListingField.Area -> ListingValidator.AREA_MIN to ListingValidator.AREA_MAX
            ListingField.Floor -> ListingValidator.FLOOR_MIN to ListingValidator.FLOOR_MAX
            else -> null
        }
    return when {
        range != null -> stringResource(R.string.owner_error_range, range.first, range.second)
        field == ListingField.Rent -> stringResource(R.string.owner_error_rent)
        else -> stringResource(R.string.owner_error_required)
    }
}

@Composable
internal fun StepTitle(
    text: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
) {
    Column(modifier) {
        Text(
            text = text,
            style = FindoraTheme.typography.headline,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (hint != null) {
            Text(text = hint, style = FindoraTheme.typography.caption, color = FindoraTheme.colors.onSurfaceMuted)
        }
    }
}

@Composable
internal fun FieldLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.bodyMedium,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier,
    )
}

/** An inline error under a group of chips (announced politely when it appears). */
@Composable
internal fun GroupError(
    text: String?,
    tag: String,
    modifier: Modifier = Modifier,
) {
    if (text == null) return
    Text(
        text = text,
        style = FindoraTheme.typography.caption,
        color = FindoraTheme.colors.error,
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag(tag),
    )
}

internal fun coordinates(
    point: GeoPoint,
    locale: Locale,
): String = String.format(locale, "%.5f, %.5f", point.latitude, point.longitude)

/** Icon of a wizard step in the preview's summary rows. */
internal fun stepIcon(step: WizardStep) =
    when (step) {
        WizardStep.Basics -> FindoraIcons.Home
        WizardStep.Location -> FindoraIcons.MapPin
        WizardStep.Details -> FindoraIcons.Bed
        WizardStep.Amenities -> FindoraIcons.Sparkles
        WizardStep.Photos -> FindoraIcons.Image
        WizardStep.Price -> FindoraIcons.Coins
        WizardStep.Preview -> FindoraIcons.Eye
    }
