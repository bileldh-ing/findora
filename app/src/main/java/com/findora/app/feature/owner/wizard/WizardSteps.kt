package com.findora.app.feature.owner.wizard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.component.FindoraTextField
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.SwitchRow
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.GeoPoint
import com.findora.app.core.domain.model.PropertyType
import com.findora.app.core.domain.owner.ListingField
import com.findora.app.core.domain.owner.ListingValidator
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.currentLocale
import com.findora.app.core.ui.match.labelRes
import com.findora.app.feature.home.labelRes

private val MapHeight = 260.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BasicsStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        StepTitle(stringResource(R.string.owner_step_basics), hint = stringResource(R.string.owner_basics_hint))
        FieldLabel(stringResource(R.string.owner_field_type))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            PropertyType.entries.forEach { type ->
                CategoryChip(
                    text = stringResource(type.labelRes()),
                    selected = draft.type == type,
                    onClick = { onEvent(WizardEvent.TypeSelected(type)) },
                    modifier = Modifier.testTag("type_${type.id}"),
                )
            }
        }
        GroupError(errorText(state, ListingField.Type), "error_type")
        FindoraTextField(
            value = draft.title,
            onValueChange = { onEvent(WizardEvent.TitleChanged(it)) },
            label = stringResource(R.string.owner_field_title),
            error = errorText(state, ListingField.Title),
            supportingText = stringResource(R.string.owner_char_count, draft.title.length, ListingValidator.TITLE_MAX),
            fieldTag = "field_title",
        )
        FindoraTextField(
            value = draft.description,
            onValueChange = { onEvent(WizardEvent.DescriptionChanged(it)) },
            label = stringResource(R.string.owner_field_description),
            error = errorText(state, ListingField.Description),
            supportingText =
                stringResource(R.string.owner_char_count, draft.description.length, ListingValidator.DESCRIPTION_MAX),
            minLines = DESCRIPTION_LINES,
            fieldTag = "field_description",
        )
    }
}

private const val DESCRIPTION_LINES = 4

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LocationStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    pinPicker: @Composable (GeoPoint, Double, GeoPoint?, (GeoPoint) -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    val region = state.region
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        StepTitle(stringResource(R.string.owner_step_location), hint = stringResource(R.string.owner_location_hint))
        FieldLabel(stringResource(R.string.owner_field_region))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            state.regions.forEach { pack ->
                CategoryChip(
                    text = pack.name,
                    selected = draft.regionId == pack.id,
                    onClick = { onEvent(WizardEvent.RegionSelected(pack.id)) },
                    modifier = Modifier.testTag("region_${pack.id}"),
                )
            }
        }
        GroupError(errorText(state, ListingField.Region), "error_region")
        if (region != null) {
            FieldLabel(stringResource(R.string.owner_field_locality))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
                verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
                modifier = Modifier.selectableGroup(),
            ) {
                region.localities.forEach { locality ->
                    CategoryChip(
                        text = locality.name,
                        selected = draft.locality == locality.name,
                        onClick = { onEvent(WizardEvent.LocalitySelected(locality.name)) },
                        modifier = Modifier.testTag("locality_${locality.name}"),
                    )
                }
            }
            GroupError(errorText(state, ListingField.Locality), "error_locality")
            FieldLabel(stringResource(R.string.owner_field_pin))
            pinPicker(
                draft.pin ?: region.center,
                if (draft.pin == null) region.defaultZoom else PIN_ZOOM,
                draft.pin,
                { onEvent(WizardEvent.PinPlaced(it)) },
                Modifier.fillMaxWidth().height(MapHeight),
            )
            Text(
                text =
                    draft.pin?.let { stringResource(R.string.owner_pin_at, coordinates(it, currentLocale())) }
                        ?: stringResource(R.string.owner_pin_none),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
                modifier = Modifier.testTag("pinLabel"),
            )
            GroupError(errorText(state, ListingField.Pin), "error_pin")
            if (draft.locality.isNotBlank()) {
                SecondaryPillButton(
                    text = stringResource(R.string.owner_pin_at_locality, draft.locality),
                    onClick = { onEvent(WizardEvent.PinAtLocality) },
                    modifier = Modifier.fillMaxWidth().testTag("pinAtLocality"),
                )
            }
        }
    }
}

private const val PIN_ZOOM = 16.0

