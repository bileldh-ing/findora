package com.findora.app.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.os.ConfigurationCompat
import com.findora.app.R
import com.findora.app.core.designsystem.component.CategoryChip
import com.findora.app.core.designsystem.component.PrimaryPillButton
import com.findora.app.core.designsystem.component.SecondaryPillButton
import com.findora.app.core.designsystem.component.SwitchRow
import com.findora.app.core.designsystem.theme.FindoraTheme
import com.findora.app.core.domain.format.PriceStyle
import com.findora.app.core.domain.model.Amenity
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.ListingBounds
import com.findora.app.core.domain.model.Money
import com.findora.app.core.domain.model.PropertyFilter
import com.findora.app.core.domain.model.SortOrder
import com.findora.app.core.ui.areaText
import com.findora.app.core.ui.rentText
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private val BEDROOM_OPTIONS = listOf(1, 2, 3, 4)
private val BATHROOM_OPTIONS = listOf(1, 2, 3)

/**
 * Filter sheet content. Edits a draft and only publishes it on "Show results"; "Reset" clears every
 * refinement (category and search text are kept). Sliders are bounded by the cached listings of the region.
 * "Best match" is offered only when [showBestMatch] (the user has a preference profile).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FilterSheetContent(
    initial: PropertyFilter,
    bounds: ListingBounds?,
    currency: Currency,
    onApply: (PropertyFilter) -> Unit,
    modifier: Modifier = Modifier,
    showBestMatch: Boolean = false,
) {
    var draft by remember(initial) { mutableStateOf(initial) }
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FindoraTheme.spacing.screenHorizontal)
                .navigationBarsPadding()
                .testTag("filterSheet"),
        verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
    ) {
        Text(
            text = stringResource(R.string.filter_title),
            style = FindoraTheme.typography.title,
            color = FindoraTheme.colors.onBackground,
            modifier = Modifier.semantics { heading() },
        )
        if (bounds != null && bounds.maxRent > bounds.minRent) {
            SheetSection(stringResource(R.string.filter_price))
            val low = draft.minRent ?: bounds.minRent
            val high = draft.maxRent ?: bounds.maxRent
            Text(
                text =
                    stringResource(
                        R.string.filter_range_value,
                        rentText(Money(low, currency), PriceStyle.Design, locale),
                        rentText(Money(high, currency), PriceStyle.Design, locale),
                    ),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
            RangeSlider(
                value = low.toFloat()..high.toFloat(),
                onValueChange = { range ->
                    val min = range.start.roundToLong()
                    val max = range.endInclusive.roundToLong()
                    draft =
                        draft.copy(
                            minRent = min.takeIf { it > bounds.minRent },
                            maxRent = max.takeIf { it < bounds.maxRent },
                        )
                },
                valueRange = bounds.minRent.toFloat()..bounds.maxRent.toFloat(),
                colors = sliderColors(),
                modifier = Modifier.testTag("priceSlider"),
            )
        }
        SheetSection(stringResource(R.string.filter_bedrooms))
        CountOptions(BEDROOM_OPTIONS, draft.minBedrooms, "beds") { draft = draft.copy(minBedrooms = it) }
        SheetSection(stringResource(R.string.filter_bathrooms))
        CountOptions(BATHROOM_OPTIONS, draft.minBathrooms, "baths") { draft = draft.copy(minBathrooms = it) }
        if (bounds != null && bounds.maxAreaSqm > bounds.minAreaSqm) {
            SheetSection(stringResource(R.string.filter_area))
            val low = draft.minAreaSqm ?: bounds.minAreaSqm
            val high = draft.maxAreaSqm ?: bounds.maxAreaSqm
            Text(
                text =
                    stringResource(
                        R.string.filter_range_value,
                        areaText(low, locale = locale),
                        areaText(high, locale = locale),
                    ),
                style = FindoraTheme.typography.caption,
                color = FindoraTheme.colors.onSurfaceMuted,
            )
            RangeSlider(
                value = low.toFloat()..high.toFloat(),
                onValueChange = { range ->
                    val min = range.start.roundToInt()
                    val max = range.endInclusive.roundToInt()
                    draft =
                        draft.copy(
                            minAreaSqm = min.takeIf { it > bounds.minAreaSqm },
                            maxAreaSqm = max.takeIf { it < bounds.maxAreaSqm },
                        )
                },
                valueRange = bounds.minAreaSqm.toFloat()..bounds.maxAreaSqm.toFloat(),
                colors = sliderColors(),
            )
        }
        SwitchRow(
            title = stringResource(R.string.filter_furnished),
            checked = draft.furnishedOnly,
            onCheckedChange = { draft = draft.copy(furnishedOnly = it) },
        )
        SwitchRow(
            title = stringResource(R.string.filter_pets),
            checked = draft.petFriendlyOnly,
            onCheckedChange = { draft = draft.copy(petFriendlyOnly = it) },
        )
        SwitchRow(
            title = stringResource(R.string.filter_parking),
            checked = draft.parkingOnly,
            onCheckedChange = { draft = draft.copy(parkingOnly = it) },
        )
        SwitchRow(
            title = stringResource(R.string.filter_verified),
            checked = draft.verifiedOnly,
            onCheckedChange = { draft = draft.copy(verifiedOnly = it) },
            modifier = Modifier.testTag("verifiedSwitch"),
        )
        SheetSection(stringResource(R.string.filter_amenities))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        ) {
            Amenity.entries.forEach { amenity ->
                val selected = amenity in draft.amenities
                CategoryChip(
                    text = stringResource(amenity.labelRes()),
                    selected = selected,
                    onClick = {
                        draft =
                            draft.copy(
                                amenities =
                                    if (selected) {
                                        draft.amenities - amenity
                                    } else {
                                        draft.amenities +
                                            amenity
                                    },
                            )
                    },
                )
            }
        }
        SheetSection(stringResource(R.string.filter_sort))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
            verticalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs),
        ) {
            SortOrder.entries.filter { it != SortOrder.BestMatch || showBestMatch }.forEach { sort ->
                CategoryChip(
                    text = stringResource(sort.labelRes()),
                    selected = draft.sort == sort,
                    onClick = { draft = draft.copy(sort = sort) },
                    modifier = Modifier.testTag("sort_${sort.name}"),
                )
            }
        }
        Spacer(Modifier.height(FindoraTheme.spacing.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.sm)) {
            SecondaryPillButton(
                text = stringResource(R.string.action_reset),
                onClick = { draft = draft.withoutRefinements() },
                modifier = Modifier.weight(1f).testTag("resetFilters"),
            )
            PrimaryPillButton(
                text = stringResource(R.string.action_show_results),
                onClick = { onApply(draft) },
                modifier = Modifier.weight(1f).testTag("applyFilters"),
            )
        }
        Spacer(Modifier.height(FindoraTheme.spacing.md))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CountOptions(
    options: List<Int>,
    selected: Int?,
    tagPrefix: String,
    onSelect: (Int?) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(FindoraTheme.spacing.xs)) {
        CategoryChip(
            text = stringResource(R.string.filter_any),
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        options.forEach { count ->
            CategoryChip(
                text = stringResource(R.string.filter_at_least, count),
                selected = selected == count,
                onClick = { onSelect(count) },
                modifier = Modifier.testTag("${tagPrefix}_$count"),
            )
        }
    }
}

@Composable
private fun SheetSection(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = FindoraTheme.typography.section,
        color = FindoraTheme.colors.onBackground,
        modifier = modifier.padding(top = FindoraTheme.spacing.sm).semantics { heading() },
    )
}

@Composable
private fun sliderColors() =
    SliderDefaults.colors(
        thumbColor = FindoraTheme.colors.primaryAction,
        activeTrackColor = FindoraTheme.colors.primaryAction,
        inactiveTrackColor = FindoraTheme.colors.outline,
    )

fun SortOrder.labelRes(): Int =
    when (this) {
        SortOrder.Recommended -> R.string.sort_recommended
        SortOrder.BestMatch -> R.string.sort_best
        SortOrder.PriceLowToHigh -> R.string.sort_price_low
        SortOrder.PriceHighToLow -> R.string.sort_price_high
        SortOrder.Newest -> R.string.sort_newest
        SortOrder.Rating -> R.string.sort_rating
    }

@Suppress("CyclomaticComplexMethod") // A flat lookup table, one branch per amenity.
fun Amenity.labelRes(): Int =
    when (this) {
        Amenity.Wifi -> R.string.amenity_wifi
        Amenity.AirConditioning -> R.string.amenity_air_conditioning
        Amenity.PowerBackup -> R.string.amenity_power_backup
        Amenity.Lift -> R.string.amenity_lift
        Amenity.Security -> R.string.amenity_security
        Amenity.Cctv -> R.string.amenity_cctv
        Amenity.Gym -> R.string.amenity_gym
        Amenity.SwimmingPool -> R.string.amenity_swimming_pool
        Amenity.Garden -> R.string.amenity_garden
        Amenity.Balcony -> R.string.amenity_balcony
        Amenity.WashingMachine -> R.string.amenity_washing_machine
        Amenity.ModularKitchen -> R.string.amenity_modular_kitchen
        Amenity.WaterPurifier -> R.string.amenity_water_purifier
        Amenity.PlayArea -> R.string.amenity_play_area
        Amenity.Clubhouse -> R.string.amenity_clubhouse
        Amenity.SolarWaterHeater -> R.string.amenity_solar_water_heater
        Amenity.RainwaterHarvesting -> R.string.amenity_rainwater_harvesting
        Amenity.HomeOffice -> R.string.amenity_home_office
        Amenity.SeaView -> R.string.amenity_sea_view
        Amenity.BackwaterView -> R.string.amenity_backwater_view
    }
