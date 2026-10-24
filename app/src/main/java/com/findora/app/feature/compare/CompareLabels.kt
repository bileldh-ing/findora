package com.findora.app.feature.compare

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.findora.app.R
import com.findora.app.core.domain.compare.CompareMetric
import com.findora.app.core.domain.format.AreaFormatter
import com.findora.app.core.domain.format.CurrencyConverter
import com.findora.app.core.domain.format.DisplayMoney
import com.findora.app.core.domain.format.PriceFormatter
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money
import com.findora.app.core.ui.LocalDisplayPreferences
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Price per area in the display unit and currency: the per-m² figure is converted with the same rates as the
 * rent (unrounded, so the "≈" figure matches the rent's conversion) and divided by about 10.76 for sq ft.
 */
@Composable
internal fun pricePerAreaText(
    pricePerSqm: Double,
    source: Currency,
    locale: Locale,
): String {
    val display = LocalDisplayPreferences.current
    val unit = display.areaUnit
    val perUnit = AreaFormatter.perArea(pricePerSqm, unit)
    val target = display.currency?.takeIf { display.converts && it != source }
    val converted =
        target?.let { currency ->
            display.rates
                ?.let(::CurrencyConverter)
                ?.convertExact(BigDecimal.valueOf(perUnit), source, currency)
                ?.let {
                    DisplayMoney(
                        Money(it.setScale(0, RoundingMode.HALF_UP).toLong(), currency),
                        approximate = true,
                    )
                }
        }
    val exact = converted ?: DisplayMoney(Money(perUnit.roundToLong().coerceAtLeast(0L), source), approximate = false)
    val format =
        when (unit) {
            AreaUnit.Metric -> R.string.compare_price_sqm_value
            AreaUnit.Imperial -> R.string.compare_price_sqft_value
        }
    return stringResource(format, PriceFormatter.formatDisplay(exact, locale))
}

/** The row label; the price-per-area row follows the unit chosen in Settings. */
@Composable
fun CompareMetric.label(): String =
    when (this) {
        CompareMetric.PricePerSqm ->
            stringResource(
                when (LocalDisplayPreferences.current.areaUnit) {
                    AreaUnit.Metric -> R.string.compare_row_price_sqm
                    AreaUnit.Imperial -> R.string.compare_row_price_sqft
                },
            )
        else -> stringResource(labelRes())
    }
