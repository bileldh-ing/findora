package com.findora.app.feature.search

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.search.LifestyleHint
import com.findora.app.core.domain.search.QueryMeaning
import com.findora.app.core.ui.match.labelRes
import com.findora.app.feature.home.labelRes
import java.text.NumberFormat

/** The chip text for one understood meaning, in the UI language; amounts use the listing currency code. */
@Suppress("CyclomaticComplexMethod") // A flat lookup table, one branch per kind of meaning.
@Composable
internal fun meaningLabel(
    meaning: QueryMeaning,
    currency: Currency,
): String =
    when (meaning) {
        is QueryMeaning.Budget -> budgetLabel(meaning, currency)
        is QueryMeaning.Bedrooms -> stringResource(R.string.nl_chip_bedrooms, meaning.min)
        is QueryMeaning.Bathrooms -> stringResource(R.string.nl_chip_bathrooms, meaning.min)
        is QueryMeaning.Area -> areaLabel(meaning)
        is QueryMeaning.Type -> stringResource(meaning.type.labelRes())
        is QueryMeaning.Place -> meaning.name
        is QueryMeaning.Region -> meaning.name
        QueryMeaning.Furnished -> stringResource(R.string.filter_furnished)
        QueryMeaning.PetFriendly -> stringResource(R.string.nl_chip_pets)
        QueryMeaning.Parking -> stringResource(R.string.filter_parking)
        QueryMeaning.Verified -> stringResource(R.string.must_have_verified)
        is QueryMeaning.Feature -> stringResource(meaning.amenity.labelRes())
        is QueryMeaning.Lifestyle -> stringResource(R.string.nl_chip_hint, stringResource(meaning.hint.labelRes()))
        is QueryMeaning.Sort -> stringResource(meaning.order.labelRes())
    }

@Composable
private fun budgetLabel(
    budget: QueryMeaning.Budget,
    currency: Currency,
): String {
    val format = NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0])
    val money: (Long) -> String = { "${format.format(it)} ${currency.code}" }
    val min = budget.min
    val max = budget.max
    return when {
        min != null && max != null -> stringResource(R.string.filter_range_value, money(min), money(max))
        max != null -> stringResource(R.string.nl_chip_under, money(max))
        else -> stringResource(R.string.nl_chip_from, money(min ?: 0))
    }
}

@Composable
private fun areaLabel(area: QueryMeaning.Area): String {
    val min = area.minSqm
    val max = area.maxSqm
    return when {
        min != null && max != null -> stringResource(R.string.nl_chip_area_range, min, max)
        max != null -> stringResource(R.string.nl_chip_area_max, max)
        else -> stringResource(R.string.nl_chip_area_min, min ?: 0)
    }
}

@StringRes
private fun LifestyleHint.labelRes(): Int =
    when (this) {
        LifestyleHint.Quiet -> R.string.lifestyle_quiet
        LifestyleHint.Family -> R.string.lifestyle_family
        LifestyleHint.Transport -> R.string.nl_chip_transport
        LifestyleHint.Nightlife -> R.string.lifestyle_nightlife
        LifestyleHint.Nature -> R.string.lifestyle_nature
        LifestyleHint.Student -> R.string.lifestyle_student
        LifestyleHint.RemoteWork -> R.string.lifestyle_remote_worker
    }