@Composable
internal fun DetailsStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        StepTitle(stringResource(R.string.owner_step_details))
        Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm)) {
            NumberField(
                value = draft.bedrooms?.toLong(),
                onValueChange = { onEvent(WizardEvent.BedroomsChanged(it?.toInt())) },
                label = stringResource(R.string.owner_field_bedrooms),
                error = errorText(state, ListingField.Bedrooms),
                tag = "field_bedrooms",
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = draft.bathrooms?.toLong(),
                onValueChange = { onEvent(WizardEvent.BathroomsChanged(it?.toInt())) },
                label = stringResource(R.string.owner_field_bathrooms),
                error = errorText(state, ListingField.Bathrooms),
                tag = "field_bathrooms",
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm)) {
            NumberField(
                value = draft.areaSqm?.toLong(),
                onValueChange = { onEvent(WizardEvent.AreaChanged(it?.toInt())) },
                label = stringResource(R.string.owner_field_area),
                error = errorText(state, ListingField.Area),
                tag = "field_area",
                modifier = Modifier.weight(1f),
            )
            NumberField(
                value = draft.floor?.toLong(),
                onValueChange = { onEvent(WizardEvent.FloorChanged(it?.toInt())) },
                label = stringResource(R.string.owner_field_floor),
                error = errorText(state, ListingField.Floor),
                allowNegative = true,
                tag = "field_floor",
                modifier = Modifier.weight(1f),
            )
        }
        draft.areaSqm?.takeIf { it > 0 }?.let { area ->
            Text(
                text = areaText(area),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
        }
        SwitchRow(
            title = stringResource(R.string.owner_field_furnished),
            checked = draft.furnished,
            onCheckedChange = { onEvent(WizardEvent.FurnishedChanged(it)) },
            modifier = Modifier.testTag("switch_furnished"),
        )
        SwitchRow(
            title = stringResource(R.string.owner_field_pets),
            checked = draft.petFriendly,
            onCheckedChange = { onEvent(WizardEvent.PetsChanged(it)) },
            modifier = Modifier.testTag("switch_pets"),
        )
        SwitchRow(
            title = stringResource(R.string.owner_field_parking),
            checked = draft.parking,
            onCheckedChange = { onEvent(WizardEvent.ParkingChanged(it)) },
            modifier = Modifier.testTag("switch_parking"),
        )
    }
}

/**
 * A whole-number field. It keeps what is typed (so "-" can start a negative floor) and reports the parsed value, or
 * null while the text is not a number.
 */
@Composable
internal fun NumberField(
    value: Long?,
    onValueChange: (Long?) -> Unit,
    label: String,
    error: String?,
    tag: String,
    modifier: Modifier = Modifier,
    allowNegative: Boolean = false,
) {
    var text by rememberSaveable(tag) { mutableStateOf(value?.toString().orEmpty()) }
    if (value != text.toLongOrNull() && !(value == null && text == "-")) text = value?.toString().orEmpty()
    FindoraTextField(
        value = text,
        onValueChange = { input ->
            val filtered =
                input
                    .filterIndexed { index, char -> char.isDigit() || (allowNegative && char == '-' && index == 0) }
                    .take(MAX_DIGITS)
            text = filtered
            onValueChange(filtered.toLongOrNull())
        },
        label = label,
        error = error,
        keyboardType = if (allowNegative) KeyboardType.Text else KeyboardType.Number,
        fieldTag = tag,
        modifier = modifier,
    )
}

private const val MAX_DIGITS = 10

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AmenitiesStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        StepTitle(stringResource(R.string.owner_step_amenities), hint = stringResource(R.string.owner_amenities_hint))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        ) {
            Amenity.entries.forEach { amenity ->
                CategoryChip(
                    text = stringResource(amenity.labelRes()),
                    selected = amenity in state.draft.amenities,
                    onClick = { onEvent(WizardEvent.AmenityToggled(amenity)) },
                    modifier = Modifier.testTag("amenity_${amenity.id}"),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PriceStep(
    state: WizardUiState,
    onEvent: (WizardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val draft = state.draft
    Column(modifier, verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.md)) {
        StepTitle(stringResource(R.string.owner_step_price))
        FieldLabel(stringResource(R.string.owner_field_currency))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            modifier = Modifier.selectableGroup(),
        ) {
            Currency.entries.forEach { currency ->
                CategoryChip(
                    text = currency.code,
                    selected = draft.currency == currency,
                    onClick = { onEvent(WizardEvent.CurrencySelected(currency)) },
                    modifier = Modifier.testTag("currency_${currency.code}"),
                )
            }
        }
        GroupError(errorText(state, ListingField.Currency), "error_currency")
        NumberField(
            value = draft.rent,
            onValueChange = { onEvent(WizardEvent.RentChanged(it)) },
            label = stringResource(R.string.owner_field_rent, draft.currency?.code.orEmpty()),
            error = errorText(state, ListingField.Rent),
            tag = "field_rent",
        )
        NumberField(
            value = draft.deposit,
            onValueChange = { onEvent(WizardEvent.DepositChanged(it)) },
            label = stringResource(R.string.owner_field_deposit, draft.currency?.code.orEmpty()),
            error = errorText(state, ListingField.Deposit),
            tag = "field_deposit",
        )
        Text(
            text =
                pluralStringResource(
                    R.plurals.owner_deposit_hint,
                    ListingValidator.MAX_DEPOSIT_MONTHS,
                    ListingValidator.MAX_DEPOSIT_MONTHS,
                ),
            style = FindoraTheme.typography.caption,
            color = FindoraTheme.colors.onSurfaceMuted,
        )
    }
}
