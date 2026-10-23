package com.findora.app.core.domain.format

import androidx.compose.runtime.Immutable
import com.findora.app.core.domain.model.AreaUnit
import com.findora.app.core.domain.model.Currency
import com.findora.app.core.domain.model.Money

/** A rent ready to display: in the listing's currency, or [approximate]ly converted to the display currency. */
@Immutable
data class DisplayMoney(
    val money: Money,
    val approximate: Boolean,
)

/**
 * How amounts and areas are displayed. A null [currency] keeps every listing in its own currency; otherwise
 * rents are converted with [rates] and marked approximate. Without rates (or for a currency missing from the
 * table) the listing currency is shown, so a price is never wrong, only unconverted.
 */
@Immutable
data class DisplayPreferences(
    val currency: Currency? = null,
    val areaUnit: AreaUnit = AreaUnit.Metric,
    val rates: ExchangeRates? = null,
) {
    private val converter: CurrencyConverter? = rates?.let(::CurrencyConverter)

    val converts: Boolean get() = currency != null && converter != null

    fun display(money: Money): DisplayMoney {
        val target = currency?.takeIf { it != money.currency }
        val converted = target?.let { converter?.convert(money, it) }
        return if (converted != null) {
            DisplayMoney(converted, approximate = true)
        } else {
            DisplayMoney(money, approximate = false)
        }
    }
}
